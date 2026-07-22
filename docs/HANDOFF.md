# Session Handoff — Meditation Timer (native Kotlin/Android)

**Purpose of the new session:** you will have **full network access**. The one thing the
previous sessions could not do was reach the open internet to fetch real audio. Your primary
job is to **source, process, and bundle real CC0 sounds** so the app stops falling back to
synthesized bells/noise. Everything else (the app itself) is built and compiling green.

Read this whole file first, then see **"Your task"** near the bottom for the concrete steps.

---

## 1. What this project is

A from-scratch **native Kotlin Android** meditation timer (NOT Capacitor/webview). Non-negotiable
design rule from the brief: Kotlin owns authoritative session timing (never a JS/UI counter);
sessions survive screen-off / backgrounding / process-death via a foreground service + AlarmManager
fallback. Fully local — no network, no accounts.

- **Branch you must work on:** `claude/app-docs-review-uctnfo` (do NOT push elsewhere).
- **Build:** there is no Android SDK locally and `dl.google.com` is blocked in the sandbox, so the
  ONLY way to compile `:app` is **GitHub Actions**. Workflow: `.github/workflows/build-apk.yml`.
  It runs core tests + `:app:assembleDebug` and uploads the APK as artifact `meditation-debug-apk`.
  Every push to the branch triggers it. Watch runs with the `mcp__github__actions_*` tools.
- **Core logic** (`:core`, pure-Kotlin JVM module) has 34 unit tests, all passing. It compiles and
  tests locally without the Android SDK (`./gradlew :core:test` works in the sandbox once a wrapper
  is bootstrapped — see the workflow for how).

### Module layout
- `:core` — pure Kotlin: session engine, interval scheduling, models, statistics, backup. Testable.
- `:app` — Android: Compose UI, Room + DataStore, foreground service, alarms, audio. Compile-verified
  via CI only (no device/emulator was ever available — see caveat in §6).

---

## 2. THE AUDIO SITUATION (this is why you're here)

### How sounds are wired
- Manifest: **`app/src/main/assets/metadata/sounds.json`** — the seed catalog. Loaded by
  `app/src/main/kotlin/com/meditation/app/data/AssetCatalog.kt`.
- Each recorded sound has `"sourceType": "bundled"` and a `"filename"`. The catalog maps that to a
  URI: `file:///android_asset/audio/<filename>`. So **every bundled file must live at
  `app/src/main/assets/audio/<filename>`.**
- Attributions: `app/src/main/assets/metadata/attributions.json` (one entry per `attributionId`).
- Playback (`app/src/main/kotlin/com/meditation/app/audio/AudioController.kt`):
  - `hasBundledFile(asset)` opens `assets/audio/<filename>`. **If the file is missing, it returns
    false and the app silently falls back** — strikes get synthesized by `ToneSynth`, ambience
    recordings go silent, generated noise still plays.
  - Strikes → `BellPlayer` (SoundPool). Ambience loops → `AmbiencePlayer` (Media3/ExoPlayer, main
    thread only). Procedural noise/drones → `NoiseGenerator` (AudioTrack, PCM_FLOAT).

