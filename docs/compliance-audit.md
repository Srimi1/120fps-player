# Constitution Compliance Audit

**Date:** 2026-08-15
**Commit audited:** `cd98d0e` (branch `claude/120fps-realtime-player-25ie4s`, PR #1 draft)
**Project state:** Phase 0 builds and packages in CI; 15 unit tests pass in CI; Phase 1 spikes not started; **nothing yet run on hardware**

Re-run this audit at every phase boundary. Statuses: **PASS** / **OPEN** (violated, unresolved) / **N/A** (article governs code that does not exist yet) / **FLAG** (needs a human decision).

---

## Update — Phase 0 hardening pass

**Scope:** bug fixes, a rebuilt player UI, and the first execution of the app on an Android runtime.
This is not a phase boundary, so the audit below is left intact as the record of its own commit; this
section records what moved.

| # | Article | Was | Now |
|---|---|---|---|
| 1 | Evidence tiers | PASS (nothing `measured` beyond unit tests) | **PASS** — first `measured`-on-a-runtime facts exist, and are labelled emulator-only |
| 4 | 60 promised, 120 best-effort | FLAG (product name) | **FLAG, unchanged** — but the UI now states the caveat itself |
| 5 | OS grants the rate | PASS (infrastructure shaped correctly) | **PASS, now exercised** — request and grant observed diverging, and the cadence followed the grant |
| 8 | Power cost disclosed | N/A | **N/A, groundwork laid** — battery current, charge level, thermal headroom and thermal status are all on the HUD |
| 9 | Runs on the phone | **OPEN** | **OPEN, unchanged** — an emulator is not the S24 Ultra |
| 10 | Dependency floors verified | PASS | **PASS** — one dependency added, floor checked first |

### Article 1 — the tier vocabulary now has something in it

Before this pass, every claim in the repo was `cited` or `assumed`, and the only `measured` facts were
unit-test results about arithmetic. The emulator run produces the first `measured` facts about the
*application*: it launches, plays, letterboxes, seeks, recovers from process death, and reports
plausible instrument values.

These are filed as [`emulator-api31-results.md`](device-reports/emulator-api31-results.md), which
opens by stating what it cannot establish. That framing is deliberate: an emulator report sitting in
`device-reports/` is exactly the kind of artifact a future reader could mistake for Article 9 being
satisfied.

### Article 4 — the UI now carries the caveat the name does not

The naming tension is unresolved and still needs the user's decision. What changed is that the app no
longer relies on documentation to carry the honesty:

- The start screen reads *"Opens a local video, asks the display for 120 Hz, and reports what the
  system actually granted"* — a description of behaviour, not a promise of a rate.
- Under it: *"Phase 0 — no frame interpolation is implemented yet."*
- The HUD shows `requested` and `granted` as two separate rows, and colours `granted` amber when it is
  below what was asked.

No user-facing string promises 120fps. The strongest claim any of them makes is that the app *asks*
for it.

### Article 5 — exercised, not just shaped

Previously this passed on inspection: the controller requested 120 and read the granted rate back, but
no code consumed the granted value beyond displaying it, so nothing could have violated the article
because nothing computed a cadence at all.

Now `:app` depends on `:interp-core`, and the HUD computes the cadence from the granted rate. On the
emulator, which has no 120 Hz mode, the app asked for 120, was given 60, and reported
`cadence 24 → 60 2.50x` — following the grant rather than the request, observed rather than argued.

`DisplayRate.snap()` was added so this stays honest against real readings: an unrecognised rate returns
null and the HUD shows `--` rather than snapping to a nearby guess.

### Article 9 — still open, and the emulator does not narrow it

The emulator's virtual panel is 60 Hz, so `Surface.setFrameRate(120)` could not have been granted
regardless of the app's behaviour, and its software decoder dropped most frames. **Neither of the two
questions Phase 0 exists to answer has been touched.** `docs/device-reports/phase-0-results.md` is
still absent.

The checklist has been rewritten to match the current UI — the HUD row it previously called `display`
is now two rows, `granted` and `requested`, and the gestures changed — so a report filed against the
old checklist would describe a UI that no longer exists.

### Article 10 — one dependency added, floor checked first

`androidx.lifecycle:lifecycle-runtime-compose` was needed for `LocalLifecycleOwner`. Its floor was
checked before adding: 2.11.0 requires compileSdk 37 and is one of the three libraries named in the
AGP 8.13 ADR as having broken `checkDebugAarMetadata` before. It is pinned to **2.10.0**, sharing a
version reference with `lifecycle-runtime-ktx` so the two cannot drift apart.

`kotlin("test")` was added to `:app` for unit tests. It carries no version — it resolves against the
Kotlin already on the classpath — so it cannot introduce a floor.

### Appendix — what running the code found that reading it did not

The original audit's appendix argued that careful review substitutes for a compiler. This pass tested
that claim, and the result qualifies it: review caught real defects, and running the app caught four
more that review had not, each of which is a timing or layout property that is not visible in source.

1. **The frame-rate meter blanked instead of reporting a struggling device** — the HUD's central
   number failing in exactly the condition it exists to report.
2. **Video flashed stretched** before the decoder reported a size.
3. **The expanded HUD ran off-screen** behind the scrubber.
4. **`launchMode` was `standard`**, so each "Open with" stacked another activity and another decoder.

None of these would have been caught by a green CI run, which is Article 9's whole point restated from
the other direction: it is not that CI is inadequate evidence for *completion*, it is that it is
inadequate evidence for *anything about runtime behaviour*.

Also fixed in this pass, found by review rather than execution: playback continued in the background
with audio; the screen slept during a film; no audio focus was requested and headphone unplug was
ignored; playback failures showed a black screen with no message; the dropped-frame counter carried
across files; `getIntProperty` returning `Integer.MIN_VALUE` for an unsupported fuel gauge rendered as
`-2147483 mA`; thermal headroom defaulted to `0.00`, indistinguishable from a genuine reading of zero;
and there was no way to open a second video without force-stopping the app.

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

---

## Appendix — findings from reviewing never-executed code

Article 10 says CI is the compiler and code must be written to be right the first time. A deliberate review pass over the Phase 0 sources — none of which had ever been compiled or run when written — found three defects, which is the evidence for why that article matters:

1. **Plugin resolution (build-breaking).** `:interp-core` requested `org.jetbrains.kotlin.jvm` with a version, but the jvm and android Kotlin plugins ship in one jar; the plugin was already on the classpath with an unresolvable version and plugin resolution failed outright. Fixed by declaring it once at the root with `apply false`, matching the other four plugins.

2. **Data race in the measurement instrument.** `PlaybackStatsCollector.totalDropped` was written from the player's application looper and read from the playback thread with no synchronisation, so the HUD could report a stale dropped-frame count. Since the HUD *is* the instrument every Phase 0 and Phase 1 measurement depends on, an unreliable reading would corrupt the data the whole plan is steered by. Now `@Volatile`.

3. **No pause control.** The device checklist asks for a five-minute stability run while reading HUD values, which is impractical when the picture cannot be held. Tap-to-toggle added.

Also checked and found *not* to be a problem: `PlayerEngine` exposes `@UnstableApi` media3 types without an opt-in annotation, which was expected to trip lint's error-severity `UnsafeOptInUsageError`. `./gradlew lint` passes, so no change was made — recorded here so the question is not re-litigated later.

## Actions arising

1. **[User, blocking Phase 0]** Sideload the APK and file `docs/device-reports/phase-0-results.md` → closes the Article 9 violation.
2. **[User decision]** Resolve the Article 4 naming tension — keep `120fps Player` as branding, or rename.
3. **[Next audit]** Re-run at the end of Phase 1 (spike results) and Phase 2 (first engine), when Articles 6–8 become applicable.
