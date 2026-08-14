# Feasibility Synthesis: Real-Time 120fps Frame-Rate Up-Conversion Video Player
## Galaxy S24 Ultra & iPhone 17 — August 2026

---

## Executive Summary (read this first)

**1080p 24/30→60 fps is feasible and shipping today on both devices. 1080p→120 fps via any neural network is refuted by direct measurement on the exact target SoC. 120 fps output is achievable only via classical MEMC (block-matching) on Android, with heavy battery cost and artifact risk; on iPhone it is additionally blocked by OS refresh-rate policy regardless of compute. 4K interpolation at any target rate is out of reach. The honest product answer: build for 60 fps as the dependable signature feature, with 120 fps as a best-effort Android-only boost mode.**

---

## 1. How Players Like VLC/mpv Work

### The universal pipeline

Every serious player follows the same shape:

```
container input → demuxer → decoder (HW: MediaCodec / VideoToolbox)
    → [filter chain]  ← the only place an interpolator can live
    → renderer/vout → display surface (paced by vsync)
```

An interpolation stage is a filter with an unusual contract: it consumes frames N and N+1 and emits 2–5 frames with *new* presentation timestamps. That asymmetric N-in/M-out contract is what disqualifies most filter APIs, which assume 1:1 frame flow.

### VLC — worst foundation for this job

VLC is a capability-based plugin system (libvlccore + modules selected via `module_need`). Video filters implement `filter_t` with `vlc_filter_operations.filter_video` (`picture_t*` in → `picture_t*` out, per `include/vlc_filter.h`). Two disqualifying problems on mobile, both well-sourced:

1. **Opaque direct rendering.** On Android, VLC decodes via MediaCodec straight to opaque hardware surfaces. VLC's own trac ticket #18078 states filters *cannot run on opaque frames* — there is no MediaCodec opaque-to-I420 path. Filtering requires `:no-mediacodec-dr`, forcing a CPU copy-back of every decoded frame — exactly the overhead a 120fps pipeline cannot afford.
2. **No sideloading.** Custom filters must be compiled into libvlc itself (libvlcjni on Android, VLCKit on iOS). You are forking libvlc, not extending it.

**Verdict: exclude VLC.**

### mpv — and what `--interpolation` actually is

mpv's pipeline: demuxer → `vd_lavc` → vf filter chain → `vo_gpu`/`vo_gpu-next`.

**Critical correction (adversarially verified, refuted claim):** mpv's famous `--interpolation` is **not** motion interpolation. The mpv wiki says verbatim it has "no motion-based prediction whatsoever, just a simple blending operation of two frames." It is temporal blending ("smoothmotion") along the display timeline: at 24fps on 60Hz, each frame shows ~2.5×, with the fractional slot blended via `--tscale` (default `oversample`; linear/mitchell/gaussian get progressively blurrier). It requires `--video-sync=display-resample` and is auto-disabled by `--interpolation-threshold` when source fps ≈ refresh. It removes judder and deliberately *preserves* the perceived original frame rate — no soap-opera effect because no new motion is synthesized. Similarly, mpv's `--glsl-shader` hooks are per-frame spatial passes and cannot emit extra frames.

True motion compensation in mpv comes only through `vf=vapoursynth` (mpv built with VapourSynth: streamed clip, `buffered-frames` window default 4, script reload on every seek).

### SVP — the reference MEMC design and the mobile existence proof

SVP (SmoothVideo Project) generates a VapourSynth script calling **svpflow1** (hierarchical block-matching motion-vector search, a refactored MVTools2) and **svpflow2** (motion-compensated frame rendering), injected into mpv via the VapourSynth filter and coordinated over mpv's JSON IPC. Its RIFE AI mode (IFNet via ncnn-Vulkan/TensorRT) is real-time only on desktop GPUs.