### The actual problem
**The `app/src/main/assets/audio/` directory does not exist.** The manifest references 19 `.wav`
files that were never added (the sandbox couldn't download them). That's the whole reason bells,
bowls, gongs, and ambience sound synthetic/bad — they're all hitting the ToneSynth/silent fallback.
The synthesized fallbacks are a stopgap, not the intended product.

### Files the manifest expects (put real audio at `app/src/main/assets/audio/<name>`)
Strikes (one-shots, trimmed, normalized, short fade-out, mono or stereo, 44.1 kHz):
```
bell-clear-small-01.wav       Clear Bell    (bright, short decay ~4s)
bell-soft-small-01.wav        Soft Bell     (gentle ~5s)
bell-temple-medium-01.wav     Temple Bell   (ritual, ~8s decay)
bowl-light-01.wav             Light Singing Bowl (airy/high ~10s)
bowl-medium-01.wav            Warm Singing Bowl  (~11s)
bowl-deep-bronze-01.wav       Deep Singing Bowl  (low bronze, ~14s)   << note filename has "-bronze-"
gong-small-01.wav             Small Gong    (~9s)
gong-medium-01.wav            Bronze Gong   (~13s)
gong-deep-01.wav              Deep Temple Gong (~19s)
wood-block-01.wav             Wood Block    (dry, <1s)
wood-mokugyo-01.wav           Wooden Fish   (hollow, ~1s)
crystal-soft-01.wav           Crystal Chime (airy, long, ~7s)
completion-soft-01.wav        Soft Completion Bell (quiet, non-startling ~6s)
```
Ambience (must be **seamless loops** — no clicks at the loop point; 30s–2min is fine, ExoPlayer
loops them):
```
ambience-rain-steady-01.wav   Steady Rain   (even, no thunder)
ambience-ocean-gentle-01.wav  Gentle Ocean  (slow waves, no birds)
ambience-forest-calm-01.wav   Calm Forest   (minimal wildlife)
ambience-fireplace-soft-01.wav Soft Fireplace (low crackle)
ambience-room-tone-01.wav     Quiet Room    (neutral room tone)
ambience-drone-low-01.wav     Low Atmospheric Drone (smooth, non-melodic)
```

### Licensing — IMPORTANT
Use **CC0 / public-domain** sources only (Freesound CC0 filter, Wikimedia Commons PD, archive.org
PD, Pixabay). CC0 requires no attribution, but **fill in `attributions.json` anyway** (creator,
title, sourceName, sourcePage URL, licenseName="CC0", licensePage) — the Sounds detail screen shows
it and it's good hygiene. The `modifications` field is already pre-filled describing typical edits;
update it to match what you actually did.

### Format notes
- Container: `.wav` is what the manifest expects. If you'd rather ship `.ogg`/`.mp3` (much smaller —
  WAV ambience loops can be many MB), you may, **but** then update each `filename` in `sounds.json`
  to the new extension. ExoPlayer and SoundPool both handle ogg/mp3 fine. WAV is simplest/safest.
  Recommendation: **ogg for the long ambience loops** (size), **wav for the short strikes** (quality).
- Keep total APK size sane — prefer ogg for anything over a few seconds.
- Normalize levels so no single sound is dramatically louder than the others; leave headroom
  (peak ~ -3 dBFS) so the app's own volume mixing doesn't clip.
- You'll likely need `ffmpeg` (available with network) to trim/convert/normalize and to create
  seamless loops (crossfade the tail into the head).

---

## 3. Audio bug-fix history (context, so you don't re-break it)

The user reported audio problems on the synthesized fallbacks; these were fixed in
`app/src/main/kotlin/com/meditation/app/audio/`:
- **Sound preview crashed** → ExoPlayer must be created/controlled on the **main thread**
  (`AmbiencePlayer` is confined to `Dispatchers.Main.immediate`; preview builds ExoPlayer inside
  `withContext(Dispatchers.Main)`). Don't move it off-main.
- **Brown noise "broke up" + white noise clipping** → `NoiseGenerator` now: leaky-integrator brown,
  `tanh` soft-clip on all output, **deeper AudioTrack buffer (4×), `THREAD_PRIORITY_URGENT_AUDIO`
  worker thread**, and a gentle low-pass on white. (Latest commit `e36a94a`.) These matter mainly
  if the user keeps using generated noise; real recordings sidestep most of it.
- **Silent bells** → when no bundled file exists, `ToneSynth` synthesizes the strike so it's at least
  audible. Once you add real files, `hasBundledFile` returns true and the real recording plays
  instead — no code change needed, the fallback just stops triggering.

**Once you bundle real files, the fallbacks become inert automatically.** You should not need to
touch the audio Kotlin at all — this is a content task, not a code task. (Optional: once real noise
recordings exist you could even switch the `generated-*` noise entries to bundled loops, but the
procedural ones are fine to keep.)

---

## 4. How to verify your work

1. Add files under `app/src/main/assets/audio/`, update `attributions.json`.
2. Commit + push to `claude/app-docs-review-uctnfo`.
3. CI (`build-apk.yml`) runs automatically. Confirm it's green with the `mcp__github__actions_*`
   tools (list runs for `build-apk.yml`, filter branch, check `conclusion == success`).
   - Note: `assembleDebug` does NOT validate audio content — it'll pass even if a loop clicks. Real
     validation is the user installing the APK. So also **listen to each file yourself** (ffprobe /
     play) before committing; check loop seams on the ambience.
