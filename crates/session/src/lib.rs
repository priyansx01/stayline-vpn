//! Glue between `stayline-core` and the apps that drive it.
//!
//! Turns a [`Profile`] into what the supervisor needs, and the supervisor's
//! [`Status`] and errors into the [`TunnelState`] clients display. Shared by
//! the Windows service and the mobile library so both report the same way.

use std::time::Duration;

use stayline_core::supervisor::Status;
use stayline_core::{Credentials, Error, Fingerprint, Gateway};
use stayline_ipc::{Attention, CertificateReport, Profile, TunnelState};
use zeroize::Zeroizing;

const INSPECT_TIMEOUT: Duration = Duration::from_secs(15);

/// A profile checked and parsed for the supervisor.
#[derive(Debug)]
pub struct SessionParams {
    pub gateway: Gateway,
    pub pin: Option<Fingerprint>,
    pub credentials: Credentials,
}

impl SessionParams {
    /// Parses `profile`. The error is a message for the user.
    pub fn from_profile(profile: Profile, password: Zeroizing<String>) -> Result<Self, String> {
        let gateway = Gateway::parse(&profile.gateway).map_err(|e| e.to_string())?;
        let pin: Option<Fingerprint> = profile
            .pin
            .as_deref()
            .filter(|p| !p.trim().is_empty())
            .map(str::parse)
            .transpose()
            .map_err(|e: Error| e.to_string())?;
        let credentials = Credentials {
            username: profile.username,
            password,
            realm: profile.realm.filter(|r| !r.is_empty()),
        };
        Ok(Self {
            gateway,
            pin,
            credentials,
        })
    }
}

/// The client-facing form of a supervisor status.
pub fn to_ipc(status: &Status) -> TunnelState {
    match status {
        Status::Connecting { attempt } => TunnelState::Connecting { attempt: *attempt },
        Status::Connected { local_ip, since } => TunnelState::Connected {
            local_ip: local_ip.to_string(),
            since_unix: since
                .duration_since(std::time::UNIX_EPOCH)
                .map_or(0, |d| d.as_secs()),
        },
        Status::Reconnecting {
            attempt,
            retry_in,
            last_error,
        } => TunnelState::Reconnecting {
            attempt: *attempt,
            retry_in_secs: retry_in.as_secs(),
            last_error: last_error.clone(),
        },
        Status::WaitingForNetwork => TunnelState::WaitingForNetwork,
        Status::NeedsUser { reason } => TunnelState::NeedsUser {
            reason: reason.clone(),
            attention: Attention::Other,
        },
        Status::Disconnected => TunnelState::Disconnected,
    }
}

/// Classifies why the session needs the user. For an untrusted certificate
/// the fingerprint is fetched so the user can decide whether to trust it.
pub async fn attention_for(error: &Error, gateway: &Gateway) -> Attention {
    match error {
        Error::BadCredentials => Attention::Credentials,
        Error::CertificateChanged { expected, actual } => Attention::CertificateChanged {
            expected: expected.clone(),
            actual: actual.clone(),
        },
        Error::CertificateUntrusted(_) => Attention::CertificateUntrusted {
            fingerprint: inspect_certificate(gateway.to_string()).await.fingerprint,
        },
        _ => Attention::Other,
    }
}

/// Fetches the gateway's certificate for the user to review.
pub async fn inspect_certificate(gateway: String) -> CertificateReport {
    let mut report = CertificateReport {
        gateway: gateway.clone(),
        fingerprint: None,
        publicly_trusted: false,
        problem: None,
    };
    let parsed = match Gateway::parse(&gateway) {
        Ok(parsed) => parsed,
        Err(e) => {
            report.problem = Some(e.to_string());
            return report;
        }
    };
    let inspect = stayline_core::tls::inspect_certificate(&parsed);
    match tokio::time::timeout(INSPECT_TIMEOUT, inspect).await {
        Ok(Ok(info)) => {
            report.fingerprint = Some(info.fingerprint.to_string());
            report.publicly_trusted = info.public_ca_error.is_none();
            report.problem = info.public_ca_error;
        }
        Ok(Err(e)) => report.problem = Some(format!("could not reach the gateway: {e}")),
        Err(_) => report.problem = Some("the gateway did not answer within 15 seconds".into()),
    }
    report
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::net::Ipv4Addr;
    use std::time::UNIX_EPOCH;

    fn profile() -> Profile {
        Profile {
            gateway: "vpn.example.com:10443".into(),
            username: "alice".into(),
            realm: Some(String::new()),
            pin: Some("  ".into()),
        }
    }

    #[test]
    fn blank_pin_and_realm_are_ignored() {
        let params = SessionParams::from_profile(profile(), Zeroizing::new("pw".into())).unwrap();
        assert!(params.pin.is_none());
        assert!(params.credentials.realm.is_none());
        assert_eq!(params.credentials.username, "alice");
    }

    #[test]
    fn bad_pin_is_reported() {
        let mut p = profile();
        p.pin = Some("not-hex".into());
        assert!(SessionParams::from_profile(p, Zeroizing::new("pw".into())).is_err());
    }

    #[test]
    fn connected_status_carries_unix_time() {
        let since = UNIX_EPOCH + Duration::from_secs(1_790_000_000);
        let state = to_ipc(&Status::Connected {
            local_ip: Ipv4Addr::new(10, 0, 0, 5),
            since,
        });
        assert_eq!(
            state,
            TunnelState::Connected {
                local_ip: "10.0.0.5".into(),
                since_unix: 1_790_000_000
            }
        );
    }

    #[tokio::test]
    async fn bad_credentials_ask_for_a_password() {
        let gateway = Gateway::parse("vpn.example.com").unwrap();
        assert_eq!(
            attention_for(&Error::BadCredentials, &gateway).await,
            Attention::Credentials
        );
    }
}
