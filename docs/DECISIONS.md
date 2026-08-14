# Architecture Decision Record

Running log of decisions made during implementation, especially spike-gated ones. Newest first.

---

## Pending: Architecture A vs B (Spike A)

**Status:** not yet decided — awaiting Phase 1, Spike A on-device results.

**Question:** can `ExoPlayer.setVideoEffects()` with a custom `GlShaderProgram` actually emit more output frames than input frames during *live playback* (not just offline export), at target rates up to 120fps?

- **Architecture A (preferred if it works):** interpolation lives entirely inside the Media3 effect pipeline as a `GlShaderProgram`. Simpler, reuses ExoPlayer's playback control, A/V sync, and lifecycle handling.
- **Architecture B (fallback):** ExoPlayer decodes to our own `SurfaceTexture`; we run our own Choreographer-paced EGL render loop to interpolate and present frames, slaved to `player.currentPosition`. More code to own (our own pacing and A/V sync), but removes dependence on undocumented behavior in Media3's internal 10ms work loop and known effect-pipeline bugs (androidx/media #1139, #1166).

Verdict will be recorded here after Phase 1.
