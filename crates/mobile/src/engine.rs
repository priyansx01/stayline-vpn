//! Owns the tunnel session, like the Windows service's controller, and
//! reports its state to the app.

use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::time::Duration;

use stayline_core::supervisor::{
    self, NetEvent, Status, SupervisorConfig, SupervisorEnd, SupervisorIo,
};
use stayline_core::tunnel::TunnelStats;
use stayline_ipc::{Event, Profile, TunnelState};
use stayline_session::{SessionParams, attention_for, inspect_certificate, to_ipc};
use tokio::runtime::Runtime;
use tokio::sync::{mpsc, watch};
use tokio::task::JoinHandle;
use zeroize::Zeroizing;

use crate::device::{Pump, QUEUE_LEN};
use crate::hooks::{AndroidHooks, NetState};
use crate::link::LinkSpec;

const STATS_INTERVAL: Duration = Duration::from_secs(2);

/// What the engine needs from the app.
pub trait Platform: Send + Sync + 'static {
    /// Delivers an event to the app.
    fn emit(&self, event: &Event);

    /// Opens the VPN interface and returns its file descriptor, which the
    /// engine then owns.
    fn establish(&self, link: &LinkSpec) -> Result<i32, String>;

    /// Called on each engine thread just before it exits.
    fn thread_stopping(&self) {}
}

struct Session {
    stop: watch::Sender<bool>,
    task: JoinHandle<()>,
}

struct Inner {
    platform: Arc<dyn Platform>,
    net: Arc<NetState>,
    state: watch::Sender<TunnelState>,
    session: Mutex<Option<Session>>,
    /// Event sender of the running session.
    events: Mutex<Option<mpsc::UnboundedSender<NetEvent>>>,
    /// Traffic counters of the current session.
    stats: Mutex<Arc<TunnelStats>>,
    /// Bumped by every connect and disconnect. A session only reports while
    /// it is the latest, so one being replaced cannot say "disconnected" and
    /// make the app tear down the VPN the new session is bringing up.
    generation: AtomicU64,
}

/// Every call returns at once; results arrive as events.
pub struct Engine {
    runtime: Runtime,
    inner: Arc<Inner>,
}

impl Engine {
    pub fn new(platform: Arc<dyn Platform>) -> std::io::Result<Self> {
        let on_stop = platform.clone();
        // Two workers are plenty for one tunnel and keep memory low.
        let runtime = tokio::runtime::Builder::new_multi_thread()
            .worker_threads(2)
            .thread_name("stayline")
            .enable_all()
            .on_thread_stop(move || on_stop.thread_stopping())
            .build()?;
        let inner = Arc::new(Inner {
            platform,
            net: Arc::default(),
            state: watch::Sender::new(TunnelState::Disconnected),
            session: Mutex::new(None),
            events: Mutex::new(None),
            stats: Mutex::new(Arc::default()),
            generation: AtomicU64::new(0),
        });
        runtime.spawn(report(inner.clone()));
        Ok(Self { runtime, inner })
    }

    /// Starts (or restarts with new settings) a session that keeps the
    /// tunnel up. Invalid settings are answered with [`Event::Rejected`].
    pub fn connect(&self, profile: Profile, password: Zeroizing<String>) {
        let params = match SessionParams::from_profile(profile, password) {
            Ok(params) => params,
            Err(reason) => return self.inner.platform.emit(&Event::Rejected { reason }),
        };
        let (stop, stop_rx) = watch::channel(false);
        let (events_tx, events_rx) = mpsc::unbounded_channel();
        *self.inner.events.lock().expect("events lock") = Some(events_tx);

        let mut session = self.inner.session.lock().expect("session lock");
        let previous = session.take();
        let id = self.inner.next_generation();
        let inner = self.inner.clone();
        let task = self.runtime.spawn(async move {
            // The old session must let go of the VPN interface first.
            if let Some(previous) = previous {
                stop_session(previous).await;
            }
            if !*stop_rx.borrow() {
                inner.run_session(id, params, stop_rx, events_rx).await;
            }
        });
        *session = Some(Session { stop, task });
        tracing::info!("connect requested");
    }

    pub fn disconnect(&self) {
        let previous = self.inner.session.lock().expect("session lock").take();
        *self.inner.events.lock().expect("events lock") = None;
        let id = self.inner.next_generation();
        let inner = self.inner.clone();
        self.runtime.spawn(async move {
            if let Some(previous) = previous {
                tracing::info!("disconnect requested");
                stop_session(previous).await;
            }
            inner.publish(id, TunnelState::Disconnected);
        });
    }

    /// Fetches the gateway's certificate; answered with [`Event::Certificate`].
    pub fn inspect_certificate(&self, gateway: String) {
        let platform = self.inner.platform.clone();
        self.runtime.spawn(async move {
            platform.emit(&Event::Certificate(inspect_certificate(gateway).await));
        });
    }

    /// The default network changed. `handle` is 0 when there is none.
    pub fn network_changed(&self, handle: i64, available: bool) {
        self.inner.net.update(handle, available);
        self.inner.notify(NetEvent::Changed);
    }

    /// The phone woke up; the connection may have died while it slept.
    pub fn resumed(&self) {
        self.inner.notify(NetEvent::Resumed);
    }
}

async fn stop_session(session: Session) {
    let _ = session.stop.send(true);
    if let Err(e) = session.task.await {
        tracing::error!(error = %e, "tunnel session task failed");
    }
}

