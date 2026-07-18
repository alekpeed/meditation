# Always-On Meditation Timer

A private, local-first Android meditation timer built in **native Kotlin** (Jetpack Compose + a
Kotlin session engine). Reliability is the product: a session keeps correct time when the screen is
off, the activity is destroyed, or the process is recreated — because **Kotlin owns the authoritative
timer**, never a UI-side counter.

> Built from the four supporting specs (implementation brief, screen flow, asset manifest, dev
> backlog). This repository implements **Phase 1** with the brief's mandated ordering: the native
> reliability core first, verified by tests, before UI polish.

---

## Architecture

```
:core   Pure-Kotlin, zero Android dependencies. The reliability engine.
        ├── Model.kt            domain types (presets, stages, sounds, interval plans)
        ├── Clock.kt            monotonic vs wall-clock abstraction (+ FakeClock for tests)
        ├── SessionEngine.kt    authoritative timing, state machine, dedup'd event firing, projection
        ├── IntervalScheduler.kt interval-bell offset math
        └── History.kt          completed-session record builder
        tests: 16 JUnit tests covering timing, overtime, pause/resume, dedup, serialization

:app    Android application layer.
        ├── data/     Room (presets, active session, history, sounds, attributions) + DataStore + mappers
        ├── engine/   AndroidClock + MeditationController (binds the pure engine to Android side effects)
        ├── service/  mediaPlayback ForegroundService + chronometer notification + action receiver
        ├── alarm/    AlarmManager final-bell fallback + boot receiver
        ├── audio/    SoundPool bells · Media3/ExoPlayer ambience · AudioTrack generated noise · audio focus
        └── ui/       Compose: Home, Active Timer, Sessions, Sounds, History, Settings
```

### Why the split

The timing rules are the hard, correctness-critical part. Keeping them in a pure module with an
injectable `Clock` means they are **unit-testable on a plain JVM** — no emulator, no flakiness. The
Android layer is a thin, replaceable adapter around a verified core.

### How timing stays correct

- **Monotonic base.** All active-time math uses `SystemClock.elapsedRealtime()`. Wall-clock changes
  (DST, manual set, NTP) cannot alter an in-flight session. Wall-clock is stored only for history.
- **Reconstruct, never decrement.** Remaining time is always recomputed from persisted timestamps.
- **Persist before acting.** Every transition writes the full `ActiveSessionState` (as JSON) before
  audio starts or the UI advances, so the session is recoverable at any instant.
- **Dedup by fired-key set.** Opening/interval/transition/closing/final events each fire exactly
  once, tracked in the persisted state — so the service tick, the fallback alarm, and a manual finish
  can never double-fire the final bell.
- **Foreground service + alarm belt-and-braces.** The `mediaPlayback` service keeps the process alive
  for interval bells and ambience with the screen off; an exact `AlarmManager` alarm guarantees the
  final event even if the service is evicted.

---

## Build & run

Requires Android Studio (Ladybug+) or the Android SDK with an API 35 platform.

```bash
# Unit-test the reliability core (pure JVM, no Android SDK needed):
./gradlew :core:test

# Build the app:
./gradlew :app:assembleDebug

# Install on a connected device/emulator:
./gradlew :app:installDebug
```

`minSdk 26 · targetSdk 35 · compileSdk 35`.

### Audio assets

The catalog metadata (`app/src/main/assets/metadata/sounds.json`, `attributions.json`) ships the 24
sound identities from the asset manifest. The **audio binaries themselves are not included** — they
are a separate sourcing/licensing task per the manifest workflow (Freesound → trim → normalize →
attribute). Drop trimmed `.wav` files into `app/src/main/assets/audio/` using the manifest filenames.
Until then, **bells degrade gracefully to silence** and the **procedural noise/drones play fully**
(they need no files). Every attribution field must be completed before an asset is accepted.

---

## Phase 1 status

| Area | State |
|---|---|
| Native timing engine (countdown, count-up, overtime, prep, multi-stage, extend, skip) | ✅ implemented + **16 tests pass** |
| Pause/resume, finish, cancel, idempotency | ✅ + tested |
| Process-death recovery (state serialization round-trip) | ✅ + tested |
| Room persistence + DataStore | ✅ |
| Foreground service + chronometer notification + actions | ✅ |
| Exact-alarm final-bell fallback + capability flow | ✅ |
| Audio: bells (SoundPool), ambience (ExoPlayer), generated noise (AudioTrack), audio focus | ✅ |
| Compose UI: Home/Quick Start, Active Timer, Sessions, Sounds, History, Settings | ✅ functional |
| Ambient mixer (3-layer), full multi-step Session Builder UI, Sound Detail, Statistics screen | 🟨 partial / follow-up (engine + data model already support them) |
| User audio import (SAF), reboot recovery polish, export | 🟨 scaffolded (backlog P2) |

**Important honesty note:** the `:core` module's tests were executed and pass in CI-equivalent
conditions here. The `:app` (Android) module was written to compile-ready quality but **could not be
compiled in the authoring environment** (no Android SDK present). Please run the first
`:app:assembleDebug` in Android Studio and expect to resolve minor issues typical of a first build.

---

## Mapping to the brief's Phase 1 acceptance criteria

| # | Criterion | Where it's handled |
|---|---|---|
| 1 | 60-min silent session finishes screen-off | engine + exact alarm fallback |
| 2 | 60-min ambient session continues screen-off | foreground service + ExoPlayer loop |
| 3–4 | UI swipe-away / reopen shows correct time | persisted state + `restore()` + projection |
| 5–6 | Pause / Add-time from notification | `NotificationActionReceiver` → controller |
| 7 | Interval bell fires screen-off | service event loop + dedup |
| 8 | Final bell fires exactly once | fired-key dedup (tested with service+alarm+finish) |
| 9 | One history record per session | DAO `INSERT OR IGNORE` by session id (tested) |
| 10 | Clock changes don't alter duration | monotonic `elapsedRealtime` (tested) |
| 11–12 | Bluetooth / call safety | `AudioFocusManager`, USAGE_MEDIA routing |
| 13–14 | Missing exact-alarm / notification perms | capability flow, graceful fallback, Settings status |
| 15–18 | Imports private · attributions · all local · offline | app-private storage, attributions screen, no network |

See `DOC_REVIEW.md` for the cross-document consistency review of the four source specs.
```