Crucially, **SVPlayer** (SVP's own mobile app, both stores) proves the mobile case: an mpv-based player running CPU SVPflow MEMC on-device, targets 48/60/120 fps, requiring ~A13 / Snapdragon 865-class for 1080p, with battery drain "like top games." Its mobile RIFE mode only *streams* frames from a desktop PC — no shipping mobile app runs AI VFI on-device in real time.

KMPlayer contributes nothing: desktop is a DirectShow graph host; mobile has no interpolation ("8K/60FPS optimized" is marketing). ijkplayer is unmaintained (FFmpeg n3.4, dead since ~2024) — exclude.

### Where the interpolator slots, by foundation

| Foundation | Access to decoded pixels | Cost |
|---|---|---|
| **Media3/ExoPlayer** | `setVideoEffects()` hands you a GL texture; `GlShaderProgram` contract *explicitly* permits N-in/M-out with per-output `presentationTimeUs` | Easiest (Android only) |
| **libmpv** | VapourSynth filter; proven by SVPlayer on both OSes | Custom native builds of mpv+VapourSynth+filter; mpv-android is not an importable AAR and lacks VapourSynth (issue #38) |
| **AVFoundation (iOS)** | No filter hook in AVPlayer; use `AVPlayerItemVideoOutput.copyPixelBuffer` → Metal → CAMetalLayer, or AVAssetReader → interpolate → AVSampleBufferDisplayLayer with rewritten PTS | Build-it-yourself, but well-trodden |
| **libvlc** | Fork libvlc, lose hardware direct rendering | Prohibitive |

---

## 2. Interpolation Technology Options

### Family 1: Classical MEMC (block matching) — the only proven real-time family on phones

Hierarchical coarse-to-fine block matching produces motion-vector fields plus cover/uncover occlusion masks (MVTools/SVPflow lineage; also what every TV and Pixelworks chip does). **The only VFI family with demonstrated real-time 1080p performance on phone CPUs** — SVPlayer ships it on 2019-class silicon (A13/SD865 for 1080p). Cost knobs: search radius, per-level simplification.

*Artifacts:* halos/shimmer around moving objects against detailed backgrounds, ghosting trails, breakup on fast/complex motion, and the soap-opera effect — disliked strongly enough that the TV industry invented Filmmaker Mode to turn it off. Any product needs per-content defaults and an instant toggle.

### Family 2: Dense optical flow + warp

Flow itself is cheap: DIS optical flow (ECCV 2016, `cv::DISOpticalFlow`) runs ~3 ms per 1024×436 frame on a *single CPU core* (~1.7 ms without variational refinement) — so ~15–25 ms at 1080p on mobile CPU is plausible. But flow+warp alone has no occlusion reasoning; quality lands below tuned MEMC without added masking/inpainting. This is essentially what Lossless Scaling's LSFG does on desktop (at reduced flow resolution, and still heavy enough that enthusiasts offload it to a second GPU).

### Family 3: AI VFI (RIFE and successors) — refuted at 1080p real-time on mobile

The adversarial verification **refuted** the claim that RIFE-class networks run real-time at 1080p on either target. The evidence is direct and on-target-SoC:

- **GPU path:** a purpose-built, zero-copy Vulkan+NCNN+RIFE pipeline on Snapdragon 8 Gen 3 / Adreno 750 (MediaCodec → AHardwareBuffer → Vulkan external memory) measured **~4 fps at 1080p at 90%+ GPU utilization** (~250 ms/frame vs a 27.8 ms budget for 24→60 and 10.4 ms for 24→120). 9–25× too slow with the GPU already saturated. (~23 fps at 480p, ~10 fps at 720p, ~1 fps at 4K.)
- **NPU path:** ANVIL (arXiv 2603.26835, ASPLOS 2026) profiled RIFE on the Hexagon V75 via QNN: fits the 33.3 ms budget only at **360p–480p**, with INT8 quality collapse on two backends. The structural finding: RIFE is NPU-hostile — convolutions are 5.1% of cycles, memory-bound ops 95%. Specifically, `grid_sample` (backward warping, used by essentially every flow-based VFI net) has prohibitive latency on mobile NPUs, making **7 of 9 evaluated VFI methods non-viable at 1080p on NPU**. The bottleneck is operator-accelerator mismatch, not FLOPs.
- **Bandwidth sanity check:** RIFE v4.4 at 1080p hits only ~71–107 fps on an RTX 3090 with ~936 GB/s of bandwidth. The S24 Ultra has 77 GB/s. A memory-bound net that barely clears 100 fps on a 3090 cannot hit 36–96 generated fps on a phone.
- **Apple side:** no published ANE RIFE benchmark exists anywhere; SVP's own data pegs RIFE real-time at **576p@48fps on an M1** (roughly A19-GPU-class); ANE has no public API and RIFE's gather/warp ops force CPU/GPU fallback. All shipping iOS RIFE apps (FrameFlo, AI Video Smoother, Time Cut) are offline-export only.

**The one bright spot — ANVIL's architecture (not RIFE):** discard learned optical flow entirely, reuse **H.264 decoder motion vectors as a free motion prior** (the decoder already paid for motion estimation), pre-align on CPU/GPU, run only compute-bound INT8 residual refinement on the NPU. Result on Snapdragon 8 Gen 3: **12.8 ms per 1080p interpolated frame (inference), 28.4 ms median end-to-end** in an open-source Android player, sustained 1080p 30→60 for 30 minutes (94.9% of 54,623 frame pairs within the 33.3 ms budget), at 0.6 dB below RIFE on Vimeo90K. This is the single strongest existence proof for the product concept, and the codec-MV trick is the key architectural insight — it restores for video the advantage game frame-gen gets from engine-supplied motion vectors.

### Offline-only tools (for calibration of expectations)

FILM: 0.393 s/frame at 720p on a V100, midpoint-recursive (2ⁿ only). DAIN: ~10 hours for a 2-minute clip. EMA-VFI: 25–132 ms at only 512×512. **ffmpeg `minterpolate`** (mi_mode=mci): single-threaded, ~12 interpolated 1080p frames *per minute* (~0.2 fps) on a Ryzen 9 — two-plus orders of magnitude from real time; useful only as an offline quality reference.

### Why game frame-gen numbers don't transfer

DLSS-FG/FSR3/MetalFX interpolation are cheap because the engine hands them exact per-pixel motion vectors and depth for free; video FG must estimate motion from pixels — the expensive step — *except* that compressed streams carry codec motion vectors (ANVIL's insight). Conversely, video is far more forgiving on latency: games need FG to add <10 ms; a player just needs ≥1 frame of decode lookahead, which is an ordinary buffering + audio-delay offset (slightly slower seek), not an interactivity problem. But video is harder on content (cuts, credits, subtitles, letterboxing, pulldown) and on quality expectations (viewers stare at faces; game artifacts hide in motion at 100+ base fps).

### Mandatory pipeline features

- **Scene-cut gating:** interpolating across a cut produces grotesque morph frames. Every serious tool gates on it. On-device it's nearly free: codec hints (I-frames, high-residual blocks) + a cheap histogram/SAD check; on a cut, duplicate instead of interpolate.
- **Cadence math:** 24→120 is a clean integer 5× (t = 0.2/0.4/0.6/0.8) — actually cadence-cleaner than 24→60 (non-integer 2.5×, which needs arbitrary-timestep support: RIFE v4.x or MEMC; RIFE ≤v3/FILM only do 2ⁿ). 30→120 is 4×; 60→120 is 2× (cheapest).
- **Inverse telecine:** detect 3:2-pulldown (24-in-60) sources and IVTC before interpolating, or the interpolator chases pulldown judder.
- **Soap-opera controls:** per-content-type defaults, adjustable strength, instant toggle.

---

## 3. Hardware Reality on S24 Ultra & iPhone 17

### Compute ceilings — peak vs. sustained

| | S24 Ultra (SD 8 Gen 3 for Galaxy) | iPhone 17 (A19) |
|---|---|---|
| GPU peak | Adreno 750 ~3.0 TFLOPS FP32 / ~6 FP16 | ~7.4 TFLOPS FP16 measured (5-core GPU + neural accelerators) |
| Memory BW | 77 GB/s LPDDR5X | (comparable class) |
| NPU | Hexagon V75, ~34–45 TOPS INT8 (no official figure) | 16-core ANE ~38 TOPS class, **no public API** |
| Thermal stability | **~48–49%** in 3DMark stress (GPU roughly halves in ~20 min) | Base 17 has **no vapor chamber**; even 17 Pro holds only 61–64% |
| Display | 120 Hz, app-requestable via `Surface.setFrameRate()` | 1–120 Hz LTPO ProMotion (first non-Pro), OS-arbitrated |

**Plan around roughly half of peak.** Sustained SoC budget in a passively cooled slab is realistically ~3–5 W. Sustained interpolation costs game-class power (~4–6 W system) vs ~0.5–1 W for plain playback. The S24 Ultra loses 25–30% battery/hour gaming at 120 fps — a 2-hour interpolated movie would eat half its 5000 mAh battery and trigger throttling. iPhone 17's 3692 mAh battery and chamber-less chassis are worse. Phones that sustain such loads (RedMagic 10 Pro) use built-in fans and 7050 mAh cells.

### What Pixelworks/AFME prove — and don't

Dedicated MEMC silicon has done phone video interpolation to 120 fps since 2020 (OnePlus 8 Pro/Pixelworks Iris 5; Pixelworks X7 at ~10 ms latency; vivo Q1) — proof the UX is valuable and the computation is silicon-cheap. **But the adversarial check added a sobering nuance: even the dedicated X7 chip's *shipping video* spec is 24/25/30→60 fps, inside whitelisted apps; the only documented 120fps video insertion (OnePlus 8 Pro) was an experimental mode that forced the screen to 1080p.** Neither the S24 Ultra nor any iPhone carries such a chip, and none are app-accessible anyway.

Qualcomm's AFME 2.0 (game frame doubling 60→120 at iso-power) is driver/OEM-level, **but its primitives are public and app-usable on Adreno**: `GL_QCOM_frame_extrapolation` (future frame from two prior textures, with a BSD-3 sample repo) and `GL_QCOM_motion_estimation` (hardware motion-vector search between textures). These are a legitimate app-level MEMC backbone on the S24 Ultra — vendor-locked, no Apple equivalent, and extrapolation (not interpolation) semantics.

Two more on-device precedents: Samsung's own **Instant Slow-mo** on the S24 does on-device NN frame generation (GPU+NPU, one frame per 16.6 ms; older SoCs excluded for lacking throughput) — Samsung's stack does near-real-time NN VFI on this exact phone, just not in playback. And Apple's **MetalFX Frame Interpolation** (Metal 4, iOS 26) requires engine motion vectors + depth — game-only, unusable for video files.

### The display-policy problem (this is a real gate, not a footnote)

**iPhone:** third-party apps get >60 Hz only with `CADisableMinimumFrameDurationOnPhone` in Info.plist plus `preferredFrameRateRange` — and it's a *hint*. Apple's docs say the system disables fast rates under heat/low power; a documented Apple Forums case shows an app requesting 120 being hard-capped at 90 with no workaround, and DTS confirming apps "must be prepared to render at any framerate." Low Power Mode and the accessibility "Limit Frame Rate" setting force 60. SVP itself says iPhone 120 Hz "isn't really usable." **A workload that simultaneously heats the SoC (interpolation) and requests 120 Hz is precisely the case iOS throttles.**

**S24 Ultra:** apps demonstrably *can* present 120 unique fps (COD Mobile sustains 120fps), via `Surface.setFrameRate(120, FIXED_SOURCE)` — but it's also a documented hint. Two Samsung-specific traps: (a) LTPO **content-matching** — GSMArena measured video playback refresh locked to content (24fps→24Hz, 60fps→60Hz), so anything that *looks like* video playback gets refresh-matched down; your interpolated output must be presented as an ordinary GPU-rendered surface actually submitting 120 buffers/sec; (b) game-classification caps (Android 15's 60fps default for games, Samsung Game Booster) — the app must avoid being categorized as a game. User's "Standard" motion-smoothness mode caps the device at 60 Hz. No published case exists of a third-party *video player* holding verified 120 Hz on the S24 Ultra while decoding — must be validated on-device.

### iOS wildcard: VTFrameProcessor (verified: exists, real-time unproven)

Apple ships a system VFI API in iOS 26 (which iPhone 17 runs): `VTFrameProcessor` with **`VTFrameRateConversionConfiguration`** (quality-first, editing lane; arbitrary phases 0.0–1.0, so 24→120 is expressible) and **`VTLowLatencyFrameInterpolationConfiguration`** (explicitly pitched for real-time streaming; inserts N uniform frames between pairs). On-device ML, ANE-backed. **But:** zero published performance benchmarks on any iPhone; early developer reports show resolution-dependent failures (FRC failing at 640×480 on iPhone while working at 2048×1080, FB21243261; crashes in low-latency parameters; unanswered forum questions about live-stream suitability). It changes the iOS build-vs-buy answer *if* it benchmarks well — spike it first. Android has no system equivalent whatsoever; you build your own there.

---

## 4. The 120fps Question, Answered

Budget arithmetic that governs everything: 24→60 = 36 generated fps = **27.8 ms/frame**; 30→60 = **33.3 ms**; 60→120 = **16.7 ms**; 24→120 = 96 generated fps = **10.4 ms/frame** end-to-end. Best published mobile result (ANVIL, non-RIFE, NPU+codec-MV): 12.8 ms inference / 28.4 ms e2e at 1080p.

### Scenario verdicts

| Scenario | S24 Ultra | iPhone 17 |
|---|---|---|
| **1080p 24→60** | **FEASIBLE — proven, shipping.** Two independent routes: SVPlayer's CPU MEMC (in production since 2021 on weaker silicon) and ANVIL's NPU+codec-MV pipeline (thermally sustained 30 min on this exact SoC). Battery: game-like (~10%/30 min) for CPU MEMC; NPU route should be materially better (NPU sustains at roughly half GPU power). | **FEASIBLE.** A13 sufficed for 1080p CPU MEMC; A19 has huge headroom. VTFrameProcessor low-latency config may make it near-free to build. 60 Hz presentation is unconditionally granted. |
| **1080p 24→120** | **MARGINAL — MEMC only, with caveats.** No NN path: 10.4 ms budget is below ANVIL's 28.4 ms e2e (~at its bare inference number); RIFE-class is refuted outright. SVPlayer exposes a 120 target and Pixelworks does it in 10 ms silicon, but **no verified sustained 1080p→120 benchmark on 8 Gen 3 exists** (unconfirmed, not disproven). Route: SVPflow-style MEMC or `QCOM_motion_estimation`/`frame_extrapolation`-assisted GPU MEMC, plus display-policy handling (present as GPU surface, avoid game classification). Cost: 4–6 W, ~25–30% battery/hour, throttling risk over a full movie. | **NOT FEASIBLE as a promise.** Compute might stretch (A19 headroom for MEMC), but iOS refresh-rate policy is the binding constraint: 120 Hz is a hint the system silently revokes, especially under thermal load — which interpolation itself creates. SVP's own verdict: iPhone 120 Hz mode "isn't really usable." Ship 60 as the iOS ceiling; treat 120 as opportunistic. |
| **4K 24→60** | **NOT FEASIBLE (real-time, on-device).** RIFE-GPU measured ~1 fps at 4K on Adreno 750; ANVIL demonstrated only 1080p; CPU MEMC at 4K exceeds SVPlayer's stated envelope. Desktop 4K real-time RIFE needs ~RTX 4080-class. Only sane option: decode 4K, interpolate at 1080p, upscale — i.e., not true 4K interpolation. | **NOT FEASIBLE** on the same grounds; no public evidence VTFrameProcessor sustains 4K real-time (one forum report shows it *working* at 2048×1080 in an offline context — no fps data). |
| **4K 24→120** | **NOT FEASIBLE.** Roughly 8× the compute of the hardest demonstrated case, into a 77 GB/s memory system, in a passively cooled slab. Not a research-stretch — an arithmetic impossibility on this hardware generation. | **NOT FEASIBLE.** Same, plus display policy. |

### "Do we need to go till 60 FPS only?"

**Mostly yes — and the data says that's the right product, not a compromise:**

- **60 fps output at 1080p is the evidenced ceiling for *quality* interpolation** (NN-adjacent or well-tuned MEMC) on both devices. It is shipping commercially (SVPlayer) and demonstrated thermally sustained (ANVIL). Build this as the reliable signature feature.
- **120 fps is worth shipping as an Android-only "boost" mode**, clearly labeled best-effort: MEMC/extrapolation-based (visible artifact risk), heavy battery drain disclosed, and dependent on the OS actually granting 120 Hz. Note the irony: 24→120 is *cadence-cleaner* than 24→60 (integer 5× vs 2.5×), so when the compute and display grants line up it looks genuinely better — but you cannot promise it.
- **On iPhone, do not market 120 at all.** The OS will make you a liar. 60 fps with flawless pacing beats an unstable 90-that-was-supposed-to-be-120.
- A useful middle path the 120 Hz LTPO panels enable: interpolate to 48 or 72 fps and let the display show each frame 2–3× — much of the perceptual smoothness gain at a fraction of the compute.

---

## 5. Recommended Architecture

### Android (S24 Ultra)

**Foundation: Media3/ExoPlayer with `setVideoEffects()`.** It is the only framework that hands you decoded frames as GL textures with a *documented* N-in/M-out shader contract (`GlShaderProgram`: "implementations can choose to… process several input frames before producing an output frame," each output with its own `presentationTimeUs`). No libvlc fork, no custom mpv+VapourSynth toolchain. One caveat to spike first: setVideoEffects is only demoed 1:1; a frame-rate-increasing program exercises `videoFrameReleaseControl` in undocumented ways.

**Frame flow:**
```
MediaCodec (async, +1-2 frame lookahead queue)
  → SurfaceTexture / external GL texture (zero-copy)
  → [scene-cut gate: codec I-frame/residual hints + histogram SAD → duplicate on cut]
  → interpolation engine (ladder below), timestamps retimed to target cadence
  → GL output → Surface with setFrameRate(120 or 60, FIXED_SOURCE, CHANGE_ALWAYS)
  → audio clock offset for the lookahead latency
```

**Engine ladder (quality → fallback):**
1. **NPU hybrid (quality mode, →60):** ANVIL-style — extract codec motion vectors (MediaCodec doesn't expose them; either a lightweight parallel bitstream parse or a custom software MV-extraction path — this is a spike), pre-align on GPU, INT8 residual refinement on Hexagon via QNN. 12.8 ms/1080p-frame class. Never RIFE-as-is: refuted.
2. **GPU MEMC (default, →60/120):** block matching using `GL_QCOM_motion_estimation` for hardware MV search + MC warp with occlusion masks; optionally `GL_QCOM_frame_extrapolation` for the cheapest boost frames. Vendor-locked to Adreno — keep a portable compute-shader MEMC behind the same interface for non-Qualcomm devices.
3. **CPU MEMC (compat):** SVPflow-style — proven on SD865-class.
4. **Blend floor:** mpv-style temporal blending / frame duplication — artifact-free judder reduction when thermals or content defeat the above; also the automatic scene-cut and throttle response.

**Display grant:** request via `Surface.setFrameRate()`; render as an ordinary GPU surface (Samsung's LTPO content-matching will down-clock anything that presents like video); verify the app is not game-classified; detect the granted mode via `DisplayManager` callbacks and re-target the interpolator (never interpolate to 120 while the panel holds 60).

### iOS (iPhone 17)

**Foundation: AVFoundation custom pipeline** — `AVPlayer` + `AVPlayerItemVideoOutput.copyPixelBufferForItemTime` → interpolation → `CAMetalLayer` with CADisplayLink pacing (the documented-safe route; AVSampleBufferDisplayLayer's 120fps behavior is unverified). libmpv/MPVKit (LGPL, App Store-viable) is the fallback if broad container/codec support outweighs pipeline simplicity.

**Engine ladder — VTFrameProcessor changes the answer here:**
1. **`VTLowLatencyFrameInterpolationConfiguration`** (iOS 26): Apple's own ANE-backed real-time interpolator, N uniform frames per pair. If the spike shows it sustains 1080p at target rates, it replaces the entire in-house engine on iOS — free implementation, free ANE access (which has no public API otherwise), Apple-maintained quality. Gate on `isSupported` + empirical benchmarks; early reports show resolution-dependent failures and crashes, so treat as unproven until measured on an actual iPhone 17.
2. **Metal compute MEMC** (SVPflow-style; A13 sufficed, A19 has huge headroom) as the in-house fallback.
3. **Blend floor**, as on Android.

Do **not** plan around ANE-deployed custom NN VFI (no public ANE API; grid_sample-class ops fall back to GPU/CPU) or MetalFX interpolation (needs engine motion vectors/depth).

**Display:** ship `CADisableMinimumFrameDurationOnPhone` + `preferredFrameRateRange`, but architect pacing to degrade gracefully to whatever CADisplayLink actually delivers (120/90/80/60) — retime interpolation targets to the granted rate each callback. Default product target: 60.

### Both platforms
Per-content-type interpolation defaults (sports/anime on, film off — Filmmaker-Mode lesson), instant toggle, strength slider, IVTC for telecined sources, thermal-state listeners that step down the ladder before the OS does it for you, and honest battery messaging.

---

## 6. Key Risks & Unknowns (ranked), Each With a Cheap Spike

1. **iOS 120 Hz grant under interpolation load (highest risk — can kill iOS-120 entirely).** *Spike:* trivial Metal app on iPhone 17: plist key + frame-rate range, render 120 unique frames/s while burning ~3 W of fake GPU/ANE load; log CADisplayLink actual rates over 30 min, in/out of Low Power Mode. Days of work; decisively prices the iOS ceiling.
2. **VTFrameProcessor real-time performance (could delete most iOS engineering).** *Spike:* benchmark `VTLowLatencyFrameInterpolation` and FRC configs on an iPhone 17 at 1080p/4K: ms/frame, sustained 30-min run, thermals, resolution failure envelope (reports of 640×480 failures suggest odd boundaries). No public numbers exist; you'd be first.
3. **Media3 frame-rate-increasing GlShaderProgram in the playback path (Android foundation bet).** *Spike:* GlShaderProgram that naively emits each input frame twice with retimed PTS via `setVideoEffects`; verify videoFrameReleaseControl and A/V sync at 60→120 on the S24 Ultra. Documented contract allows it; playback path is undemoed.
4. **S24 Ultra 120 Hz for a decoding app (Samsung LTPO content-matching).** *Spike:* render decoded video via GL to a surface submitting 120 buffers/s with `setFrameRate(120)`; verify panel state (HWC dumpsys / high-speed camera) during real decode. No published proof any third-party player has held 120 Hz here.
5. **Codec motion-vector extraction on Android (gates the NPU quality mode).** MediaCodec doesn't expose MVs. *Spike:* lightweight H.264/HEVC bitstream MV parser running parallel to MediaCodec on 1080p streams; measure CPU cost and MV usability (ANVIL proves the concept but its extraction path may not transfer; check what their open-source player does).
6. **Sustained thermals of the full pipeline (not micro-benchmarks).** *Spike:* 2-hour continuous run of decode+MEMC+120Hz on both devices; log clocks, skin temp, battery, dropped frames. The S24 Ultra's 48% stress stability says minute-10 performance ≠ minute-60.
7. **QCOM extension viability on the S24 Ultra specifically.** *Spike:* confirm `GL_QCOM_motion_estimation`/`frame_extrapolation` are exposed on the Galaxy's driver build (OEM builds sometimes strip extensions); measure MV search ms at 1080p and extrapolation artifact quality on real video (they're designed for game buffers).
8. **MEMC quality bar vs. SVPlayer (product risk).** If output artifacts match a $12 incumbent, there's no product. *Spike:* golden-clip suite (pans over detail, occlusions, subtitles over motion, scene cuts, credits) through SVPlayer, ffmpeg minterpolate (offline reference), and your prototype; blind A/B.
9. **Hexagon INT8 quality/latency for the residual net (ANVIL replication).** ANVIL reports INT8 quality collapse for standard VFI nets; their residual design avoids it — replication on your stack unverified. *Spike:* port a small residual-refinement net via QNN; measure ms and PSNR vs FP16.
10. **ANVIL specifics (low confidence in details).** The paper's abstract-level numbers were verified, but full text was unfetchable in research; codec coverage (H.264 vs HEVC/AV1 — most 1080p streaming is HEVC/AV1, whose MV extraction differs), player code license, B-frame MV semantics. *Spike:* obtain and read the paper + repo before committing the quality-mode architecture to it.

---

### Bottom line

Ship **1080p→60** as the promise on both platforms (MEMC default, NPU/VTFrameProcessor quality modes), **1080p→120 as an Android-only, best-effort, MEMC-based boost** with explicit battery/thermal framing, and **decline 4K interpolation** on this hardware generation (interpolate-at-1080p-then-upscale if 4K sources must be supported). Every path to more than that runs through either silicon that these phones don't have, OS policy that won't cooperate (iOS 120 Hz), or arithmetic that doesn't close (NN at 1080p/120 on 77 GB/s of bandwidth).