---
title: Verifying releases
description: Check that a release APK was built from its published source.
---

There are two ways to check a release APK. The first takes a minute and trusts GitHub. The second takes a few minutes of building and trusts nobody.

## Check the build attestation

Releases from `v0.5.0` onwards carry build-provenance attestations. With the GitHub CLI:

```sh
gh attestation verify MaterialXray-v0.10.0-6ae73a5.apk --repo AetherMagee/MaterialXray
```

This proves that the repository's release workflow built the file on GitHub Actions, and names the commit it was built from.

## Rebuild it yourself

Releases after `v0.10.0` are reproducible: building the tagged source produces the same files as the release APK, byte for byte. Only the signature differs. A match doesn't depend on trusting GitHub, the build machine or the maintainers.

### What you need

- Linux on x86_64. macOS and Windows are untested.
- Git, `unzip`, a JDK 17 or newer to start Gradle, and Go 1.21 or newer on `PATH`. Go downloads the exact toolchain the build pins.
- The Android SDK with these packages:

  ```sh
  sdkmanager "platforms;android-37.2" "build-tools;37.0.0" "ndk;30.0.16248370" "cmake;4.1.2"
  ```

- Network access. The build downloads Gradle, its dependencies, Xray-core and the geodata, and checks every file against a checksum recorded in the repository. Gradle also downloads the JDK 21 it builds with if it can't find one.

### Build

```sh
git clone https://github.com/AetherMagee/MaterialXray
cd MaterialXray
git checkout v0.10.1
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew :app:assembleRelease -x validateReleaseTelemetry
```

Point `sdk.dir` at your SDK, or set `ANDROID_HOME` instead. The source archive attached to a release builds the same APK as a clone.

`validateReleaseTelemetry` only makes sure an official build can upload crash-report symbols. The upload token doesn't change the APK, so skipping the check is safe.

The unsigned APKs land in `app/build/outputs/apk/release/`: `app-universal-release-unsigned.apk` and one per ABI, such as `app-arm64-v8a-release-unsigned.apk`.

### Compare

Unzip the release APK and your build, and compare the two:

```sh
mkdir release rebuilt
unzip -q MaterialXray-v0.10.1-<commit>.apk -d release
unzip -q app/build/outputs/apk/release/app-universal-release-unsigned.apk -d rebuilt
diff -r release rebuilt && echo Reproduced
```

For a per-ABI APK, unzip the release file ending in `.<abi>.apk` and `app-<abi>-release-unsigned.apk` instead.

`diff` prints nothing and the last command prints `Reproduced` when every file matches. If it lists differences, check that you built the release's tag and that `git status` shows no changes.

### What the build pins

- **Gradle and every dependency and plugin:** checksums in `gradle/verification-metadata.xml` and `gradle/wrapper/gradle-wrapper.properties`.
- **Xray-core:** upstream's arm64-v8a and x86_64 Android builds by checksum. The armeabi-v7a build is compiled from a pinned commit with a pinned Go toolchain.
- **Geodata and the CA bundle:** pinned releases, checked by checksum.
- **Crash-report IDs:** derived from the build's own output instead of generated at random.
