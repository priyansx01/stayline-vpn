//! Windows networking for stayline: the Wintun adapter, its address, routes
//! and DNS. Everything here needs administrator rights.

#[cfg(windows)]
mod device;
#[cfg(windows)]
mod error;
#[cfg(windows)]
mod iphlp;
#[cfg(windows)]
mod setup;

#[cfg(windows)]
pub use device::{DeviceChannels, TunDevice};
#[cfg(windows)]
pub use error::{NetError, Result};
#[cfg(windows)]
pub use iphlp::NetworkWatcher;
#[cfg(windows)]
pub use setup::{GatewayRoute, TunnelNetwork, WindowsHooks};

pub use stayline_core::tunnel::TUNNEL_MTU;
