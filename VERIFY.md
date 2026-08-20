# Waldschatten 0.1.1 verification

Date: 2026-08-18. Target: Minecraft 26.2, Fabric Loader 0.19.3,
Fabric API 0.157.0+26.2, Loom 1.17, JDK 25.

## Build and worldgen

- JDK 25 clean `test build` — passed remotely from commit `81ad017a8e`; no local Java was
  started.
- Six vanilla worlds and six worlds with the live terrain stack (Terralith 2.6.4,
  lithostitched 1.8.0+beta3, Empire Worldgen 0.1.0) — **12/12 passed**.
- Waldschatten was within 1,000 blocks of actual spawn in all 12 worlds. Vanilla distances
  were 0–607 blocks; live-stack distances were 32–289 blocks.
- The Waldschatten witch hut was locatable in all 12 worlds. The live-stack hut distances
  were 75–374 blocks from spawn.
- Connected-patch terrain probes sampled up to 64 chunk centres in every live-stack world.
  All six measured 0 blocks of relief (24 blocks is the failure threshold).
- Biome-source samples found Waldschatten in 4.43–9.28% of vanilla cells and 0.59–2.20% of
  live-stack cells over the 8,192×8,192 survey square.
- The client render battery was not rerun for 0.1.1 because the remote container cannot
  execute Minecraft's extracted graphics natives. The 0.1.0 frames remain the visual proof;
  0.1.1 changes placement, structure placement, and release-only server surveying, not the
  models, textures, structures, palette, canopy features, or client rendering.

## Release and shared-pack gate

- GitHub release, independent download/hash verification, shared manifest update, client
  convergence, and server deployment follow this completed 0.1.1 verification.

# Waldschatten 0.1.2 verification (2026-08-20, post-ship)

0.1.2 shipped on 2026-08-19. This entry is written a day later, during a folder-wide
audit that found the release had no verification of its own — and found the reason.

## Why 0.1.2 shipped on 0.1.0's screenshots

The render battery could not run. Waldschatten hard-depends on `vibranium >=1.8.1`, and
`runGametest` never staged a vibranium jar into its run directory, so the client died at
mod resolution before a single frame:

```
Immediate reason: [HARD_DEP_NO_CANDIDATE waldschatten 0.1.2 {depends vibranium @ [>=1.8.1]}]
```

The battery stopped being possible the moment that dependency was added, and a battery
that cannot start looks exactly like a battery nobody bothered to run. The newest frames
on disk were from 2026-08-18 — 0.1.0's.

**Fixed**: `runGametest` now stages vibranium the same way `runTerralithSurvey` already
staged Terralith, from `~/Desktop/mc-server/server-mods-staging` by default and
overridable with `WALDSCHATTEN_DEP_MODS`. It fails loudly with a named reason if no
vibranium jar is there, rather than dying at mod resolution.

## Battery, on 0.1.2

`./gradlew runGametest` — **BUILD SUCCESSFUL in 2m 30s**, 8 frames, 2026-08-20 16:33. ✅

Worldgen survey, from the run's own log:

- world offers 56 biomes, `waldschatten` present: **true**
- 4,225 samples over 8,192×8,192 blocks at y=64 — **waldschatten 188 (4.45%)**, vanilla
  `forest` 197 (4.66%), vanilla `dark_forest` 18 (0.43%)
- nearest waldschatten at (−192, −576), **607 blocks from spawn**
- nearest witch hut at (1024, −704), **1,248 blocks from spawn**

The biome is present at a share comparable to vanilla forest and reachable from spawn,
and the hut generates. ✅

**Frames read** (rule 9): `waldschatten_blocks` — every block draws with its own texture,
no missing sheets and no truncation; the log, plank, stone and fungal set are all
distinguishable. `waldschatten_canopy`, `biome_day`, `witch_hut`, `set_pieces`,
`dark_night`, `dark_soul_torch`, `dark_again` all produced and legible. ✅

## What is actually deployed

- Jar on the server hashes to `9559d60b4656f364a1d7e781…`, exactly what `mods.json`
  declares — verified by hashing the file pulled off the server. ✅
- `waldschatten 0.1.2` initialised in the 13:43 boot; 104 mods, zero mixin failures,
  zero errors. ✅
- Rebuilt from committed source: `tools/jar-compare.js` reports **SAME CONTENT** — all
  252 entries match by CRC, differing only in the order Loom wrote
  `Fabric-Loom-Client-Only-Entries`. The shipped jar is the committed source. ✅

## Not covered

The survey world is a throwaway gametest world without Terralith. The empire server runs
Terralith, and `runTerralithSurvey` exists precisely because that is a different question;
it was not re-run for 0.1.2. Biome presence on the live world is also a fresh-chunk
question — the world's directories predate this release.
