//! The tunnel engine of the stayline Android app.
//!
//! The app's `VpnService` drives it through a handful of JNI calls
//! ([`jni`]); everything else is shared with Windows. Status reaches the app
//! as the same JSON [`stayline_ipc::Event`]s the Windows service sends over
//! its pipe, and the app opens the VPN interface when asked with a
//! [`LinkSpec`].

mod device;
mod engine;
mod hooks;
mod jni;
mod link;
#[cfg(target_os = "android")]
mod log;

pub use engine::{Engine, Platform};
pub use link::{LinkSpec, RouteSpec};
