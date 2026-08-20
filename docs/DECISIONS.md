# Architecture Decision Record

Running log of decisions made during implementation, especially spike-gated ones. Newest first.

---

## Decided: an emulator smoke run gates the APK, but does not close Article 9

**Status:** decided, Phase 0.

Everything about Phase 0 rested on a green CI run, which Article 1 says is worth nothing as evidence
that anything works. The gap between "packages" and "runs" was closed cheaply by running the debug
APK on a local Android 12 / API 31 emulator with a synthetic 24fps clip, and it immediately paid for
itself: four defects surfaced that were invisible in review (see
[`device-reports/emulator-api31-results.md`](device-reports/emulator-api31-results.md)).

**The emulator is explicitly not a substitute for the device.** Its panel is 60 Hz and its decoder is
software, so it cannot speak to either question Phase 0 actually exists to answer — whether Samsung's
LTPO panel grants 120 Hz to a decoding app, and what the thermal envelope does under load. Article 9
stays open until `phase-0-results.md` is filed from the S24 Ultra.

**What would change this:** nothing. The emulator run is a floor, not a ceiling. If a future
contributor is tempted to treat a green emulator run as phase completion, that is the exact
substitution Article 9 forbids.

---

## Decided: the frame-rate meter is sampled by the reader, not pushed by the renderer

**Status:** decided, Phase 0. Reversible, but do not reverse it without reading this.

The obvious implementation updates the HUD from inside `onVideoFrameAboutToBeRendered` — the callback
already fires once per frame, so counting there is free. It is also wrong in the two states the HUD
matters most:

- **Paused or stalled**, the callback stops firing, so the last value pushed stands forever. The HUD
  reports a confident 24fps over a frozen picture.
- **Struggling**, frames arrive sparsely. An early fix suppressed the reading when no frame had
  arrived recently, which turned a device rendering 7.8fps into a blank `--`. The emulator run caught
  this: a device that cannot keep up is precisely the finding, and blanking it hides the finding.

`PlaybackStatsCollector` now only increments counters on the playback thread, and a 200ms ticker on
the UI side computes the rate over a rolling one-second window, taking `isPlaying` so a stopped player
reports a true zero rather than a decaying one.

**What would change this:** a Media3 API that reports render rate directly and correctly through
pause and stall.

---

## Decided: `:app` depends on `:interp-core`, and the HUD reports the cadence for the *granted* rate

**Status:** decided, Phase 0.

`CadencePlanner` and its 15 tests existed but nothing on the phone used them, so the arithmetic the
whole project turns on was verified only in CI. The HUD now computes the cadence live from two
observed values — the frame rate the decoder reports for the file, and the refresh rate the OS
granted — and shows the ratio, the frames per second an interpolator would have to synthesise, and
the per-frame budget.

Two constraints shaped it:

- **Article 5.** The cadence is computed from the *granted* rate, never from `TARGET_REFRESH_HZ`. On
  the emulator that meant it correctly read `24 → 60  2.50x` while the app was still asking for 120.
- **Articles 1 and 4.** Nothing about this is a measurement of interpolation, because there is no
  interpolation. The panel says so on its own header line: `cadence = plan, not measurement`.

`DisplayRate.snap()` was needed to make it honest: panels report 120.00001 and containers report
23.976025, and feeding those floats into the cadence maths reintroduces the drift `Rational` exists to
prevent. An unrecognised rate returns null and the HUD shows `--` rather than snapping to a guess —
a wrong belief about the granted rate is worse than admitting ignorance.

**What would change this:** nothing foreseeable; if the engine later needs a cadence the planner
cannot express, that is a `CadencePlanner` change, not a wiring change.

---

## Decided: `launchMode="singleTask"` for MainActivity

**Status:** decided, Phase 0.

Adding an `ACTION_VIEW` intent filter (so the player shows up in "Open with") exposed a problem under
the default `standard` launch mode: every file handed over starts *another* `MainActivity`, each
constructing its own `ExoPlayer` and holding its own hardware decoder. Two decoders alive for one
visible player is a real resource bug on a device with a limited codec pool, and the `onNewIntent`
handler written to take subsequent files was unreachable.

