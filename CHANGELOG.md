# Changelog

All notable changes to Stayline VPN are documented here. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.2.1] - 2026-11-26

### Fixed
- Android builds start the Rust toolchain by its full path, so they work where Rust is installed without being on the build's PATH, as on F-Droid's build servers. The app itself is unchanged.

## [0.2.0] - 2026-11-26

### Added
- Android releases: separate APKs for armeabi-v7a, arm64-v8a and x86_64, signed with the project's own key and reproducible from source, ready for F-Droid.
- Android app: a native Jetpack Compose interface in ruby and emerald with micro-animations, on the same Rust tunnel as Windows (`crates/mobile`). Supports Always-on VPN and a Quick Settings tile, and keeps passwords in the Android Keystore.
- `stayline-session` crate with the code the Windows service and the Android engine share.
- Licence files, contributing guide, security policy, code of conduct and documentation in `docs/`.
- GitHub Actions: CI (format, clippy, tests) and a release workflow that publishes the MSI.

### Changed
- Logins open a fresh connection to the gateway instead of reusing a kept-alive one that may have died. HTTP errors now include their causes.
- Android connects on the first attempt: the app no longer mistakes the VPN coming up for a network change. Until the tunnel has been up once, it shows one calm "Connecting" step.
- Android bundles the Manrope typeface instead of downloading it through Google Play Services, so it works on phones without Google.
- The Windows app now matches the Android design. It uses ruby, topaz and emerald for off, working and protected, the Manrope typeface, and a cut-gem connect button. The button changes colour with the state, spins a ring while connecting and ripples once when the tunnel comes up.
- Other motion:
  - a sliding sidebar pill;
  - a sliding appearance switch;
  - pages that fade in;
  - dialogs that rise into view;
  - a password field that shakes when the password is rejected.
- New brand mark: a ruby and emerald gem. It is used for the application icon, the in-app logo and the installer artwork. The tray icon is a small gem in the colour of the state.
- Installer sources moved to `packaging/`.

## [0.1.1] - 2026-10-03

### Added
- Sign-in prompt when connecting a profile without a saved password; a rejected password brings it back.
- First connection to a gateway with a non-public certificate asks for approval in a dialog showing the fingerprint.
- The app starts the service when it is installed but stopped, and tells apart a missing, starting and stopped service.
- Stayline VPN artwork in the installer.

### Changed
- Product name is now **Stayline VPN**.
- New interface: white sidebar with Status, Profiles, Settings and About, compact panels and a new vector logo.
- Gateway address and port are separate fields.
- Fingerprints are shown in blocks of eight.
- An installer with the same version now replaces the installed one instead of installing beside it.

### Fixed
- Long placeholders and fingerprints no longer overflow their fields; the Slint badge on About fits its panel.

## [0.1.0] - 2026-10-02

### Added
- FortiGate SSL-VPN login, tunnel over PPP and certificate pinning.
- Automatic reconnect after network changes, Wi-Fi drops and sleep, with back-off and offline detection.
- Windows service that owns the tunnel, and a tray app with status, profiles and settings.
- Saved passwords encrypted with Windows DPAPI.
- Company-managed connections, multiple profiles and trust-on-first-use certificates.
- MSI installer, including company installers with a built-in connection.
- About page listing every open-source component and its licence.

[Unreleased]: https://github.com/priyansx01/stayline-vpn/compare/v0.2.1...HEAD
[0.2.1]: https://github.com/priyansx01/stayline-vpn/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/priyansx01/stayline-vpn/compare/v0.1.1...v0.2.0
[0.1.1]: https://github.com/priyansx01/stayline-vpn/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/priyansx01/stayline-vpn/releases/tag/v0.1.0
