//! Sends `tracing` output to logcat under the tag `stayline`.

use std::ffi::{CString, c_char, c_int};
use std::io::Write;

use tracing_subscriber::fmt::MakeWriter;

const TAG: &core::ffi::CStr = c"stayline";
const ANDROID_LOG_INFO: c_int = 4;

#[link(name = "log")]
unsafe extern "C" {
    fn __android_log_write(prio: c_int, tag: *const c_char, text: *const c_char) -> c_int;
}

pub fn init() {
    let _ = tracing_subscriber::fmt()
        .with_max_level(tracing::Level::INFO)
        .with_ansi(false)
        .without_time()
        .with_writer(Logcat)
        .try_init();
}

#[derive(Clone, Copy)]
struct Logcat;

/// Buffers one formatted event and writes it as one logcat line.
struct Line(Vec<u8>);

impl Write for Line {
    fn write(&mut self, buf: &[u8]) -> std::io::Result<usize> {
        self.0.extend_from_slice(buf);
        Ok(buf.len())
    }

    fn flush(&mut self) -> std::io::Result<()> {
        Ok(())
    }
}

impl Drop for Line {
    fn drop(&mut self) {
        while self.0.last() == Some(&b'\n') {
            self.0.pop();
        }
        self.0.retain(|&b| b != 0);
        if let Ok(text) = CString::new(std::mem::take(&mut self.0)) {
            // SAFETY: both strings are NUL-terminated and outlive the call.
            unsafe { __android_log_write(ANDROID_LOG_INFO, TAG.as_ptr(), text.as_ptr()) };
        }
    }
}

impl<'a> MakeWriter<'a> for Logcat {
    type Writer = Line;

    fn make_writer(&'a self) -> Self::Writer {
        Line(Vec::with_capacity(256))
    }
}