**What would change this:** wanting genuine multi-window/multi-instance playback, which would need a
different design anyway (one player per instance, and the display-mode controller made per-window).

---

## Decided: `lifecycle-runtime-compose` pinned to 2.10.0

**Status:** decided, Phase 0.

`LocalLifecycleOwner` moved out of `androidx.compose.ui.platform` and into
`androidx.lifecycle.compose`, so pausing playback when the app leaves the screen needs this artifact.

Per Article 10 the floor was checked before adding it rather than after: **2.11.0 requires
compileSdk 37** — it is named in the AGP 8.13 entry below as one of the three libraries that broke
`:app:checkDebugAarMetadata` once already. **2.10.0** is the same release train as the
`lifecycle-runtime-ktx` already pinned here and is documented against API 36, so it shares a version
reference with it in `libs.versions.toml` and cannot drift independently.

**What would change this:** moving to compileSdk 37 / AGP 9, at which point both move together.

---

## Decided: stayed on AGP 8.13 instead of AGP 9.x

**Status:** decided, Phase 0.

By the time Phase 0 was implemented, AGP 9.2.0 was the actual current stable Android Gradle Plugin release — but AGP 9 is a major breaking change: it switches to new mandatory DSL interfaces and, most significantly, enables **built-in Kotlin support by default**, replacing the traditional separate `org.jetbrains.kotlin.android` plugin model this codebase (and most existing Android documentation/examples) assumes.

This cloud development environment cannot reach `dl.google.com` (only `maven.google.com`'s metadata endpoints, which redirect there and get blocked), so **no Android build can be compiled or verified locally here** — only GitHub Actions CI, which runs outside this network policy, can actually build the app. Given zero local compile feedback, adopting AGP 9's new paradigm shift on the first try was too risky to get right blind.

**Decision:** pin to AGP **8.13.0** (last stable 8.x release, explicitly documents "Kotlin 2.3 support"), Kotlin **2.3.21** (latest 2.3.x patch, matches AGP's stated compatibility — true latest stable Kotlin was 2.4.10, but that combination with AGP 8.13 is unverified), Compose BOM **2026.01.00** (predates the 2026.08.00 BOM's requirement of AGP 9.1.1+), Media3 **1.11.0**. Revisit the AGP 9 migration once the app has a working baseline that's been verified on-device, so any migration issues surface against a known-good comparison point rather than compounding with first-build risk.

**Lesson learned (from an actual CI failure, not foreseeable by reading docs alone):** individual androidx libraries carry their own `minCompileSdk` AAR-metadata requirement independent of the Compose BOM — picking each library's own latest version bumped `core-ktx` to 1.19.0, `lifecycle-runtime-ktx` to 2.11.0, and transitively `lifecycle-runtime-compose` to 2.11.0, all of which silently require compileSdk 37 / AGP 9.1+, breaking `:app:checkDebugAarMetadata` even though the Compose BOM itself was fine. Fixed by pinning each to its last version explicitly documented as "compiled against API 36": `core-ktx` **1.17.0**, `lifecycle-runtime-ktx` **2.10.0**, `activity-compose` **1.11.0**. When bumping any androidx dependency in this project while still on AGP 8.13/compileSdk 36, check that library's own release notes for a compileSdk/AGP floor bump, not just whether the version number exists.

---

## Pending: Architecture A vs B (Spike A)

**Status:** not yet decided — awaiting Phase 1, Spike A on-device results.

**Question:** can `ExoPlayer.setVideoEffects()` with a custom `GlShaderProgram` actually emit more output frames than input frames during *live playback* (not just offline export), at target rates up to 120fps?

- **Architecture A (preferred if it works):** interpolation lives entirely inside the Media3 effect pipeline as a `GlShaderProgram`. Simpler, reuses ExoPlayer's playback control, A/V sync, and lifecycle handling.
- **Architecture B (fallback):** ExoPlayer decodes to our own `SurfaceTexture`; we run our own Choreographer-paced EGL render loop to interpolate and present frames, slaved to `player.currentPosition`. More code to own (our own pacing and A/V sync), but removes dependence on undocumented behavior in Media3's internal 10ms work loop and known effect-pipeline bugs (androidx/media #1139, #1166).

Verdict will be recorded here after Phase 1.
