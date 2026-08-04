# White / Pink / Brown Noise — Implementation Guide

A port-ready description of the noise generators, written so they can be rebuilt in another app.
Everything here is DSP: no audio files, no assets, ~200 lines of code total.

The reference implementation is `core/src/main/kotlin/com/meditation/core/NoiseDsp.kt` and
`NoiseColorCalibration.kt`, with tests in `NoiseColorCalibrationTest.kt`. The code is plain Kotlin
with no platform dependencies, so it ports directly to any language.

---

## 1. What actually matters

Most of the perceived quality comes from four things that are easy to miss. Textbook-correct noise
sounds bad; these are the differences.

1. **Stereo must be decorrelated.** Run two *independent* generators, one per channel. Copying one
   mono stream to both channels sounds identical to mono — it collapses to a point inside the head.
   Independence is what makes noise sound wide and enveloping. This is the single biggest factor.
2. **Voice the top end down.** A mathematically correct colour still carries far more high-frequency
   energy than people expect from that colour. Untreated white hisses; untreated pink reads as
   "hiss sitting on top of the low end"; untreated brown is nowhere near as deep as commercial brown
   noise. Every colour gets a shelf/low-pass on top of its raw slope (§5).
3. **Match by loudness, not amplitude.** Equal RMS makes brown sound much quieter than white,
   because brown's energy sits where the ear is least sensitive. Match K-weighted loudness (§6).
4. **Background playback is a platform problem, not a DSP one.** Continuous audio needs a
   foreground service (Android) or equivalent, or the OS freezes the process and audio stops (§9).

---

## 2. Signal chain

Per channel, per sample:

```
random white  ->  colour filter  ->  30 Hz high-pass  ->  18 kHz low-pass
                  (none/pink/brown)

              ->  voicing (shelf and/or low-pass, per colour)

              ->  calibrated gain  ->  user volume

              ->  peak limiter (linked across L/R)  ->  output
```

Two of these chains run in parallel from independent random streams (left and right). The limiter
is shared: it computes one gain reduction from the larger of the two channel peaks and applies it to
both, so the stereo image never shifts.

Output format: **48 kHz, stereo, 32-bit float**. 48 kHz is native on essentially all mobile audio
hardware (so nothing resamples on the way out) and is the rate the loudness coefficients below are
defined at.

---

## 3. Colour generators

### White
Uniform random in [-1, 1]. Equal energy per Hz.

```kotlin
val white = random.nextDouble() * 2.0 - 1.0
```

### Pink (−3 dB/octave)
Paul Kellett's approximation. Accurate to well within a dB across the audible band. Keep the `0.11`
output scale — the rest of the calibration assumes it.

```kotlin
b0 = 0.99886 * b0 + white * 0.0555179
b1 = 0.99332 * b1 + white * 0.0750759
b2 = 0.96900 * b2 + white * 0.1538520
b3 = 0.86650 * b3 + white * 0.3104856
b4 = 0.55000 * b4 + white * 0.5329522
b5 = -0.7616 * b5 - white * 0.0168980
val pink = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362) * 0.11
b6 = white * 0.115926
```

### Brown (−6 dB/octave)
A leaky integrator. The pole sets the corner below which the slope flattens out.

```kotlin
// corner 18 Hz at 48 kHz -> pole = 0.9976466
val pole  = exp(-2.0 * PI * 18.0 / 48_000.0)
val drive = 1.0 - pole
state = pole * state + drive * white
```

Put the corner **below** the audible band (18 Hz) so brown keeps its deep weight, and let the 30 Hz
high-pass handle the subsonic energy. An earlier version used a 70 Hz corner to control rumble, and
it audibly thinned the low end — the wrong fix.

---

## 4. Filters (RBJ cookbook biquads)

Direct-form 1, one instance per filter per channel (filters are stateful — never share them between
channels).

```kotlin
y = b0*x + b1*x1 + b2*x2 - a1*y1 - a2*y2
x2 = x1; x1 = x; y2 = y1; y1 = y
```

With `w0 = 2π*f0/fs`, `cw = cos(w0)`, `alpha = sin(w0)/(2Q)`, `Q = 0.7071` (Butterworth):

