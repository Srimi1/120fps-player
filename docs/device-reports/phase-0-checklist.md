# Phase 0 — On-Device Verification Checklist

**Device:** Galaxy S24 Ultra
**Purpose:** close the Article 9 requirement. Phase 0 is not done until `phase-0-results.md` is filed from this checklist.

Phase 0 is deliberately small: it is the vehicle every later spike rides on. We are not testing interpolation here (none exists yet) — we are establishing the **baseline**, especially what refresh rate the phone is willing to give a video app. That number decides how much of the 120fps plan is reachable.

---

## Getting the APK

A prebuilt debug APK is committed at [`dist/120fps-player-0.1.0-debug.apk`](../../dist/) — download it straight from the repo, transfer it to the phone, and install (allow "install from unknown sources").

CI also builds one on every push, if you would rather have the build for a specific commit: [Actions tab](https://github.com/Srimi1/120fps-player/actions) → the most recent green **Android CI** run → **Artifacts** → `app-debug`.

The APK is debug-signed and installs alongside anything else; it has no release signing.

## Test files to have on the phone

Any local videos will do, but the useful set is:

- **1080p 24fps** — a film clip. The main case: 24 into a 120 Hz panel is the whole reason this project exists.
- **1080p 60fps** — anything shot at 60. Confirms the player handles a high-rate source.
- Ideally a clip with a **steady horizontal pan** (credits, a landscape pan) — pans are where judder is most visible to the eye, and where interpolation will later be most obviously better or worse.

---

## Checks

### 1. Install and launch
- [ ] APK installs without error
- [ ] App launches full-screen (no status or navigation bar) to a dark start screen with an **Open a video** button and a `DIAGNOSTICS` panel in the top-left
- [ ] Note anything odd (crash, permission prompt, rotation weirdness — the app is locked to landscape)

### 2. Playback — 1080p 24fps
- [ ] File picker opens and lists videos
- [ ] Video plays, correct aspect ratio, not stretched or cropped (black bars at the sides of a 16:9 clip on this 19.5:9 panel are correct)
- [ ] **Audio/video sync is correct** — watch someone speaking for ~30s
- [ ] Playback is smooth (some judder on pans is *expected and normal* at 24fps — that's the problem we're here to solve later)
- [ ] **Tap once** to show the controls; they fade again after a few seconds of playback
- [ ] **Double-tap** to pause, double-tap to resume — useful for holding a frame while reading the HUD
- [ ] Scrubber, ±10s buttons, and the play/pause button all work; the time readout matches
- [ ] **Tap the DIAGNOSTICS panel** to expand it to the full readout, tap again to collapse

### 3. Playback — 1080p 60fps
- [ ] Plays correctly with sync

### 4. The HUD — the important part

Collapsed, the panel shows the four rows that matter most. Tap it to expand to the full readout. Record what each reads **during playback** for both clips. A dash (`--`) always means *no reading available*, never zero.

**Collapsed (always visible):**

| HUD row | What it means | What to expect |
|---|---|---|
| `render` | frames actually rendered, over a rolling 1s window | should track the source rate (~24, ~60) |
| `granted` | **the refresh rate the OS actually gave us** | this is the headline number |
| `requested` | what the app asked for | always 120 |
| `dropped` | cumulative dropped frames | should stay at or near 0 |

**Expanded:**

| HUD row | What it means | What to expect |
|---|---|---|
| `source` | frame rate the decoder reports for the file | 24 / 23.976 / 60 — matches the clip |
| `size` | decoded resolution | matches the clip |
| `cadence` | source → granted, and the ratio | e.g. `24 → 120  5.00x`, or `passthrough` |
| `generate` | frames/sec an interpolator would have to synthesise | 96 for 24→120, 36 for 24→60 |
| `budget` | wall-clock per output frame | 8.33 ms at 120, 16.67 ms at 60 |
| `panel max` | highest rate the panel advertises | 120 on the S24 Ultra |
| `thermal` | headroom 0.00–1.00, plus platform thermal status | low is good; `--` means unsupported |
| `battery` | instantaneous current and charge level | `out` is discharging, `in` is charging |

`cadence` and `generate` are **the plan for the rate that was granted, not a measurement of anything running** — no interpolation exists yet. They are shown because the arithmetic is what decides feasibility.

- [ ] `render` is plausible for each clip
- [ ] `dropped` stays low
- [ ] **`granted` — write down the exact value for each clip.** This is the single most valuable number in Phase 0.
- [ ] `source` matches the clip's real frame rate
- [ ] `cadence` reads sensibly for the granted rate (if `granted` is 120 and the clip is 24, expect `24 → 120  5.00x`)
- [ ] `thermal` shows a real number (not `--`)
- [ ] `battery` shows a real number

**Why `granted` matters:** the app requests 120 Hz. Samsung's LTPO panels are documented to content-match "video-looking" surfaces down to the source frame rate — so this may well read 24 or 60 instead of 120 during playback. **Either answer is a valid, useful result.** If it reads 120, the 120fps boost mode has a path. If it content-matches down, we know early that we have to present as an ordinary GPU surface rather than a video surface, and Spike B becomes the priority. Do not treat a low number as a bug — record it.

Also check the phone's own setting: **Settings → Display → Motion smoothness** must be **Adaptive**, not Standard. Standard caps the entire device at 60 Hz and would invalidate the reading.
- [ ] Motion smoothness confirmed set to Adaptive

### 5. Optional but valuable — adb cross-check

If you have adb on a computer (`Settings → Developer options → USB debugging`), the HUD's claim can be confirmed against the system's own view. With a video playing:

```bash
adb shell dumpsys display | grep -iE "mActiveMode|renderFrameRate|refreshRate"
```

```bash
# what SurfaceFlinger is actually doing with our surface
adb shell dumpsys SurfaceFlinger | grep -iA5 "120fps"
```

- [ ] adb-reported active mode matches what the HUD claims

This matters because the HUD reads `display.refreshRate`, which reports the *mode*, and can differ from the render rate SurfaceFlinger is really driving.

### 6. Stability and lifecycle
- [ ] Let a clip play for ~5 minutes — no crash, no drift out of sync, `dropped` not climbing steadily
- [ ] The screen does not dim or sleep while a video is playing
- [ ] Background the app (Home) — **audio stops**; return and playback is where you left it
- [ ] Unplug headphones mid-playback — playback pauses rather than switching to the speaker
- [ ] Open a video from a file manager via **Open with → 120fps Player** — it plays, and does not stack a second copy of the app

### 7. Error handling
- [ ] Open something that is not a playable video (rename a `.txt` to `.mp4`) — a readable "Cannot play this file" card appears instead of a black screen

---

## Filing the results

Copy the template below to `docs/device-reports/phase-0-results.md`, fill it in, and commit. Screenshots of the HUD are welcome — they capture all five values at once.

```markdown
# Phase 0 — Device Report

**Device:** Galaxy S24 Ultra
**Android/One UI version:**
**APK:** CI run # / commit
**Date:**
**Motion smoothness setting:** Adaptive / Standard

## Install & launch
Result:

## 1080p 24fps clip
- File (resolution/fps/codec):
- Plays: yes/no
- A/V sync: ok / off by ~___ms
- HUD collapsed — render: ___ fps | **granted: ___ Hz** | requested: ___ Hz | dropped: ___
- HUD expanded — source: ___ fps | size: ___ | cadence: ___ | generate: ___ fps | budget: ___ ms | panel max: ___ Hz | thermal: ___ | battery: ___
- Subjective smoothness on pans:

## 1080p 60fps clip
- File:
- Plays: yes/no
- A/V sync:
- HUD collapsed — render: ___ fps | **granted: ___ Hz** | requested: ___ Hz | dropped: ___
- HUD expanded — source: ___ fps | cadence: ___ | generate: ___ fps | thermal: ___ | battery: ___

## adb cross-check (if run)
dumpsys display active mode:
Matches HUD: yes/no

## 5-minute stability
Crash: yes/no | sync drift: yes/no | dropped climbing: yes/no
Screen stayed awake: yes/no

## Lifecycle
Audio stopped when backgrounded: yes/no
Resumed correctly: yes/no
Paused on headphone unplug: yes/no
"Open with" from a file manager: works / no

## Error handling
Non-video file shows the error card: yes/no

## Anything unexpected

## Verdict
Phase 0 acceptance: PASS / FAIL
Notes for Phase 1:
```

## What happens next

The `granted` value steers Phase 1:

- **Reads 120 during playback** → the display side is friendlier than feared; Spike A (can Media3 emit extra frames in live playback) becomes the critical path.
- **Content-matches down to 24/60** → expected, and exactly why Spike B exists. We then have to prove the panel will hold 120 for a surface that genuinely submits 120 buffers/sec, and present as an ordinary GPU surface rather than a video one.
- **Anything else, or the app misbehaves** → that's the finding; report it and Phase 1 re-plans around it.
