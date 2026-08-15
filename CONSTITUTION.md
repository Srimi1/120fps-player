# Project Constitution

The non-negotiable rules for this project. They exist because the feasibility research behind this player was adversarial and produced several counter-intuitive constraints that are easy to violate with good intentions — someone will eventually want to "just add an AI mode" or ship a 120fps badge on iPhone, and both would quietly undo work that took real evidence to establish.

Every article states the rule, why it exists, and a **Check** that makes it auditable. See [`docs/compliance-audit.md`](docs/compliance-audit.md) for the current standing.

---

## Part I — Truth

### Article 1. Claims carry their evidence tier

Every performance or capability claim in code comments, docs, commits, or the PR must be attributable to one of three tiers:

| Tier | Meaning |
|---|---|
| `measured` | We ran it on the target device and recorded numbers in `docs/device-reports/`. |
| `cited` | Someone else measured it; the source is named. |
| `assumed` | Neither. A working hypothesis, and labeled as one. |

The word **"verified" is reserved for `measured`.** A green CI run means the code compiles and packages — nothing more. It is not evidence that anything works.

**Check:** no doc claims a runtime behavior works without either a `docs/device-reports/` entry or a named external source.

### Article 2. Decisions that could be reversed get written down

Any decision that is spike-gated, load-bearing, or that a future contributor might reasonably undo goes in [`docs/DECISIONS.md`](docs/DECISIONS.md) with its reasoning and what would change it.

**Check:** every "we chose X over Y" in the codebase traces to an ADR.

---

## Part II — Physics

### Article 3. The do-not-build list is binding

These are closed by evidence, not preference. Each names what it would take to reopen it.

| Do not build | Why | Reopens if |
|---|---|---|
| Neural/RIFE frame interpolation | Measured ~4 fps at 1080p on Adreno 750 (~250 ms/frame against a 10–28 ms budget); `grid_sample` is NPU-hostile; 77 GB/s memory bandwidth doesn't close the gap | A measured on-device run of a specific network hits the frame budget at 1080p |
| 4K real-time interpolation | Roughly 8× the compute of the hardest demonstrated case, on the same memory system | New silicon, or a fundamentally cheaper algorithm with measurements |
| A VLC fork | Android MediaCodec opaque direct rendering bypasses the filter chain; filtering forces a CPU copy-back of every frame and a libvlc fork | VLC exposes a GPU-side filter hook for opaque frames |
| mpv-android as the base | No VapourSynth support, not an importable AAR | Upstream ships both |

Note that mpv's `--interpolation` is frame *blending*, not motion interpolation — per the mpv wiki, "no motion-based prediction whatsoever." Do not cite it as prior art for MEMC.

**Check:** no dependency, module, or engine tier in the repo implements anything on this list.

### Article 4. 60 is the promise; 120 is best-effort and Android-only

1080p→60 is the dependable, marketed feature. 1080p→120 ships only on Android, only via classical MEMC, and only ever labeled best-effort — because no verified sustained 1080p→120 benchmark on this SoC exists, and because it costs game-class power.

**iOS is a 60 target.** iOS treats a high-refresh request as a revocable hint and revokes it under exactly the thermal load sustained interpolation creates. Never state or imply a 120fps guarantee on iPhone, in the UI, the store listing, or the README.

**Check:** no user-facing string, doc, or marketing copy promises 120fps unconditionally, and none promises it at all on iOS.

### Article 5. The OS grants the refresh rate; we adapt to it

`Surface.setFrameRate()` is a request. Samsung's LTPO panels content-match "video-looking" surfaces down to the source rate, and the system revokes high rates under thermal pressure. The app must always read back the granted mode (`DisplayManager`) and retime the interpolator to what it actually got.

A target rate may be *requested* as a constant. It may never be *assumed* as the output rate.

**Check:** no code path computes output cadence from a hardcoded rate rather than from the observed granted rate.

---

## Part III — Quality

### Article 6. There is always an artifact-free floor, and we fall into it

Blending/duplication is the permanent floor beneath every engine tier. The pipeline degrades into it — never into garbage — on scene cuts (interpolating across a cut produces morph frames), low motion-vector confidence, and thermal pressure. Thermal step-down is proactive, before the OS throttles for us.

**Check:** every engine tier has a defined fallback, and the scene-cut gate is unconditional.

### Article 7. The viewer can always turn it off

Frame interpolation is a matter of taste — the industry invented Filmmaker Mode specifically to disable it. An instant toggle, a strength control, and sensible per-content defaults are requirements, not enhancements.

**Check:** interpolation is switchable from the player UI without entering a settings screen.

### Article 8. Power cost is disclosed, never hidden

Sustained interpolation draws game-class power (~4–6 W vs ~0.5–1 W for plain playback) and will visibly drain a battery over a feature-length film. The UI tells the truth about this rather than letting users discover it.

**Check:** enabling a heavy mode surfaces its battery/thermal cost.

---

## Part IV — Process

### Article 9. A phase is done when it runs on the phone — not when CI is green

Every phase ships an installable APK **and** a filed report in `docs/device-reports/`. Development happens in a cloud container with no Android device attached; the only way any of this becomes real is the user sideloading the build and reporting back. CI green is a necessary precondition, never the finish line.

**Check:** each completed phase has a corresponding `docs/device-reports/phase-N-results.md`.

### Article 10. CI is the compiler; dependency floors are verified, not guessed

This container cannot reach `dl.google.com` (blocked by network egress policy), so no Android build can be compiled or verified locally — GitHub Actions is the only compiler. Two consequences:

- Changes are written to be right the first time, checked against real documentation rather than recollection.
- While on AGP 8.13 / compileSdk 36, **every** androidx version bump is checked against that library's own release notes for a `compileSdk`/AGP floor. Individual libraries carry their own AAR-metadata requirements independent of the Compose BOM — this is not hypothetical, it broke the build once already (`core-ktx` 1.19.0, `lifecycle` 2.11.0 silently require compileSdk 37).

**Check:** no dependency is bumped without confirming its documented compileSdk/AGP floor.

### Article 11. Undocumented territory gets a spike and a named fallback

Where the platform is undemoed — chiefly whether Media3's effect pipeline can emit more frames than it consumes during *live playback* — build the cheap experiment first and name the fallback architecture before the unknown becomes load-bearing. No plan may depend on an unverified platform behavior with no escape hatch.

**Check:** every dependency on undocumented behavior has a spike and a written fallback in `DECISIONS.md`.

---

## Amendment

An article changes only when **evidence** contradicts it — a measurement, not an opinion or an inconvenience. The contradicting measurement is recorded in `docs/DECISIONS.md` alongside the amendment. Absent that, the article stands even when it is annoying; that is the entire point of writing it down.
