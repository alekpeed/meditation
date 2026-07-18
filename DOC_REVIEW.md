# Cross-Document Review — Source Specifications

Review of the four supporting documents (Implementation Brief, Screen Flow, Asset Manifest,
Development Backlog) for internal consistency, gaps, and build risks. The specs are unusually
coherent; the items below are the discrepancies worth resolving so they don't surface as bugs.
Where an item affected implementation, the decision taken in this codebase is noted.

## Contradictions to resolve

1. **Generated-sound count: 4 vs 5.** Brief §8 lists 4 generated sounds (white/pink/brown + one
   drone). Asset Manifest §7 and Backlog H4 list **5** (white, pink, brown, sine drone, dual drone).
   → *Adopted 5* (both drones), matching the manifest.

2. **Library size: "~20" vs 24.** Brief §8 says "approximately 20"; manifest/backlog total 24
   (13 acoustic + 6 ambience + 5 generated). Harmless, but state the real target as 24.

3. **Crystal/chime has no home.** Manifest §5 categorizes `crystal-soft-01` as **"Chime"**, but the
   Brief's data-model enum (§6.1) has no `chime` category and the Sounds screen (Screen Flow §9) has
   no Chime tab. → *Added a `CHIME` category* to the core enum so the crystal sound is classifiable
   and browsable.

4. **Export priority: P1 vs P2.** Brief §7.6 and Screen Flow §12/§15 present JSON/CSV export as a
   Phase-1 feature; Backlog J4 marks it **P2**. → Treated as P2 (scaffolded, not in the release gate).

5. **Ambient/audio-focus priority vs the release gate.** Backlog marks E3 (ambient playback) and E4
   (audio focus) as **P1**, yet Brief acceptance criteria #2, #11, #12 (screen-off ambient session,
   Bluetooth safety, call safety) are mandatory release gates. Effectively these are P0-for-release.
   Recommend re-labeling or explicitly noting the gate dependency.

6. **Model fields with no V1 UI.** `SessionPreset.doNotDisturbBehavior` and
   `SessionStage.spokenCueIds` exist in the Brief data model, but DND integration and spoken cues are
   Phase 2 (§15), and the Session Builder (Screen Flow §7) exposes neither. → Kept as forward-compat
   fields; documented that they have no Phase-1 UI.

## Naming / minor inconsistencies

7. **"Sessions" tab actually shows presets.** The bottom-nav "Sessions" destination (Screen Flow §6)
   browses *presets*, while past sessions live under "History." A user may read "Sessions" as session
   history. Consider "Presets" for the tab (Brief §7.7 already calls the screen "Presets").

8. **Asset id vs filename drift.** Manifest §8 uses id `bowl-deep-01` with filename
   `bowl-deep-bronze-01.wav`, but the §3 naming examples show `sound-bowl-deep-bronze-01-thumb.webp`
   while §14 shows `sound-bowl-deep-01-thumb.webp`. Pick one stem convention for the asset pipeline.

9. **Add-time presets differ.** Screen Flow §4 offers +1/+5/+10/custom; Brief §7.1 lists 1/5/custom
   (no +10). Notification action is fixed at "Add 5". → UI offers +1/+5/+10; notification uses +5.

10. **Generated drone build task is missing from the backlog.** Manifest §7 and Brief §7.4 require
    sine/dual drones, but Backlog E5 ("Procedural noise") only names white/pink/brown. Add drone
    generation to E5's scope. → Implemented all five in `NoiseGenerator`.

## Gaps / underspecified

11. **Statistics screen entry point.** Statistics is a full screen (Screen Flow §14, Brief screen #8)
    but is not a bottom-nav destination and no doc states how it's reached. → Folded summary stats
    into the History screen for Phase 1.

12. **Sound catalog source of truth.** Manifest stores `sounds.json`/`attributions.json` as bundled
    files; Brief §3 and Backlog A2 put "assets" in Room. → Resolved as: bundled JSON is seeded into
    Room on first launch (`AssetCatalog` + `SoundRepository.seedIfEmpty`); imports live only in Room.

13. **Attribution "complete every field" vs CC0.** Manifest §9 requires every attribution field
    filled before acceptance, but CC0 assets legitimately have no required creator/license page.
    Recommend allowing CC0 records with empty creator/licensePage.

14. **History display across DST.** Acceptance #10 is satisfied for *elapsed duration* via monotonic
    time, but the spec doesn't address how wall-clock history timestamps render across a DST boundary.
    Low risk; worth a note.

## Strengths

- P0-first gating (no visual polish until reliability passes) is disciplined and correct.
- Acceptance criteria are concrete and testable; the dual reliability path (foreground service for
  audio/interval sessions, alarm-only for silent sessions) is the right call.
- The single biggest correctness risk — a duplicated final bell when both the service and the alarm
  fire — is explicitly called out in every doc and is covered here by fired-key deduplication with a
  dedicated test.
