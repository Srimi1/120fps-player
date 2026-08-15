# Architecture Decision Record

Running log of decisions made during implementation, especially spike-gated ones. Newest first.

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
