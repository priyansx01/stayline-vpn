//! The Android side of keeping a tunnel up.

use std::net::Ipv4Addr;
use std::sync::atomic::{AtomicBool, AtomicI64, Ordering};
use std::sync::{Arc, Mutex};

use stayline_core::TunnelConfig;
use stayline_core::supervisor::TunnelHooks;

use crate::device::Pump;
use crate::engine::Platform;
use crate::link::LinkSpec;

/// The phone's network as last reported by the app.
#[derive(Debug, Default)]
pub struct NetState {
    offline: AtomicBool,
    /// `Network.getNetworkHandle()` of the default network; 0 if unknown.
    handle: AtomicI64,
}

impl NetState {
    pub fn update(&self, handle: i64, available: bool) {
        self.handle.store(handle, Ordering::Relaxed);
        self.offline.store(!available, Ordering::Relaxed);
    }

    fn handle(&self) -> i64 {
        self.handle.load(Ordering::Relaxed)
    }
}

struct Link {
    spec: Option<LinkSpec>,
    pump: Pump,
}

pub struct AndroidHooks {
    platform: Arc<dyn Platform>,
    net: Arc<NetState>,
    /// Network the current connection attempt started on.
    connected_on: AtomicI64,
    link: Mutex<Link>,
}

impl AndroidHooks {
    pub fn new(platform: Arc<dyn Platform>, net: Arc<NetState>, pump: Pump) -> Self {
        Self {
            platform,
            net,
            connected_on: AtomicI64::new(0),
            link: Mutex::new(Link { spec: None, pump }),
        }
    }
}

impl TunnelHooks for AndroidHooks {
    /// Nothing to route: the app excludes its own traffic from the VPN, so
    /// login and the tunnel connection always use the phone's network.
    fn route_to_gateway(&self, _gateway_ip: Ipv4Addr) -> Result<(), String> {
        self.connected_on
            .store(self.net.handle(), Ordering::Relaxed);
        Ok(())
    }

    fn link_up(&self, local_ip: Ipv4Addr, cfg: &TunnelConfig) -> Result<(), String> {
        let spec = LinkSpec::new(local_ip, cfg);
        let mut link = self.link.lock().expect("link lock");
        // Keeping the interface across a reconnect means apps never see the
        // VPN go away, so nothing leaks while the tunnel is rebuilt.
        if link.spec.as_ref() == Some(&spec) && link.pump.is_attached() {
            return Ok(());
        }
        let fd = self.platform.establish(&spec)?;
        link.pump
            .attach(fd)
            .map_err(|e| format!("could not use the VPN interface: {e}"))?;
        link.spec = Some(spec);
        Ok(())
    }

    /// The default network changed, e.g. Wi-Fi to mobile data: the old
    /// connection is bound to a network that is gone.
    fn gateway_path_changed(&self) -> bool {
        let now = self.net.handle();
        let was = self.connected_on.load(Ordering::Relaxed);
        if was == 0 {
            self.connected_on.store(now, Ordering::Relaxed);
            return false;
        }
        now != 0 && now != was
    }

    fn network_available(&self) -> bool {
        !self.net.offline.load(Ordering::Relaxed)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use stayline_ipc::Event;
    use std::sync::atomic::AtomicUsize;
    use tokio::sync::mpsc;

    /// Hands out invalid descriptors, so attaching fails after `establish`.
    #[derive(Default)]
    struct FakePlatform {
        establishes: AtomicUsize,
    }

    impl Platform for FakePlatform {
        fn emit(&self, _event: &Event) {}
        fn establish(&self, _link: &LinkSpec) -> Result<i32, String> {
            self.establishes.fetch_add(1, Ordering::Relaxed);
            Ok(-1)
        }
    }

    fn hooks(platform: Arc<FakePlatform>, net: Arc<NetState>) -> AndroidHooks {
        let (in_tx, _) = mpsc::channel(1);
        let (_, out_rx) = mpsc::channel(1);
        AndroidHooks::new(platform, net, Pump::new(in_tx, out_rx))
    }

    #[tokio::test]
    async fn switching_networks_changes_the_path() {
        let net = Arc::new(NetState::default());
        net.update(100, true);
        let hooks = hooks(Arc::default(), net.clone());
        hooks.route_to_gateway(Ipv4Addr::LOCALHOST).unwrap();

        assert!(!hooks.gateway_path_changed());
        net.update(200, true);
        assert!(hooks.gateway_path_changed());
        // Losing the network says nothing about the path; availability does.
        net.update(0, false);
        assert!(!hooks.gateway_path_changed());
        assert!(!hooks.network_available());
    }

    #[tokio::test]
    async fn unknown_start_network_is_learned() {
        let net = Arc::new(NetState::default());
        let hooks = hooks(Arc::default(), net.clone());
        hooks.route_to_gateway(Ipv4Addr::LOCALHOST).unwrap();
        net.update(100, true);
        assert!(!hooks.gateway_path_changed());
        net.update(200, true);
        assert!(hooks.gateway_path_changed());
    }

    #[tokio::test]
    async fn a_bad_descriptor_fails_link_up() {
        let platform = Arc::new(FakePlatform::default());
        let hooks = hooks(platform.clone(), Arc::default());
        let cfg = TunnelConfig::default();
        assert!(hooks.link_up(Ipv4Addr::new(10, 0, 0, 5), &cfg).is_err());
        // Not remembered as attached, so the next attempt asks again.
        assert!(hooks.link_up(Ipv4Addr::new(10, 0, 0, 5), &cfg).is_err());
        assert_eq!(platform.establishes.load(Ordering::Relaxed), 2);
    }
}