impl Inner {
    fn next_generation(&self) -> u64 {
        self.generation.fetch_add(1, Ordering::SeqCst) + 1
    }

    /// Reports `state` if session `id` is still the latest.
    fn publish(&self, id: u64, state: TunnelState) {
        if self.generation.load(Ordering::SeqCst) == id {
            self.state.send_replace(state);
        }
    }

    fn notify(&self, event: NetEvent) {
        if let Some(tx) = self.events.lock().expect("events lock").as_ref() {
            let _ = tx.send(event);
        }
    }

    async fn run_session(
        self: Arc<Self>,
        id: u64,
        params: SessionParams,
        stop: watch::Receiver<bool>,
        mut events_rx: mpsc::UnboundedReceiver<NetEvent>,
    ) {
        self.publish(id, TunnelState::Connecting { attempt: 1 });

        let (in_tx, mut from_device) = mpsc::channel(QUEUE_LEN);
        let (to_device, out_rx) = mpsc::channel(QUEUE_LEN);
        let hooks = AndroidHooks::new(
            self.platform.clone(),
            self.net.clone(),
            Pump::new(in_tx, out_rx),
        );
        let stats = Arc::new(TunnelStats::default());
        *self.stats.lock().expect("stats lock") = stats.clone();

        let (status_tx, mut status_rx) = watch::channel(Status::Disconnected);
        let this = self.clone();
        let forward = tokio::spawn(async move {
            while status_rx.changed().await.is_ok() {
                let status = status_rx.borrow_and_update().clone();
                // The session reports needing the user itself, with details.
                if !matches!(status, Status::NeedsUser { .. }) {
                    this.publish(id, to_ipc(&status));
                }
            }
        });

        let end = supervisor::supervise(
            SupervisorConfig {
                gateway: &params.gateway,
                pin: params.pin,
                credentials: &params.credentials,
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
        // Closes the VPN interface.
        drop(hooks);

        match end {
            SupervisorEnd::Stopped => {
                self.publish(id, TunnelState::Disconnected);
            }
            SupervisorEnd::NeedsUser(e) => {
                let attention = attention_for(&e, &params.gateway).await;
                let reason = e.to_string();
                tracing::error!(%reason, ?attention, "tunnel stopped");
                self.publish(id, TunnelState::NeedsUser { reason, attention });
            }
        }
    }
}

/// Sends every state change to the app, and traffic counters while
/// connected.
async fn report(inner: Arc<Inner>) {
    let mut state_rx = inner.state.subscribe();
    let initial = state_rx.borrow_and_update().clone();
    inner.platform.emit(&Event::Status(initial));

    let mut tick = tokio::time::interval(STATS_INTERVAL);
    tick.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Skip);
    loop {
        tokio::select! {
            changed = state_rx.changed() => {
                if changed.is_err() {
                    return;
                }
                let state = state_rx.borrow_and_update().clone();
                inner.platform.emit(&Event::Status(state));
            }
            _ = tick.tick() => {
                if matches!(*state_rx.borrow(), TunnelState::Connected { .. }) {
                    let (sent, received) = inner.stats.lock().expect("stats lock").snapshot();
                    inner.platform.emit(&Event::Stats { sent, received });
                }
            }
        }
    }
}

impl Drop for Engine {
    fn drop(&mut self) {
        if let Some(session) = self.inner.session.lock().expect("session lock").take() {
            let _ = session.stop.send(true);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[derive(Default)]
    struct Recorder {
        events: Mutex<Vec<Event>>,
    }

    impl Platform for Recorder {
        fn emit(&self, event: &Event) {
            self.events.lock().unwrap().push(event.clone());
        }
        fn establish(&self, _link: &LinkSpec) -> Result<i32, String> {
            Err("no interface in tests".into())
        }
    }

    fn wait_for(recorder: &Recorder, pred: impl Fn(&[Event]) -> bool) {
        for _ in 0..200 {
            if pred(&recorder.events.lock().unwrap()) {
                return;
            }
            std::thread::sleep(Duration::from_millis(10));
        }
        panic!("timed out; got {:?}", recorder.events.lock().unwrap());
    }

    fn profile(pin: &str) -> Profile {
        Profile {
            gateway: "vpn.example.com".into(),
            username: "alice".into(),
            realm: None,
            pin: Some(pin.into()),
        }
    }

    #[test]
    fn reports_the_initial_state() {
        let recorder = Arc::new(Recorder::default());
        let _engine = Engine::new(recorder.clone()).unwrap();
        wait_for(&recorder, |e| {
            e.first() == Some(&Event::Status(TunnelState::Disconnected))
        });
    }

    #[test]
    fn invalid_profile_is_rejected() {
        let recorder = Arc::new(Recorder::default());
        let engine = Engine::new(recorder.clone()).unwrap();
        engine.connect(profile("zz"), Zeroizing::new("pw".into()));
        wait_for(&recorder, |e| {
            e.iter().any(|e| matches!(e, Event::Rejected { .. }))
        });
    }

    #[test]
    fn disconnect_without_a_session_reports_disconnected() {
        let recorder = Arc::new(Recorder::default());
        let engine = Engine::new(recorder.clone()).unwrap();
        engine.disconnect();
        engine.resumed();
        engine.network_changed(0, false);
        wait_for(&recorder, |e| {
            e.iter()
                .all(|e| *e == Event::Status(TunnelState::Disconnected))
                && !e.is_empty()
        });
    }
}
