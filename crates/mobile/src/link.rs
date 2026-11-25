//! What the app needs to open the VPN interface.

use std::collections::BTreeSet;
use std::net::Ipv4Addr;

use serde::Serialize;
use stayline_core::TunnelConfig;
use stayline_core::tunnel::TUNNEL_MTU;

/// Settings for `VpnService.Builder`, sent to the app as JSON.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct LinkSpec {
    pub address: String,
    pub mtu: u32,
    /// Send all traffic through the tunnel. The app also captures IPv6 so
    /// it cannot leak around the IPv4-only tunnel.
    pub full_tunnel: bool,
    /// Split-tunnel routes, with host bits cleared as Android requires.
    pub routes: Vec<RouteSpec>,
    pub dns: Vec<String>,
    pub search_domains: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, PartialOrd, Ord, Serialize)]
pub struct RouteSpec {
    pub address: String,
    pub prefix: u8,
}

impl LinkSpec {
    pub fn new(local_ip: Ipv4Addr, cfg: &TunnelConfig) -> Self {
        // Android has no per-domain DNS: split-DNS servers answer everything.
        let dns: Vec<Ipv4Addr> = unique(
            cfg.dns
                .iter()
                .chain(cfg.split_dns.iter().flat_map(|s| &s.servers))
                .copied(),
        );

        let full_tunnel = cfg.is_full_tunnel();
        let routes = if full_tunnel {
            Vec::new()
        } else {
            // In split mode the DNS servers must be reachable through the tunnel.
            let mut routes: BTreeSet<RouteSpec> = cfg
                .routes
                .iter()
                .map(|r| route(r.network, r.prefix_len()))
                .collect();
            routes.extend(dns.iter().map(|ip| route(*ip, 32)));
            routes.into_iter().collect()
        };

        Self {
            address: local_ip.to_string(),
            mtu: TUNNEL_MTU,
            full_tunnel,
            routes,
            dns: dns.iter().map(Ipv4Addr::to_string).collect(),
            search_domains: cfg
                .dns_suffix
                .as_deref()
                .map(search_domains)
                .unwrap_or_default(),
        }
    }
}

fn route(network: Ipv4Addr, prefix: u32) -> RouteSpec {
    let prefix = prefix.min(32);
    let mask = u32::MAX.checked_shl(32 - prefix).unwrap_or(0);
    RouteSpec {
        address: Ipv4Addr::from(u32::from(network) & mask).to_string(),
        prefix: prefix as u8,
    }
}

/// Order kept, duplicates dropped.
fn unique(items: impl Iterator<Item = Ipv4Addr>) -> Vec<Ipv4Addr> {
    let mut seen = BTreeSet::new();
    items.filter(|ip| seen.insert(*ip)).collect()
}

/// FortiGate sends several suffixes separated by `;`, `,` or spaces.
fn search_domains(suffix: &str) -> Vec<String> {
    suffix
        .split([';', ',', ' '])
        .map(str::trim)
        .filter(|d| !d.is_empty())
        .map(str::to_owned)
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    use stayline_core::{Route, SplitDns};

    fn ip(s: &str) -> Ipv4Addr {
        s.parse().unwrap()
    }

    #[test]
    fn full_tunnel_has_no_routes() {
        let cfg = TunnelConfig {
            dns: vec![ip("10.1.0.53")],
            dns_suffix: Some("corp.example.com; example.com".into()),
            ..Default::default()
        };
        let spec = LinkSpec::new(ip("10.212.134.5"), &cfg);
        assert!(spec.full_tunnel);
        assert!(spec.routes.is_empty());
        assert_eq!(spec.address, "10.212.134.5");
        assert_eq!(spec.mtu, TUNNEL_MTU);
        assert_eq!(spec.dns, ["10.1.0.53"]);
        assert_eq!(spec.search_domains, ["corp.example.com", "example.com"]);
    }

    #[test]
    fn split_tunnel_clears_host_bits_and_routes_dns() {
        let cfg = TunnelConfig {
            routes: vec![Route {
                network: ip("10.20.30.40"),
                mask: ip("255.255.0.0"),
            }],
            split_dns: vec![SplitDns {
                domains: vec!["corp.example.com".into()],
                servers: vec![ip("192.168.7.53"), ip("192.168.7.53")],
            }],
            ..Default::default()
        };
        let spec = LinkSpec::new(ip("10.212.134.5"), &cfg);
        assert!(!spec.full_tunnel);
        assert_eq!(spec.dns, ["192.168.7.53"]);
        assert_eq!(
            spec.routes,
            [
                RouteSpec {
                    address: "10.20.0.0".into(),
                    prefix: 16
                },
                RouteSpec {
                    address: "192.168.7.53".into(),
                    prefix: 32
                },
            ]
        );
    }

    #[test]
    fn same_config_gives_an_equal_spec() {
        let cfg = TunnelConfig {
            dns: vec![ip("10.1.0.53")],
            ..Default::default()
        };
        assert_eq!(
            LinkSpec::new(ip("10.0.0.5"), &cfg),
            LinkSpec::new(ip("10.0.0.5"), &cfg)
        );
        assert_ne!(
            LinkSpec::new(ip("10.0.0.5"), &cfg),
            LinkSpec::new(ip("10.0.0.6"), &cfg)
        );
    }

    #[test]
    fn default_route_prefix_is_handled() {
        assert_eq!(
            route(ip("1.2.3.4"), 0),
            RouteSpec {
                address: "0.0.0.0".into(),
                prefix: 0
            }
        );
    }
}
