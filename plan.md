# 120fps Player — Implementation Plan

Real-time frame-rate up-conversion video player for Android (Galaxy S24 Ultra), with iPhone 17 as a later phase. Full research backing this plan lives in [`docs/research/feasibility-report.md`](docs/research/feasibility-report.md).

## The 60-second version

Play any local video and use the phone's own GPU/NPU to generate extra frames in real time, so a 24fps film or 30fps clip can display smoothly on a 120Hz screen instead of the panel just repeating the same frame.

**Verdict:** 1080p → 60fps is proven feasible on both devices. 1080p → 120fps is possible but Android-only, best-effort, and uses classical motion-compensated interpolation (not AI) — neural frame interpolation (RIFE-class networks) was directly measured and refuted as too slow on this hardware (~4fps at 1080p vs. the ~36-96fps generation rate required). iPhone cannot be promised 120Hz at all — iOS revokes high refresh-rate requests under thermal load, which sustained interpolation itself causes. 4K interpolation isn't feasible on this hardware generation at any target rate.

**Product shape:** ship 60fps as the reliable core feature everywhere; ship 120fps as an explicitly-labeled, best-effort "boost" mode on Android only.

## Why not just fork VLC or mpv?

- **VLC**: on Android it decodes straight to opaque hardware surfaces. VLC's own bug tracker confirms filters can't run on opaque frames — you'd need `:no-mediacodec-dr` (forces a CPU copy of every frame, killing the frame budget) and you'd have to compile your filter into libvlc itself. Excluded.
- **mpv**: its famous `--interpolation` flag is *not* motion interpolation — per the mpv wiki it's "no motion-based prediction whatsoever, just a simple blending operation of two frames." It smooths judder but doesn't synthesize new motion. Real motion compensation only exists via a VapourSynth filter bolted on (this is what SmoothVideo Project/SVP does), and mpv-android has no VapourSynth support.
- **Media3/ExoPlayer** (Android's modern player toolkit) is the right foundation: its `GlShaderProgram` effect contract explicitly allows a filter to consume N input frames and emit M output frames, each with its own timestamp — exactly the shape frame interpolation needs.

## Architecture

```
MediaCodec decode (async, 1-2 frame lookahead)
  → GL texture (zero-copy)
  → scene-cut gate (duplicate across cuts, never interpolate them)
  → interpolation engine  ← the core of the product
  → retimed presentation timestamps
  → GPU surface requesting 120Hz, adapting to whatever the OS actually grants
  → audio delayed to match the added video latency
```

**Engine ladder** (falls back automatically under thermal pressure or low confidence):
1. **GPU MEMC** (default) — compute-shader motion estimation + motion-compensated warp with occlusion handling. Portable to any GPU; accelerated by Qualcomm's `GL_QCOM_motion_estimation` hardware extension where available.
2. **Blend floor** — mpv-style frame blending. Artifact-free, cheap, the automatic fallback for scene cuts and thermal throttling.
3. *(Stretch goal)* NPU-assisted mode reusing the video codec's own motion vectors (the way Anthropic's research found a real 2026 paper, "ANVIL," doing exactly this on this same chipset) to cut GPU cost further.

We deliberately do **not** build a RIFE/AI-neural-network interpolator — it was measured on the actual target GPU at ~250ms per frame, roughly 10-25x too slow for real-time use here.

## Phases

Each phase ends in an installable APK you sideload and test, with results logged back to `docs/device-reports/`.

| Phase | Goal |
|---|---|
| 0 | Scaffold + basic player: open a local file, play it, request 120Hz, on-screen stats HUD (fps, dropped frames, display mode, thermal, battery) |
| 1 | **Decision-gate spikes**: (A) can Media3's effect pipeline actually emit *more* frames than it receives during live playback — undocumented territory; (B) does the S24 Ultra actually grant a video app 120Hz, or silently cap it; (C) is Qualcomm's hardware motion-estimation extension present and fast on this phone's driver. Each spike is a debug screen inside the same app. Results decide the engine architecture. |
| 2 | Frame pacing/retiming infrastructure + the blend-floor "smooth" mode — the first thing that visibly does something |
| 3 | Full GPU motion-compensated interpolation engine, targeting 60fps reliably and 120fps where the phone allows |
| 4 | Quality and robustness: scene-type detection, strength control, thermal step-down, 2-hour battery/heat soak test, blind A/B quality comparison |
| 5 *(stretch)* | NPU-assisted mode |
| 6 *(later)* | iPhone 17 app — spikes Apple's new `VTFrameProcessor` system API first, since if it performs well it replaces most of the custom engine |

## What we're explicitly not building

No AI/neural interpolation port, no VLC fork, no 4K real-time interpolation, no promise of 120Hz on iPhone, no fighting the OS's app classification to force a refresh rate — the app adapts to whatever the OS actually grants rather than trying to override it.

## Full detail

See [`docs/research/feasibility-report.md`](docs/research/feasibility-report.md) for the complete research: how VLC/mpv/SVP actually work internally, real benchmark numbers for every interpolation algorithm family, exact hardware specs and thermal limits for the S24 Ultra and iPhone 17, the platform APIs involved on each side, and a ranked list of the biggest open risks with a cheap way to test each one.
