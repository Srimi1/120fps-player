# Prebuilt APK

`120fps-player-0.1.0-debug.apk` — debug build, `versionName` 0.1.0, `versionCode` 1.

| | |
|---|---|
| Package | `dev.fps.app` |
| Min Android | 12 (API 31) |
| Compiled against | API 36 |
| Size | ~35 MB |
| Signing | debug key — installs alongside anything else, no release signing |

## Install

Transfer to an Android 12+ phone and open it. You will need to allow "install from unknown sources"
for whatever app you opened it with.

## Verify the download

```bash
shasum -a 256 120fps-player-0.1.0-debug.apk
```

```
52c4a0c124b7d8414749cf33994a4916aed73bf5bd3bbc0af5691bc2d2f91eba
```

## Why it is committed rather than left as a CI artifact

Device reports are the bottleneck on this project, and a GitHub Actions artifact requires an account,
navigating to the right run, and unzipping. A file in the repo is one click. The tradeoff is real —
a 35 MB binary sits in git history permanently and cannot be delta-compressed — and it was made
deliberately.

If you want the build for a specific commit rather than this one, CI still publishes one on every
push: **Actions** → the run → **Artifacts** → `app-debug`.

## Rebuilding it

```bash
./gradlew :app:assembleDebug
```

Output lands at `app/build/outputs/apk/debug/app-debug.apk`. Requires JDK 17 and the Android SDK
(compileSdk 36). The debug signing key is generated per machine, so a local rebuild will not match
the checksum above byte for byte.