4. Download the APK from the run's `meditation-debug-apk` artifact for the user, or just tell them
   the run URL → Artifacts.

The APK is signed with a **committed debug keystore** (`app/debug.keystore`, universal Android debug
key — not sensitive) so every build installs as a **clean upgrade** over the user's existing app
(no uninstall needed). Keep using it.

---

## 5. Repo orientation (key files)

```
core/src/main/kotlin/com/meditation/core/
  Model.kt            SoundAsset, SessionPreset, SessionStage, IntervalPlan (incl. Random)
  SessionEngine.kt    authoritative timing state machine (advance/project/start/pause/...)
  IntervalScheduler.kt, Clock.kt, History.kt, Statistics.kt, Backup.kt, PresetValidation.kt
app/src/main/kotlin/com/meditation/app/
  data/AssetCatalog.kt        loads sounds.json / attributions.json  <-- audio manifest entry point
  data/Entities.kt, Mappers.kt, Repositories.kt   Room + mapping
  audio/AudioController.kt     playback facade + hasBundledFile()     <-- fallback logic
  audio/BellPlayer.kt, AmbiencePlayer.kt, NoiseGenerator.kt, ToneSynth.kt, AudioFocusManager.kt
  engine/MeditationController.kt   orchestrator; onTerminal records history
  service/…  alarm/…            foreground service, notifications, AlarmManager, QS tile, reminders
  ui/…                          Compose screens + MeditationViewModel
app/src/main/assets/metadata/    sounds.json, attributions.json
app/src/main/assets/audio/       <-- DOES NOT EXIST YET; create it and add the 19 files
.github/workflows/build-apk.yml  the only compiler
docs/  (specs the app was built from), README.md
```

---

## 6. Caveats / state

- **Compile-verified only.** No device or emulator was ever available in these sessions. The whole
  `:app` module is confirmed to *compile* via CI, and `:core` is unit-tested, but UI/audio has not
  been runtime-tested on hardware. The user tests by installing the APK.
- **Latest green build** was #16 (`5e7672f`). Commit `e36a94a` (noise fixes) was pushed and building
  as of handoff — check `build-apk.yml` runs to confirm it went green.
- The app was built to a 4-doc spec set (implementation brief, screen flow, asset manifest, dev
  backlog). Phase 1 + Phase 2 features are all done (random/progressive interval bells, statistics,
  breathing pacer, mala/mantra counter, backup & restore, daily reminder, Quick Settings tile,
  mood/note/tags + history detail, calendar heatmap).
- **Feature work is effectively complete.** Every buildable/CI-verifiable feature across Phase 2,
  Phase 3, and two moonshots (generative ambient music, on-device recommendations) is implemented.
  The remaining gap is **content, not code**: the 19 bundled audio files (see §2).
- **Permanently scrapped — do NOT build, do NOT re-propose.** The user explicitly cancelled all of
  these; they are out of scope for this app:
  - NFC launch, Tasker integration.
  - In-app audio trimming, audio visualizer, spatial audio (the "needs on-device verification" set).
  - Wearable/health integration, HRV-adaptive sessions, encrypted device-to-device sync, group
    meditation (the "needs hardware / peers / a backend" set).

---

## Your task (concrete)

1. Create `app/src/main/assets/audio/`.
2. Source **CC0** audio for the 19 filenames in §2 (match the character described). Freesound's CC0
   filter is the richest source for bells/bowls/gongs/nature; Wikimedia/Pixabay also work.
3. Process with ffmpeg: trim, normalize (~-3 dBFS peak), fade strikes' tails, make ambience seamless
   loops. Convert (ogg for long loops, wav for strikes) — if you change extensions, update
   `filename` in `sounds.json`.
4. Fill in `attributions.json` for each (creator, title, source URL, licenseName "CC0", etc.).
5. **Listen to every file** and check loop seams before committing.
6. Commit + push to `claude/app-docs-review-uctnfo`; confirm CI green; give the user the APK link.
7. Tell the user to install and verify the real sounds — especially the ambience loops and that
   bells/bowls/gongs now sound natural instead of synthesized.

Do not open a PR unless the user asks.