**High-pass**
```
a0 = 1 + alpha
b0 =  ((1+cw)/2)/a0 ,  b1 = (-(1+cw))/a0 ,  b2 = ((1+cw)/2)/a0
a1 =  (-2*cw)/a0    ,  a2 = (1-alpha)/a0
```

**Low-pass**
```
a0 = 1 + alpha
b0 =  ((1-cw)/2)/a0 ,  b1 = (1-cw)/a0 ,  b2 = ((1-cw)/2)/a0
a1 =  (-2*cw)/a0    ,  a2 = (1-alpha)/a0
```

**High shelf** (`A = 10^(gainDb/40)`, `alpha = sin(w0)/2 * sqrt((A + 1/A)(1/Q - 1) + 2)`):
```
a0 = (A+1) - (A-1)*cw + 2*sqrt(A)*alpha
b0 =  A*((A+1) + (A-1)*cw + 2*sqrt(A)*alpha) / a0
b1 = -2*A*((A-1) + (A+1)*cw)                / a0
b2 =  A*((A+1) + (A-1)*cw - 2*sqrt(A)*alpha)/ a0
a1 =  2*((A-1) - (A+1)*cw)                  / a0
a2 = ((A+1) - (A-1)*cw - 2*sqrt(A)*alpha)   / a0
```

**Shared for all colours:** 30 Hz high-pass, then 18 kHz low-pass.
The high-pass removes inaudible subsonic energy that otherwise eats headroom and moves speakers for
nothing.

---

## 5. Voicing (the part that makes it sound right)

Applied after the shared filters. These are **taste settings, not definitions** — they were tuned by
listening, and they are the difference between "technically correct" and "sounds like the good noise
apps". Every one of these was arrived at because the untreated version was judged too bright.

| Colour | Voicing | Effect (dB re 500 Hz band) |
|---|---|---|
| **White** | high shelf **−12 dB @ 2 kHz**, then low-pass **8 kHz** | 4 kHz: +7.9 → −1.1 · 8 kHz: +6.2 → −3.1 |
| **Pink** | high shelf **−18 dB @ 1.2 kHz**, then low-pass **9 kHz** | 2 kHz −10 · 4 kHz −14 · 8 kHz −18 |
| **Brown** | **two cascaded** low-passes @ **1.6 kHz** | 2 kHz −6 · 4 kHz −12 · 8 kHz −16 |

Notes worth keeping:

- **Use a shelf, not just a low-pass, for white and pink.** A steep cut leaves whatever survives above
  the corner audible as a *separate hissy layer*. A shelf tilts the whole top down gradually so it
  blends into the body of the sound. This was the specific fix for pink sounding like "white noise on
  top of brown noise".
- The strengths keep the colours distinct: white stays roughly 13 dB brighter than pink, pink well
  above brown. Verify this if you retune (§8).
- Everything below ~500 Hz is untouched in all three, so none of them lose body.
- Single constants: raise the shelf gain toward −6 for brighter, lower for darker.

---

## 6. Loudness matching

Match **K-weighted** (ITU-R BS.1770) loudness, not RMS.

K-weighting is two biquads in series. These coefficients are defined at 48 kHz:

```
Stage 1 (high shelf):  b = [ 1.53512485958697, -2.69169618940638, 1.19839281085285 ]
                       a = [ 1.0,              -1.69065929318241, 0.73248077421585 ]

Stage 2 (RLB high-pass): b = [ 1.0, -2.0, 1.0 ]
                         a = [ 1.0, -1.99004745483398, 0.99007225036621 ]
```

Measure `sqrt(mean(y²))` of the K-weighted signal, discarding the first ~4800 samples so the filters
settle.

### Deriving the gains

Don't hard-code gains. Derive them from the *actual* chain at startup, so they can never drift out of
step with the filters:

1. For each colour, generate ~24k samples at unity gain through the full chain (§2, minus limiter).
2. Measure its K-weighted loudness `L_c` and its peak `P_c`.
3. Pick the shared target as the loudest level at which **every** colour still has headroom:
   `T = min over colours of (PEAK_CEILING * L_c / P_c)`, with `PEAK_CEILING = 0.80`.
