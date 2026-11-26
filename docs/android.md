# Android

The Android app uses the same Rust tunnel as Windows: login, TLS with pinning, PPP and the reconnect supervisor are all `stayline-core`, unchanged. Only the parts that touch the platform are new.

```
Compose UI ──StateFlow──▶ StaylineVpnService (VpnService, foreground)
                                │  JNI: six calls, JSON payloads
                                ▼
                       libstayline_mobile.so  (crates/mobile)
                       ├─ stayline-core supervisor   (shared with Windows)
                       ├─ AndroidHooks: TunnelHooks
                       └─ packet pump on the VPN interface's fd
```

| Piece | Windows | Android |
| --- | --- | --- |
| Tunnel engine | `stayline-svc` (service) | `crates/mobile`, loaded by the app |
| Network adapter | Wintun | `VpnService.Builder`, its file descriptor handed to Rust |
| Route to the gateway | pinned host route | the app excludes its own traffic from the VPN (`addDisallowedApplication`) |
| Network changes | IP Helper notifications | `ConnectivityManager` default-network callback, leaving Doze |
| Saved passwords | DPAPI | AES-GCM key in the Android Keystore |
| Status to the UI | named pipe, JSON lines | JNI callback, the same JSON `stayline_ipc::Event`s |

`crates/session` holds the code both platforms share between the core and their UI: turning a profile into supervisor settings, mapping status to `TunnelState`, and deciding what a "needs you" stop is about.

## Behaviour

- **Reconnects keep the interface.** If the gateway hands out the same settings again, the existing VPN interface is reused, so apps never see the VPN go away and nothing leaks while the tunnel is rebuilt.
- **Network switches.** A change of default network (Wi-Fi to mobile data) rebuilds the tunnel at once instead of waiting for echoes to time out.
- **IPv6.** The tunnel carries IPv4 only. In full-tunnel mode the app also captures IPv6 and drops it, so it cannot leak around the VPN.
- **Split DNS.** Android has no per-domain DNS for VPNs, so the gateway's split-DNS servers answer all lookups, and in split-tunnel mode they are routed through the tunnel.
- **Always-on VPN.** Supported. Android starts Stayline at boot and it connects with the active profile and its saved password. Together with "Block connections without VPN", traffic never leaves outside the tunnel.
- **Quick Settings tile.** Toggles the VPN. It opens the app when a password or the VPN permission is needed.

Not supported, as on Windows: SAML, token codes, IPsec.

## Building

You need [rustup](https://rustup.rs), the Android SDK with NDK 28.2.13676358 (r28c), and JDK 17. Releases are built with exactly these versions, so a rebuild matches them byte for byte:

| Tool | Version |
| --- | --- |
| Rust | 1.99.0, from `rust-toolchain.toml` |
| Android NDK | 28.2.13676358 (r28c) |
| cargo-ndk | 4.1.2 |
| JDK | 17 |

```sh
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk --version 4.1.2 --locked
```

Point `android/local.properties` at the SDK (`sdk.dir=...`), then:

```sh
cd android
./gradlew assembleDebug -Pstayline.abis=arm64-v8a   # one ABI, for a phone
./gradlew assembleRelease                           # all ABIs, shrunk
```

The `cargoBuild` task builds `crates/mobile` with `cargo ndk` into `app/src/main/jniLibs` before every build. Pass `-Pstayline.skipCargo=true` to reuse libraries already there.

Release builds are unsigned unless the release key is in the environment (see [Releasing](#releasing)). To install one on a phone while testing, sign it with the debug key: `-Pstayline.debugSign=true`.

Each CPU type gets its own APK and version code: `versionCode` × 10 plus 1 for armeabi-v7a, 2 for arm64-v8a and 3 for x86_64. Version 0.2.0 (code 20) is 201, 202 and 203.

## Releasing

Releases are built on GitHub's Linux runners and signed with the project's key. F-Droid rebuilds the same tag. If its unsigned APKs match the signed ones apart from the signature, it ships the project-signed APKs, so the app updates from either source. CI builds the APKs twice, on different systems and paths, and fails if they differ.

### Once: the release key

The key signs every future update. If it is lost, no update can replace installed copies, so keep it out of the repo and back it up offline.

```sh
keytool -genkeypair -keystore stayline-release.jks -alias stayline   -keyalg RSA -keysize 4096 -validity 10000
keytool -list -v -keystore stayline-release.jks -alias stayline   # note the SHA-256
```

Add these secrets to the GitHub repository (Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `STAYLINE_KEYSTORE_BASE64` | `base64 -w0 stayline-release.jks` |
| `STAYLINE_KEYSTORE_PASSWORD` | the keystore password |
| `STAYLINE_KEY_ALIAS` | `stayline` |
| `STAYLINE_KEY_PASSWORD` | the key password |

The certificate's SHA-256 (lowercase hex, no colons) goes in `AllowedAPKSigningKeys` in the F-Droid recipe.

### Each release

1. Set the version in `Cargo.toml` (workspace) and in `android/app/build.gradle.kts`. Raise `versionName`, and raise `versionCode` by at least one: 0.2.0 is 20, so the next release is 21 or more.
2. Add `fastlane/metadata/android/en-US/changelogs/<code>.txt` for each of the three version codes, and update `CHANGELOG.md`.
3. Push a `v<version>` tag. The release workflow publishes the MSI, then attaches `stayline-<version>-<abi>.apk` and `SHA256SUMS`.

F-Droid's update checker notices the tag and builds it.

### Once: submitting to F-Droid

`packaging/fdroid/io.github.priyansx01.stayline.yml` is the recipe. Put the release certificate's SHA-256 in it, then:

1. Fork [fdroiddata](https://gitlab.com/fdroid/fdroiddata) on GitLab and add the recipe as `metadata/io.github.priyansx01.stayline.yml`.
2. Open a merge request. F-Droid's CI lints and builds it, and reviewers may ask for changes.

The store listing (title, descriptions, icon, banner, screenshots and changelogs) is read from `fastlane/metadata/android/en-US/` in this repo.

## Size

The APKs are split by ABI. Native code is stored compressed, R8 shrinks the Kotlin side, icons are drawn in code instead of coming from an icon library, and Manrope is a downloadable font, so the APK carries no font file.

## Testing on a device

```sh
adb install app/build/outputs/apk/release/app-arm64-v8a-release.apk
adb logcat -s stayline
```

Check a gateway with `stayline-probe` on a desktop first. Then on the phone:

1. Connect. The gem turns emerald and the uptime starts.
2. Switch between Wi-Fi and mobile data. It reconnects within a few seconds.
3. Turn on airplane mode. It shows "Waiting for network", then reconnects when you turn it off.
4. Enter a wrong password. The sign-in sheet opens and the field shakes.
5. Use a gateway with a self-signed certificate. The trust sheet shows the fingerprint, and a changed certificate shows the warning.
6. Turn on Always-on VPN with "Block connections without VPN", then reboot. The VPN comes back up by itself.
7. Open an IP-leak test site. It shows the gateway's IPv4 address and no IPv6.
