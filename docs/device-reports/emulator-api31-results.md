# Emulator Run — Android API 31

**This does not close Article 9.** Article 9 requires the Galaxy S24 Ultra. What follows ran on an
emulator with a software GPU and a 60 Hz virtual panel, so it can say nothing at all about the two
questions Phase 0 exists to answer: whether Samsung's LTPO panel grants 120 Hz to a decoding app,
and what sustained interpolation does to a real thermal envelope. `docs/device-reports/phase-0-results.md`
is still missing and Phase 0 is still not done.

What it *does* establish is narrower and was previously worth nothing: **the app runs.** Before this,
every claim about Phase 0 rested on a green CI run, which Article 1 is explicit about not counting.
Each line below was observed on screen or in `logcat`/`dumpsys`, not inferred from the source.

**Evidence tier:** `measured`, on an emulator — never to be quoted as a device result.

---

## Environment

| | |
|---|---|
| Emulator | `sdk_gphone64_arm64`, Android 12 / API 31, `google_apis` arm64-v8a |
| Host | macOS 26.5 (arm64), `-gpu swiftshader_indirect` (software rendering) |
| Screen | 1080x2340, 440dpi, **60 Hz**, `panel max` reported 60 Hz |
| Decoder | `c2.goldfish.h264.decoder` (software) |
| Build | debug APK, commit at time of run, AGP 8.13 / Kotlin 2.3.21 / Media3 1.11.0 |
| Clip | synthetic H.264, 1280x720, **exactly 24fps**, 45s, generated with AVFoundation |

The clip is synthetic on purpose: it has a known-exact frame rate, a hard-edged object crossing the
frame, a 16:9 border that makes any aspect-ratio error obvious, and a strip that alternates every
frame.

---

## What the HUD read, playing the 24fps clip

```
render      7.8–12.5 fps      (red)
granted     60 Hz             (amber — downgraded)
requested   120 Hz
dropped     200–450           (red)

source      24 fps
size        1280x720
cadence     24 → 60   2.50x
generate    36.0 fps
budget      16.67 ms/frame
panel max   60 Hz
thermal     0.43  none
battery     900 mA in  100%
```

**`render` and `dropped` are an artefact of the environment, not a finding.** A software H.264
decoder on a virtualised GPU cannot sustain 24fps at 720p; it rendered roughly a third of the frames
and dropped the rest. Nothing about that number transfers to hardware. It is recorded because it
exercised the failure path, and because it is what caught the frame-rate meter blanking instead of
reporting a low rate.

**`granted` 60 against `requested` 120 is the interesting one, for a boring reason.** The virtual
panel has no 120 Hz mode, so the request could not be honoured — and the app read that back,
displayed both numbers separately, flagged the downgrade in amber, and computed the cadence from the
60 it actually got rather than the 120 it asked for. That is Article 5's required behaviour executing
end to end for the first time. It says nothing about what an S24 Ultra will do.

`cadence`, `generate`, and `budget` are computed by `:interp-core` from the two *observed* rates. The
36.0 fps and 16.67 ms shown on the phone are the same values `CadenceTest` asserts in CI.

---

## Behaviour checks

| Check | Result |
|---|---|
| Installs, launches, no crash | pass |
| Full-screen, no status or navigation bar | pass |
| Start screen, file picker opens | pass |
| Plays a video with correct 16:9 letterboxing on a 19.5:9 screen | pass |
| Scrubber, ±10s, play/pause, replay-at-end | pass |
| Controls auto-hide during playback, return on tap | pass |
| HUD expands and collapses, fits without colliding with the transport | pass |
| Opened from another app via `ACTION_VIEW` | pass |
| Second `ACTION_VIEW` reuses the one activity (`singleTask` + `onNewIntent`) | pass — one `ActivityRecord` in the task |
| Audio focus requested | pass — `MediaFocusControl: requestAudioFocus() ... USAGE_MEDIA/CONTENT_TYPE_MOVIE callingPack=dev.fps.app` |
| Unplayable file shows a readable error, not a black screen | pass — "That file is not a video this player can parse." |
| Persistable URI permission taken | pass — `dumpsys activity permissions` shows `persistable=0x3 persisted=0x1` for `dev.fps.app` |
| Survives process death | pass — `am kill` confirmed the process gone; relaunch restored the file, reopened it, and held at 0:00 paused |
| Thermal and battery readings are real numbers | pass — 0.43 / `none`, 900 mA / 100% |

## Not verified here

- Anything about 120 Hz. The virtual panel tops out at 60.
- A/V sync — the test clip has no audio track.
- Thermal behaviour under load. An emulator's 0.43 headroom is not a phone's.
- Sustained playback (the longest clip was 45s).
- Any real hardware decoder path.
- `Surface.setFrameRate` being *granted*, as opposed to being called and read back.

---

## Defects this run found

Four, none of which were visible from reading the code:

1. **The frame-rate meter blanked instead of reporting a low rate.** It suppressed the reading
   whenever no frame had arrived in the last 400ms, so a device rendering 7.8fps showed `--`. That is
   backwards: struggling is exactly the condition the HUD exists to surface. Now a paused player
   reports a true zero and a playing one reports whatever the window measured.

2. **Video flashed stretched before the letterbox applied.** The decoder's first frames can land
   before `onVideoSizeChanged` does, and the unknown-size branch fills the window. The surface is now
   transparent until a size is known.

3. **The expanded HUD ran off the bottom of the screen** and hid behind the scrubber. Rebuilt as two
   balanced columns sized to the ~207dp of usable landscape height.

4. **`launchMode` was `standard`,** so every file opened via "Open with" stacked another activity,
   each constructing its own `ExoPlayer` and holding its own decoder. `onNewIntent` was written but
   unreachable. Now `singleTask`.

---

## Verdict

Emulator acceptance: **PASS**, for the narrow claim that the app runs, plays, and instruments itself
correctly against whatever the system gives it.

Phase 0 acceptance: **still open.** It needs the S24 Ultra and
[`phase-0-checklist.md`](phase-0-checklist.md).