4. Gain for each colour is `T / L_c`.

This runs in a few milliseconds and guarantees both equal perceived loudness and peaks below the
ceiling, so the limiter only ever catches occasional transients.

**Expect brown's gain to be large** (10–20×) and white's to be small. That is correct: brown must be
electrically much hotter to sound equally loud. A consequence worth knowing is that white and pink
end up measurably quieter than an RMS-matched implementation at the same volume setting.

---

## 7. Peak limiter

Instant attack, smooth release, applied to the **larger of the two channel peaks** so both channels
get the same reduction.

```kotlin
class PeakLimiter(threshold: Double = 0.97, fs: Double = 48_000.0, releaseSeconds: Double = 0.25) {
    private val releaseCoeff = exp(-1.0 / (releaseSeconds * fs))
    private var gain = 1.0
    fun gainFor(peak: Double): Double {
        val needed = if (peak > threshold) threshold / peak else 1.0
        gain = if (needed < gain) needed else needed + (gain - needed) * releaseCoeff
        return gain
    }
}
```

Prefer this over a per-sample `tanh` soft clip: `tanh` distorts the signal continuously, whereas the
limiter is transparent until it is actually needed.

---

## 8. Tests worth porting

These caught real problems and are cheap to run offline (no audio hardware needed). Measure band
energy with a band-pass biquad (Q = 4) at the given centre frequency.

- **Slope correctness** — with voicing *disabled*, pink is −3 dB/oct and brown −6 dB/oct. Keep a
  switch to bypass voicing so the colour maths and the taste settings can be verified separately;
  otherwise a voicing change silently breaks the slope assertion.
- **Loudness match** — all three within ~1 dB of the shared target.
- **Headroom** — peak < 0.98 for every colour.
- **Voicing** — white's 8 kHz band pulled down but its 2 kHz presence retained; pink's 4 kHz and
  8 kHz well down but 125 Hz intact; brown's 4 kHz far below its raw slope.
- **Distinctness** — at 4 kHz relative to 500 Hz, white > pink + 3 dB > brown + 6 dB. Stops retuning
  from collapsing the three into the same sound.
- **Decorrelation** — correlation between the two channels < 0.1.
- **Limiter** — output never exceeds the threshold when fed over-full-scale input.

Note that constant-Q band analysis widens with frequency (+3 dB/octave of bandwidth), so subtract
`3 * log2(fHigh/fLow)` before reading a slope. Flat white reads as *rising* in this analysis.

---

## 9. Playback / platform notes

- **Stream continuously**; don't generate a buffer and loop it (a loop point in noise is audible, and
  there is no reason to loop when generation is this cheap).
- **Give the output buffer real depth.** Use several times the minimum buffer size; a minimum-size
  buffer starves on any GC pause or scheduling hiccup and the noise audibly breaks up. The reference
  uses `max(4 × minBufferSize, 32 KB)` and runs the generator thread at audio priority.
- **Background playback needs a foreground service** (Android) with a media-playback type and an
  ongoing notification. Without it the process is frozen when the user leaves the app and audio stops
  — this looks exactly like an audio bug but is not one.
- **Audio focus is a policy choice.** For ambient noise, ignoring focus loss from other media apps
  (so it keeps playing underneath) is usually right; pausing for phone calls is still correct. There
  is no way to make the *other* app quiet down — the two streams simply mix.
- **Keep drones/binaural separate.** Binaural beats need a different tone per ear and must not be
  decorrelated or limited the same way.

---

## 10. Summary of tuned constants

```
Sample rate            48000 Hz, stereo, float
High-pass (all)        30 Hz
Low-pass (all)         18000 Hz
Brown corner           18 Hz    (pole = exp(-2π·18/48000) = 0.9976466)
White voicing          shelf -12 dB @ 2000 Hz, low-pass 8000 Hz
Pink voicing           shelf -18 dB @ 1200 Hz, low-pass 9000 Hz
Brown voicing          low-pass 1600 Hz, applied twice
Peak ceiling (calib)   0.80
Limiter                threshold 0.97, release 0.25 s, stereo-linked
```
