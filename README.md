# 120fps Player

A video player for Android that uses the phone's own GPU to generate extra frames in real time, so a 24fps film can play smoothly on a 120 Hz display instead of the panel simply repeating frames.

Think VLC or KMPlayer, but with motion interpolation — the "soap opera effect" your TV does — running live on the device during playback.

> **Status: early.** Phase 0 — a working player with a diagnostics HUD — builds, is CI-green, and now runs: it has been exercised on an Android 12 emulator. **No frame interpolation is implemented yet**, and nothing has been verified on the target phone. See [Current status](#current-status).

## The honest verdict

Before writing any code, the feasibility was researched in depth and the load-bearing claims were adversarially checked. The results shape the entire product, so they are stated up front rather than buried:

| Target | Verdict |
|---|---|
| **1080p → 60fps** | **Feasible and proven.** Shipping commercially on phones today; sustained for 30 min on this exact chipset in published research. This is the dependable core feature. |
| **1080p → 120fps** | **Android only, best-effort.** Possible via classical motion estimation, at game-class power draw, and dependent on the OS actually granting 120 Hz. Never promised. |
| **iPhone → 120fps** | **Not promised, ever.** iOS treats a high-refresh request as a revocable hint and revokes it under exactly the thermal load interpolation creates. iOS targets 60. |
| **4K → anything** | **Not feasible** on this hardware generation. The memory-bandwidth arithmetic does not close. |

Notably, **AI frame interpolation (RIFE and similar) was measured and ruled out** — roughly 4fps at 1080p on a Snapdragon 8 Gen 3's GPU, against a budget that needs 36–96 generated frames per second. The engine is classical motion-compensated interpolation, the same family TVs and dedicated phone MEMC chips use. Full reasoning and sources: [`docs/research/feasibility-report.md`](docs/research/feasibility-report.md).

## Current status

Phase 0 is built: a Media3/ExoPlayer-based player that opens a local video, plays it full-screen with
transport controls, requests 120 Hz from the display, and shows a live diagnostics panel with rendered
fps, dropped frames, **the refresh rate the OS actually granted**, the source frame rate, thermal
headroom, and battery draw.

That panel is the point of Phase 0 — the granted-refresh-rate reading tells us how much of the 120fps
plan is reachable at all. It also reports the **cadence for the rate that was granted**: the ratio,
the frames per second an interpolator would have to synthesise, and the per-frame budget. Those are
computed live by `:interp-core` from the two observed rates, and they are the plan, not a
measurement — nothing is being interpolated.

**Verified running, on an emulator.** The app has been installed and exercised on an Android 12 / API
31 emulator: it plays, letterboxes correctly, seeks, survives being killed, and reports plausible
numbers. That run found four defects that code review had missed, and is written up in
[`docs/device-reports/emulator-api31-results.md`](docs/device-reports/emulator-api31-results.md).

**Not yet verified on the target phone, and Phase 0 is therefore not done.** The emulator's panel is
60 Hz and its decoder is software, so it cannot answer either question Phase 0 exists for: whether
Samsung's LTPO panel will grant 120 Hz to a decoding app, and what happens thermally under load.
Closing that gap is the next step — see the [Phase 0 checklist](docs/device-reports/phase-0-checklist.md).

## Try it

A prebuilt debug APK is committed in the repo:

**[`dist/120fps-player-0.1.0-debug.apk`](dist/)** — download it, transfer to an Android 12+ phone, install (allow "install from unknown sources").

CI also builds one on every push, if you want the build for a specific commit: [Actions tab](https://github.com/Srimi1/120fps-player/actions) → latest green **Android CI** run → **Artifacts** → `app-debug`.

**Using it:** tap **Open a video** or send it a file from any file manager with *Open with*. Tap once for the controls, double-tap to pause. Tap the `DIAGNOSTICS` panel to expand it.

If you run it, [file a report](docs/device-reports/phase-0-checklist.md) — device data is the bottleneck on this project.

## Building

Requires JDK 17 and the Android SDK (compileSdk 36).

```bash
./gradlew :app:assembleDebug
```

Dependencies are pinned to AGP 8.13 / Kotlin 2.3.21 / compileSdk 36; androidx versions are held at the last releases compatible with that floor. See [`docs/DECISIONS.md`](docs/DECISIONS.md) before bumping anything.

## Roadmap

Each phase ends in an installable APK and an on-device report.

| Phase | What |
|---|---|
| **0** ✅ built, runs | Player scaffold, 120 Hz request, diagnostics HUD. Awaiting an on-device report to be *done*. |
| **1** | Three de-risking spikes: can Media3 emit extra frames during playback; will the S24 Ultra grant 120 Hz to a video app; are Qualcomm's hardware motion-estimation GL extensions available |
| **2** | Frame pacing/retiming infrastructure + blend-based smoothing (the first visible feature) |
| **3** | The real engine: GPU motion-compensated interpolation |
| **4** | Quality and robustness — scene detection, strength control, thermal step-down, battery honesty |
| **5** | *(stretch)* NPU-assisted mode reusing the video codec's own motion vectors |
| **6** | *(later)* iOS, starting by benchmarking Apple's new system frame-interpolation API |

Detail: [`plan.md`](plan.md).

## Project rules

This project has a [**constitution**](CONSTITUTION.md) — the non-negotiable engineering rules, each traced to the evidence that produced it. It exists because several of the research findings are counter-intuitive and easy to undo with good intentions. Current standing: [`docs/compliance-audit.md`](docs/compliance-audit.md).

The short version: claims carry their evidence tier, "verified" means measured on a real device, the OS decides the refresh rate and we adapt to it, there is always an artifact-free fallback, the viewer can always turn the effect off, and a phase is not done because CI is green.
