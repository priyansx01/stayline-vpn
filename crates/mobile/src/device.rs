//! Moves packets between the VPN interface and the tunnel's channels.
//!
//! The channels live as long as the session; the interface may be replaced
//! when a reconnect brings different settings, so the pump can be pointed at
//! a new file descriptor without the supervisor noticing.

use bytes::Bytes;
use tokio::sync::{mpsc, watch};
use tokio::task::JoinHandle;

pub const QUEUE_LEN: usize = 1024;

pub struct Pump {
    in_tx: mpsc::Sender<Bytes>,
    current: watch::Sender<Option<imp::Tun>>,
    reader: Option<JoinHandle<()>>,
    writer: JoinHandle<()>,
}

impl Pump {
    /// Starts the writer. `in_tx` receives packets read from the interface;
    /// packets from `out_rx` are written to it. Needs a Tokio runtime.
    pub fn new(in_tx: mpsc::Sender<Bytes>, mut out_rx: mpsc::Receiver<Bytes>) -> Self {
        let (current, current_rx) = watch::channel::<Option<imp::Tun>>(None);
        let writer = tokio::spawn(async move {
            while let Some(packet) = out_rx.recv().await {
                let tun = current_rx.borrow().clone();
                // Without an interface yet there is nowhere to deliver it.
                if let Some(tun) = tun
                    && let Err(e) = tun.write(&packet).await
                {
                    tracing::trace!(error = %e, "could not write to the interface");
                }
            }
        });
        Self {
            in_tx,
            current,
            reader: None,
            writer,
        }
    }

    pub fn is_attached(&self) -> bool {
        self.current.borrow().is_some()
    }

    /// Takes ownership of `fd` and pumps packets through it, closing the
    /// previous interface.
    pub fn attach(&mut self, fd: i32) -> std::io::Result<()> {
        let tun = imp::Tun::new(fd)?;
        if let Some(old) = self.reader.take() {
            old.abort();
        }
        self.reader = Some(tokio::spawn(read_loop(tun.clone(), self.in_tx.clone())));
        self.current.send_replace(Some(tun));
        Ok(())
    }
}

impl Drop for Pump {
    fn drop(&mut self) {
        if let Some(reader) = self.reader.take() {
            reader.abort();
        }
        self.writer.abort();
        self.current.send_replace(None);
    }
}

async fn read_loop(tun: imp::Tun, in_tx: mpsc::Sender<Bytes>) {
    let mut buf = vec![0u8; 64 * 1024];
    loop {
        match tun.read(&mut buf).await {
            // Only IPv4 goes through the tunnel; IPv6 is captured only so it
            // does not leak, and is dropped here.
            Ok(n) if n > 0 && buf[0] >> 4 == 4 => {
                if in_tx.try_send(Bytes::copy_from_slice(&buf[..n])).is_err() {
                    tracing::trace!("tunnel queue full, dropping packet");
                }
            }
            Ok(_) => {}
            Err(e) => {
                tracing::warn!(error = %e, "VPN interface closed");
                return;
            }
        }
    }
}

#[cfg(unix)]
mod imp {
    use std::io;
    use std::os::fd::{AsRawFd, FromRawFd, OwnedFd};
    use std::sync::Arc;

    use tokio::io::unix::AsyncFd;

    /// The VPN interface's file descriptor, closed when the last clone drops.
    #[derive(Clone)]
    pub struct Tun(Arc<AsyncFd<OwnedFd>>);

    impl Tun {
        pub fn new(fd: i32) -> io::Result<Self> {
            if fd < 0 {
                return Err(io::Error::from_raw_os_error(libc::EBADF));
            }
            // SAFETY: the app detached `fd` from its ParcelFileDescriptor and
            // handed it over; nothing else closes it.
            let owned = unsafe { OwnedFd::from_raw_fd(fd) };
            // SAFETY: plain fcntl calls on a descriptor we own.
            unsafe {
                let flags = libc::fcntl(fd, libc::F_GETFL);
                if flags < 0 || libc::fcntl(fd, libc::F_SETFL, flags | libc::O_NONBLOCK) < 0 {
                    return Err(io::Error::last_os_error());
                }
            }
            Ok(Self(Arc::new(AsyncFd::new(owned)?)))
        }

        pub async fn read(&self, buf: &mut [u8]) -> io::Result<usize> {
            loop {
                let mut guard = self.0.readable().await?;
                // SAFETY: `buf` is valid for `buf.len()` bytes.
                match guard.try_io(|fd| {
                    cvt(unsafe { libc::read(fd.as_raw_fd(), buf.as_mut_ptr().cast(), buf.len()) })
                }) {
                    Ok(result) => return result,
                    Err(_would_block) => continue,
                }
            }
        }

        pub async fn write(&self, packet: &[u8]) -> io::Result<usize> {
            loop {
                let mut guard = self.0.writable().await?;
                // SAFETY: `packet` is valid for `packet.len()` bytes.
                match guard.try_io(|fd| {
                    cvt(unsafe {
                        libc::write(fd.as_raw_fd(), packet.as_ptr().cast(), packet.len())
                    })
                }) {
                    Ok(result) => return result,
                    Err(_would_block) => continue,
                }
            }
        }
    }

    fn cvt(n: isize) -> io::Result<usize> {
        usize::try_from(n).map_err(|_| io::Error::last_os_error())
    }
}

/// Lets the crate build and its logic be tested on Windows; there is no
/// VPN interface to open there.
#[cfg(not(unix))]
mod imp {
    use std::io;

    #[derive(Clone)]
    pub struct Tun;

    impl Tun {
        pub fn new(_fd: i32) -> io::Result<Self> {
            Err(io::ErrorKind::Unsupported.into())
        }

        pub async fn read(&self, _buf: &mut [u8]) -> io::Result<usize> {
            Err(io::ErrorKind::Unsupported.into())
        }

        pub async fn write(&self, _packet: &[u8]) -> io::Result<usize> {
            Err(io::ErrorKind::Unsupported.into())
        }
    }
}
