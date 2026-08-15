# Constitution Compliance Audit

**Date:** 2026-08-15
**Commit audited:** `c71c907` (branch `claude/120fps-realtime-player-25ie4s`, PR #1 draft)
**Project state:** Phase 0 complete in CI (run #5 green), Phase 1 spikes not started

Re-run this audit at every phase boundary. Statuses: **PASS** / **OPEN** (violated, unresolved) / **N/A** (article governs code that does not exist yet) / **FLAG** (needs a human decision).

---

## Summary

| # | Article | Status |
|---|---|---|
| 1 | Claims carry their evidence tier | PASS (with note) |
| 2 | Reversible decisions are written down | PASS |
| 3 | Do-not-build list is binding | PASS |
| 4 | 60 is the promise; 120 best-effort, Android-only | FLAG |
| 5 | The OS grants the refresh rate; we adapt | PASS |
| 6 | Artifact-free floor, degrade into it | N/A |
| 7 | Viewer can always turn it off | N/A |
| 8 | Power cost disclosed | N/A |
| 9 | A phase is done when it runs on the phone | **OPEN** |
| 10 | CI is the compiler; dependency floors verified | PASS |
| 11 | Undocumented territory: spike + named fallback | PASS |

**One open violation (Article 9) and one flag (Article 4) needing the user's decision.**

---

## Article 1 — Claims carry their evidence tier · PASS (with note)

Every performance claim in `plan.md` and `docs/research/feasibility-report.md` traces to a named source (SVPlayer's shipping mobile MEMC, the ANVIL ASPLOS 2026 measurements on this exact SoC, the Adreno 750 RIFE benchmark, mpv's own wiki). Nothing asserts a measured-by-us result that does not exist. Commit messages were accurate about what CI does and does not prove.

*Note:* the explicit `measured`/`cited`/`assumed` vocabulary is introduced by this constitution and has not been retro-applied to existing docs. Everything currently in the repo is tier `cited` or `assumed`; **nothing is tier `measured`,** because nothing has run on hardware yet.

*Related gap (fixed in this change):* CI ran `./gradlew test` against zero test sources — a check that passes trivially and proves nothing, which is precisely the false-confidence Article 1 exists to prevent. Fixed properly rather than annotated: the `interp-core` module now exists as a plain JVM module with **15 passing tests** covering the cadence/retiming math, and `./gradlew lint` was added for signal on the Android code.

Those tests are the first genuinely `measured` facts in the repo — they were executed, not asserted. They also independently confirm two research claims: 24→120 is a clean 5× (phases 0/.2/.4/.6/.8), and 24→60 is 2.5× requiring arbitrary phases (0/.4/.8/.2/.6), which is why midpoint-only networks could not serve this project even setting performance aside. The drift test quantifies the bug the integer arithmetic prevents: a naive per-frame accumulator drifts **288 ms across a two-hour film**.

Note the tests verify *logic*, not playback. They say nothing about whether the app works on a phone — that remains Article 9's open item.

## Article 2 — Reversible decisions are written down · PASS

`docs/DECISIONS.md` holds two entries: the AGP 8.13 pin (decided, with the reasoning about AGP 9's built-in-Kotlin shift being unverifiable without a local compile loop) and the Architecture A vs B choice (pending, gated on Spike A, with both options and the fallback specified). No undocumented "we chose X over Y" exists in the codebase.

## Article 3 — Do-not-build list is binding · PASS

Verified against the dependency graph and source tree: no neural-inference dependency of any kind (no ncnn, TFLite/LiteRT, QNN, or ONNX), no libvlc, no libmpv, no 4K interpolation path. Dependencies are Media3, Compose, AndroidX, and coroutines only. The engine ladder in `plan.md` correctly places classical MEMC first and confines any NN work to a Phase 5 stretch with its own go/no-go gate.

## Article 4 — 60 is the promise; 120 best-effort, Android-only · FLAG

**Documentation is compliant.** `plan.md`, the research report, and the PR description all consistently frame 60 as dependable, 120 as Android-only best-effort, and explicitly decline to promise 120 on iPhone.

**Flagged tension — the product name itself.** The app's display name is `120fps Player` (`app/src/main/res/values/strings.xml`) and the repo is `120fps-player`. By the letter of Article 4, a name that states "120fps" is an unconditional promise of exactly the thing the research says cannot be guaranteed — and it becomes actively misleading on the eventual iOS build, where 120 is not a target at all.

This is the user's product name and predates the constitution, so it is flagged rather than changed. Options: keep it as aspirational branding (and make sure the UI and store copy carry the honest caveat), or pick a name that does not encode a specific rate. **Decision needed from the user; no code change made.**

## Article 5 — The OS grants the refresh rate; we adapt · PASS

`playback/.../DisplayModeController.kt` requests 120 Hz via `Surface.setFrameRate(..., FRAME_RATE_COMPATIBILITY_FIXED_SOURCE, CHANGE_FRAME_RATE_ALWAYS)` and — critically — reads the granted rate back through a `DisplayManager.DisplayListener` into a `StateFlow`, which the HUD displays live. The `TARGET_REFRESH_HZ = 120f` constant in `PlayerScreen.kt` is used strictly as a *request*, which Article 5 permits; nothing computes output cadence from it.

No interpolator exists yet, so the retiming obligation is untested. The infrastructure to satisfy it is in place and correctly shaped.

## Articles 6, 7, 8 — Quality articles · N/A

No interpolation engine exists (Phase 2+). These govern the blend floor, the viewer's off switch, and power disclosure — all forward-looking. Re-audit at the end of Phase 2 and Phase 3.

## Article 9 — A phase is done when it runs on the phone · **OPEN VIOLATION**

**Phase 0 was reported complete on the strength of a green CI run. It has never run on the Galaxy S24 Ultra.** `docs/device-reports/` contains only `.gitkeep`.

What CI actually established: the project configures, the Kotlin compiles, and an APK packages. What remains entirely unverified: that the app launches, that the file picker opens a video, that playback works with correct A/V sync, that the HUD reports plausible numbers, that `Surface.setFrameRate(120)` is granted or content-matched down by Samsung's LTPO policy, and that thermal/battery readings work.

That last item is not a formality — whether the S24 Ultra grants 120 Hz to a decoding app is one of the top-ranked risks in the research, and no published case of a third-party video player holding it exists.

**To close:** the user sideloads the APK from the CI artifact and files `docs/device-reports/phase-0-results.md`. The checklist and fill-in template are at [`docs/device-reports/phase-0-checklist.md`](device-reports/phase-0-checklist.md). Until then Phase 0 is **not done**, and PR #1 stays draft.

## Article 10 — CI is the compiler; dependency floors verified · PASS

The constraint is documented in `docs/DECISIONS.md`, including the concrete failure that produced it: `core-ktx` 1.19.0 and `lifecycle-runtime-ktx` 2.11.0 (plus transitive `lifecycle-runtime-compose`) each carry an AAR-metadata `minCompileSdk` of 37 / AGP 9.1+ independent of the Compose BOM, which failed `:app:checkDebugAarMetadata`. All three dependencies are now pinned to versions whose release notes explicitly document compilation against API 36. Current state is green.

## Article 11 — Undocumented territory: spike + named fallback · PASS

The one genuinely undocumented dependency — whether a frame-rate-*increasing* `GlShaderProgram` works in Media3's live playback path — is scheduled as Spike A before any engine work, and Architecture B (ExoPlayer decoding to our own `SurfaceTexture` with a Choreographer-paced EGL loop) is written down in `DECISIONS.md` as the escape hatch. Spikes B (120 Hz grant) and C (Qualcomm GL extensions) cover the other two unknowns. No current plan step depends on unverified platform behavior without a fallback.

---

## Actions arising

1. **[User, blocking Phase 0]** Sideload the APK and file `docs/device-reports/phase-0-results.md` → closes the Article 9 violation.
2. **[User decision]** Resolve the Article 4 naming tension — keep `120fps Player` as branding, or rename.
3. **[Next audit]** Re-run at the end of Phase 1 (spike results) and Phase 2 (first engine), when Articles 6–8 become applicable.
