//! Owns the tunnel session and the state every client sees.

use std::sync::{Arc, Mutex};

use stayline_core::supervisor::{
    self, NetEvent, Status, SupervisorConfig, SupervisorEnd, SupervisorIo,
};
use stayline_core::tunnel::TunnelStats;
use stayline_core::{Credentials, Fingerprint, Gateway};
use stayline_ipc::{Attention, Profile, TunnelState};
use stayline_net::{DeviceChannels, NetworkWatcher, TunDevice, WindowsHooks};
use tokio::sync::{mpsc, watch};
use tokio::task::JoinHandle;
use zeroize::Zeroizing;

pub use stayline_session::inspect_certificate;
use stayline_session::{SessionParams, attention_for, to_ipc};

const ADAPTER_NAME: &str = "stayline";

struct Session {
    stop: watch::Sender<bool>,
    task: JoinHandle<()>,
}

pub struct Controller {
    state: watch::Sender<TunnelState>,
    session: tokio::sync::Mutex<Option<Session>>,
    /// Event sender of the running session, for power events.
    events: Mutex<Option<mpsc::UnboundedSender<NetEvent>>>,
    /// Traffic counters of the current session.
    stats: Mutex<Arc<TunnelStats>>,
}

impl Controller {
    pub fn new() -> Arc<Self> {
        Arc::new(Self {
            state: watch::Sender::new(TunnelState::Disconnected),
            session: tokio::sync::Mutex::new(None),
            events: Mutex::new(None),
            stats: Mutex::new(Arc::default()),
        })
    }

    pub fn subscribe(&self) -> watch::Receiver<TunnelState> {
        self.state.subscribe()
    }

    /// Bytes `(sent, received)` in the current session.
    pub fn stats(&self) -> (u64, u64) {
        self.stats.lock().expect("stats lock").snapshot()
    }

    /// Starts (or restarts with new settings) a session that keeps the
    /// tunnel up. Returns an error only for invalid settings.
    pub async fn connect(
        self: &Arc<Self>,
        profile: Profile,
        password: Zeroizing<String>,
    ) -> Result<(), String> {
        let SessionParams {
            gateway,
            pin,
            credentials,
        } = SessionParams::from_profile(profile, password)?;

        let mut session = self.session.lock().await;
        if let Some(old) = session.take() {
            Self::stop_session(old).await;
        }

        let (stop, stop_rx) = watch::channel(false);
        let (events_tx, events_rx) = mpsc::unbounded_channel();
        *self.events.lock().expect("events lock") = Some(events_tx.clone());
        let this = self.clone();
        let task = tokio::spawn(async move {
            this.run_session(gateway, pin, credentials, stop_rx, events_tx, events_rx)
                .await;
        });
        *session = Some(Session { stop, task });
        tracing::info!("connect requested");
        Ok(())
    }

    pub async fn disconnect(&self) {
        let old = self.session.lock().await.take();
        if let Some(old) = old {
            tracing::info!("disconnect requested");
            Self::stop_session(old).await;
        }
        *self.events.lock().expect("events lock") = None;
        self.state.send_replace(TunnelState::Disconnected);
    }

    /// Forwards a machine event (resume from sleep) to the running session.
    pub fn notify(&self, event: NetEvent) {
        if let Some(tx) = self.events.lock().expect("events lock").as_ref() {
            let _ = tx.send(event);
        }
    }

    async fn stop_session(session: Session) {
        let _ = session.stop.send(true);
        if let Err(e) = session.task.await {
            tracing::error!(error = %e, "tunnel session task failed");
        }
    }

    async fn run_session(
        &self,
        gateway: Gateway,
        pin: Option<Fingerprint>,
        credentials: Credentials,
        stop: watch::Receiver<bool>,
        events_tx: mpsc::UnboundedSender<NetEvent>,
        mut events_rx: mpsc::UnboundedReceiver<NetEvent>,
    ) {
        self.state
            .send_replace(TunnelState::Connecting { attempt: 1 });

        let (device, channels) = match TunDevice::create(ADAPTER_NAME) {
            Ok(created) => created,
            Err(e) => return self.fail(format!("could not create the network adapter: {e}")),
        };
        let DeviceChannels {
            mut from_device,
            to_device,
        } = channels;
        let watcher = match NetworkWatcher::start(device.luid(), events_tx) {
            Ok(watcher) => watcher,
            Err(e) => return self.fail(format!("could not watch for network changes: {e}")),
        };
        let hooks = WindowsHooks::new(device.luid());
        let stats = Arc::new(TunnelStats::default());
        *self.stats.lock().expect("stats lock") = stats.clone();

        let (status_tx, mut status_rx) = watch::channel(Status::Disconnected);
        let state = self.state.clone();
        let forward = tokio::spawn(async move {
            while status_rx.changed().await.is_ok() {
                let status = status_rx.borrow_and_update().clone();
                // The session reports needing the user itself, with details.
                if !matches!(status, Status::NeedsUser { .. }) {
                    state.send_replace(to_ipc(&status));
                }
            }
        });

        let end = supervisor::supervise(
            SupervisorConfig {
                gateway: &gateway,
                pin,
                credentials: &credentials,
            },
            &hooks,
            SupervisorIo {
                from_device: &mut from_device,
                to_device: &to_device,
                network_changed: &mut events_rx,
                status: &status_tx,
                stats: &stats,
                shutdown: stop,
            },
        )
        .await;

        forward.abort();
        drop(hooks);
        drop(watcher);
        drop(device);

        match end {
            SupervisorEnd::Stopped => {
                self.state.send_replace(TunnelState::Disconnected);
            }
            SupervisorEnd::NeedsUser(e) => {
                let attention = attention_for(&e, &gateway).await;
                self.fail_with(e.to_string(), attention);
            }
        }
    }

    fn fail(&self, reason: String) {
        self.fail_with(reason, Attention::Other);
    }

    fn fail_with(&self, reason: String, attention: Attention) {
        tracing::error!(%reason, ?attention, "tunnel stopped");
        self.state
            .send_replace(TunnelState::NeedsUser { reason, attention });
    }
}
