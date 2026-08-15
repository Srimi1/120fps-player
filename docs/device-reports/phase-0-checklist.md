# Phase 0 — On-Device Verification Checklist

**Device:** Galaxy S24 Ultra
**Purpose:** close the Article 9 requirement. Phase 0 is not done until `phase-0-results.md` is filed from this checklist.

Phase 0 is deliberately small: it is the vehicle every later spike rides on. We are not testing interpolation here (none exists yet) — we are establishing the **baseline**, especially what refresh rate the phone is willing to give a video app. That number decides how much of the 120fps plan is reachable.

---

## Getting the APK

CI builds a debug APK on every push. Download it from the run's artifacts:

1. Open the [Actions tab](https://github.com/Srimi1/120fps-player/actions) → the most recent green **Android CI** run on `claude/120fps-realtime-player-25ie4s`
2. Scroll to **Artifacts** → download `app-debug`
3. Unzip → `app-debug.apk` → transfer to the phone and install (allow "install from unknown sources")

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
- [ ] App launches to a black screen with an "Open video file" button and a stats HUD in the top-left
- [ ] Note anything odd (crash, permission prompt, rotation weirdness — the app is locked to landscape)

### 2. Playback — 1080p 24fps
- [ ] File picker opens and lists videos
- [ ] Video plays, correct aspect ratio, not stretched or cropped
- [ ] **Audio/video sync is correct** — watch someone speaking for ~30s
- [ ] Playback is smooth (some judder on pans is *expected and normal* at 24fps — that's the problem we're here to solve later)
- [ ] **Tap the video to pause, tap again to resume** — useful for holding a frame while reading the HUD

### 3. Playback — 1080p 60fps
- [ ] Plays correctly with sync

### 4. The HUD — the important part

The HUD shows five live values. Record what each reads **during playback** for both clips:

| HUD row | What it means | What to expect |
|---|---|---|
| `render` | frames ExoPlayer actually rendered in the last second | should track the source rate (~24, ~60) |
| `dropped` | cumulative dropped frames | should stay at or near 0 |
| `display` | **the refresh rate the OS actually granted** | this is the headline number |
| `thermal` | thermal headroom, 0.0–1.0 | low is good; NaN means unsupported |
| `battery` | instantaneous draw in mA | negative usually means discharging |

- [ ] `render` is plausible for each clip
- [ ] `dropped` stays low
- [ ] **`display` — write down the exact value for each clip.** This is the single most valuable number in Phase 0.
- [ ] `thermal` shows a real number (not NaN)
- [ ] `battery` shows a real number

**Why `display` matters:** the app requests 120 Hz. Samsung's LTPO panels are documented to content-match "video-looking" surfaces down to the source frame rate — so this may well read 24 or 60 instead of 120 during playback. **Either answer is a valid, useful result.** If it reads 120, the 120fps boost mode has a path. If it content-matches down, we know early that we have to present as an ordinary GPU surface rather than a video surface, and Spike B becomes the priority. Do not treat a low number as a bug — record it.

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

### 6. Stability
- [ ] Let a clip play for ~5 minutes — no crash, no drift out of sync, `dropped` not climbing steadily
- [ ] Background the app and return — playback recovers or stops cleanly (no crash)

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
- HUD — render: ___ fps | dropped: ___ | **display: ___ Hz** | thermal: ___ | battery: ___ mA
- Subjective smoothness on pans:

## 1080p 60fps clip
- File:
- Plays: yes/no
- A/V sync:
- HUD — render: ___ fps | dropped: ___ | **display: ___ Hz** | thermal: ___ | battery: ___ mA

## adb cross-check (if run)
dumpsys display active mode:
Matches HUD: yes/no

## 5-minute stability
Crash: yes/no | sync drift: yes/no | dropped climbing: yes/no

## Anything unexpected

## Verdict
Phase 0 acceptance: PASS / FAIL
Notes for Phase 1:
```

## What happens next

The `display` value steers Phase 1:

- **Reads 120 during playback** → the display side is friendlier than feared; Spike A (can Media3 emit extra frames in live playback) becomes the critical path.
- **Content-matches down to 24/60** → expected, and exactly why Spike B exists. We then have to prove the panel will hold 120 for a surface that genuinely submits 120 buffers/sec, and present as an ordinary GPU surface rather than a video one.
- **Anything else, or the app misbehaves** → that's the finding; report it and Phase 1 re-plans around it.
