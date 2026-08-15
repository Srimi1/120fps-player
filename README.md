# 120fps Player

A video player for Android that uses the phone's own GPU to generate extra frames in real time, so a 24fps film can play smoothly on a 120 Hz display instead of the panel simply repeating frames.

Think VLC or KMPlayer, but with motion interpolation — the "soap opera effect" your TV does — running live on the device during playback.

> **Status: early.** Phase 0 (a working player with a diagnostics HUD) builds and is CI-green. **No frame interpolation is implemented yet**, and nothing has been verified on real hardware. See [Current status](#current-status).

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

Phase 0 is built: a Media3/ExoPlayer-based player that opens a local video, plays it, requests 120 Hz from the display, and shows a live HUD with rendered fps, dropped frames, **the refresh rate the OS actually granted**, thermal headroom, and battery draw.

That HUD is the point of Phase 0 — the granted-refresh-rate reading tells us how much of the 120fps plan is reachable at all.

**Not yet verified on a device.** CI proves the app compiles and packages; it proves nothing about whether it plays video correctly. Closing that gap is the next step — see the [Phase 0 checklist](docs/device-reports/phase-0-checklist.md).

## Try it

CI builds a debug APK on every push. Grab it from the [Actions tab](https://github.com/Srimi1/120fps-player/actions): open the latest green **Android CI** run → **Artifacts** → `app-debug`. Install on an Android 12+ device.

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
| **0** ✅ built | Player scaffold, 120 Hz request, diagnostics HUD |
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
