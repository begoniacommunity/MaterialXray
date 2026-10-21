# Material Xray

Material Xray is a proxy client/subscription manager for Android. Powered by [Xray-core](https://github.com/xtls/xray-core), it routes device traffic through native tunnel or policy routing with superuser rights, or using Android build-in VPN service in rootless mode, being flexible in every detail.

### Features

- User-friendly interface with basic proxy stuff that are easy for the average user with "advanced" mode for power users:
  - Firstly, we're doing our best to create not just powerful, but also a really beautiful app. The app based on Material 3 (non-Expressive, and never will be), so it fits best in AOSP setups.
  - Simple but customizable "Home" screen: see live & total traffic use of current session, change subscriptions order or visible name, show or hide provider description — and eventually, choose the style of the "Start" button that you like best, etc.
  - Manage traffic flow in "Routing" section with easily configurable custom or subscription-specific rules, reorder to change its priority, and control apps traffic separately
- Per-app routing settings: use the proxy, bypass it, or connect through a specific server, with option to use multiple VPNs at the same time (with root, requires transparent proxy mode to be enabled)
- Bypass local network area, choose whether to use IPv6, and set up separate DNS for proxied and directs domains with encrypted queries
- Fetch and install Xray-core updates from app or install yours from file; configurable internal data updates behavior, automatic updates of components such as core or geodata, etc.
- Configurable service notification, providing quick access to internal statistics more relevant to you, as well as QS toggle
- Broad capabilities for subscription hosters, with custom headers, including subscription/server-specific routing, exposing core requirements, DNS profile, etc.
- (root) Supports tunneling tethered clients, meaning that people connected to your mobile hotspot can use current proxy connection on device, pretty much the same what [VPN Hotspot](https://github.com/mygod/vpnhotspot) do
- (root) Core outbound binding to the active physical interface by default, avoiding trivial `tun` fetching by some apps (custom name can also be specified)
- (root) Experimental support for [transparent proxy](https://xtls.github.io/en/document/level-2/transparent_proxy/transparent_proxy.html) mode (often called TProxy), reducing the scope of VPN detections by some apps
- If you have a good understanding of working with Xray, there's **way more** to explore in app settings

### On state pressure by restrictions circumvention

Be aware that even a rooted implementation isn't completely undetectable. Apps still may use other signals, such as bad routing policies or simply root detection. Although it hasn't reached that point yet, it is also clearly possible to directly check whether the proxy manager packages are present on the device, like what banks do to find "viruses" or root manager as "vulnerability" on device.

Apps known to contain mechanisms (actually traps) that ping state-blocked websites to determine whether you are using a VPN (and likely having scraped an IP of VPN host that hadn't yet been blocked), ideally should be set to "Not proxied" with rooted tunnel spoofing or transparent proxy; **do not** rely on domain routing rules, these aren't enough to cover this approach.

Keep in mind that Material Xray doesn't hide another VPN you run alongside it with Tunnel-in-Tunnel mode; if the Material Xray itself falls back to rootless mode (or Android's always-on VPN forces rootless mode), the connection becomes a simple, easily detectable Android's VpnService with `tun0` again. We will add accidental switch protection in the future builds. 

To better handle edge cases, check out [VPN Hide](https://github.com/okhsunrog/vpnhide) and use it in pair with Material Xray.

## Development

This project is AI-assisted. The app is written in Kotlin with Jetpack Compose. Core logic lives in plain JVM modules so it can be reused in a future Compose Multiplatform desktop app.

### Build yourself

JDK 21, the Android SDK, Go 1.21 or newer, and Git are required; see [CI](.github/workflows/ci.yml) for SDK packages. Set your SDK path through `ANDROID_HOME` or `sdk.dir` in `local.properties`. 

For debugging purposes, run to build `debug` variant:

```sh
./gradlew test :app:assembleDebug
```

APKs are in `app/build/outputs/apk/debug`.

For signed release, first add your signing details to `local.properties` in the repository root:

```properties
releaseKeystorePath=/path/to/release.keystore
releaseKeyAlias=alias_name
releaseKeyPassword=key_password
releaseStorePassword=store_password
```

Run unit tests and build signed `release` variant:

```sh
./gradlew test :app:assembleRelease -x :app:validateReleaseTelemetry
```

Note: `-x :app:validateReleaseTelemetry` skips the Sentry token requirement for local builds. Omit it if you set `SENTRY_AUTH_TOKEN`.

Signed APKs are in `app/build/outputs/apk/release`.

To build an APK for one architecture, edit the ABI split settings in `app/build.gradle.kts`: keep only the desired ABI in `include` (for example, `include("arm64-v8a")`) and set `isUniversalApk = false`.

The build commands above include unit tests. You may want to run Android lint separately:

```sh
./gradlew :app:lintDebug
```

For Kotlin formatting checks and static analysis, install [prek](https://prek.j178.dev), then run:

```sh
prek install
prek run --all-files
```

Use `./gradlew ktlintFormat` to fix formatting.

## License

Copyright (C) 2026 Material Xray contributors.

Material Xray's original source code, documentation, and artwork are licensed under the [GNU General Public License, version 3 or later](LICENSE), without any warranty.

Third-party components and derived files retain their respective licenses. See [Third-Party Notices](THIRD_PARTY_NOTICES.md) for attribution, license details, and corresponding-source information.
