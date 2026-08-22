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

# Waldschatten 0.1.3 verification (2026-08-22)

## What was wrong with 0.1.2

Jesse reported it from play: no Waldschatten in any new world, `/locate biome
waldschatten:waldschatten` finding nothing. His client log (`logs/latest.log`, 13:41
session) had `waldschatten 0.1.2` loaded, the biome registered (`/locate` said "could not
find within a reasonable distance", not "unknown biome"), and at world creation:

```
Enchanted Forest claimed 176 complete birch-forest climate entries.
```

— and **no** `Waldschatten claimed …` line, and no `claimed NOTHING` warning either. The
claim code was never entered.

Both mods hooked `MultiNoiseBiomeSource.parameters()` with
`@Inject(at = RETURN, cancellable = true)` + `setReturnValue`. Mixin emits
`if (cancelled) return` after each callback at an injection point, so the first handler
to cancel ends the method and every later one is skipped — silently, because the loser's
code never runs and cannot log that it lost. Enchanted Forest's config registers first.
It had entered `mods.json` on 2026-08-19, the same day as 0.1.2; every survey on record
had passed because every survey ran this mod alone.

## The fix, and the gate that would have caught it

- Both mods now claim with MixinExtras `@ModifyReturnValue` (bundled in Fabric Loader
  0.19.3 — no new dependency), which chains: each modifier receives the previous one's
  output. Enchanted Forest 0.1.11 carries the matching change.
- Every run config stages **enchanted-forest** beside vibranium (`stageDepMods` in
  `build.gradle`), and `WaldschattenHeadlessSurvey` asserts that when the mod is loaded its
  biome is both offered by the source *and* sampled at least once. A missing jar fails the
  run loudly. The plain `runHeadlessSurvey` had also never staged vibranium (only the
  Terralith variant got that fix in `fa68710`), so it could not have booted since the
  dependency was added; it does now.
- Survey servers bind `server-port=0`. 25565 was owned by another session's warfront dev
  server during this battery; the survey died at bind with exit code 0 and no PASS line.

## Release battery — 12/12 (local, JDK 25, combined set)

Staged beside the mod: `vibranium-1.8.1`, `enchanted-forest-0.1.11` (built from its
commit `9ea859d`), and for the Terralith rows `Terralith 2.6.4`, `lithostitched
1.8.0+beta3`, `empire_worldgen 0.2.0`. The six seeds are `tools/jitpack-verify.sh`'s.
Every row logged **both** `Waldschatten claimed 440/33 exact lowland slices` and
`Enchanted Forest claimed 176/17 complete birch-forest climate entries` in the same source.

| seed | stack | biomes | waldschatten | enchanted_forest | nearest | hut | relief |
|---|---|---|---|---|---|---|---|
| 0 | vanilla | 55 | 271 (6.41%) | 172 (4.07%) | 275 | 280 | – |
| 1 | vanilla | 55 | 302 (7.15%) | 193 (4.57%) | 362 | 317 | – |
| −1 | vanilla | 55 | 267 (6.32%) | 146 (3.46%) | 45 | 262 | – |
| 8675309 | vanilla | 55 | 187 (4.43%) | 171 (4.05%) | 45 | 156 | – |
| −160353759327030922 | vanilla | 55 | 392 (9.28%) | 185 (4.38%) | 0 | 213 | – |
| 9223372036854775807 | vanilla | 55 | 188 (4.45%) | 106 (2.51%) | 607 | 1248 | – |
| 0 | Terralith | 146 | 25 (0.59%) | 39 (0.92%) | 163 | 189 | 0 |
| 1 | Terralith | 146 | 93 (2.20%) | 63 (1.49%) | 32 | 75 | 0 |
| −1 | Terralith | 146 | 28 (0.66%) | 68 (1.61%) | 289 | 263 | 0 |
| 8675309 | Terralith | 146 | 26 (0.62%) | 40 (0.95%) | 32 | 210 | 0 |
| −160353759327030922 | Terralith | 146 | 48 (1.14%) | 64 (1.51%) | 45 | 374 | 0 |
| 9223372036854775807 | Terralith | 146 | 45 (1.07%) | 22 (0.52%) | 181 | 253 | 0 |

Waldschatten's own shares and distances are identical to the 0.1.1 record (vanilla
4.43–9.28%, Terralith 0.59–2.20%), as they must be: Enchanted Forest swaps the biome on
birch points without moving any parameter point, so it cannot shift where Waldschatten
lands. In the last Terralith row the modifiers happened to apply in the other order
(Enchanted Forest's claim logged first) and both still landed — order-independence
measured, not argued.

Also run, off the record seeds: vanilla 777 passed (6.39% / 3.55%, nearest 101, hut 160).
**Terralith 777 failed the survey's spawn-distance assertion** — no Waldschatten within
1,000 blocks of spawn — with both claims landed and both biomes present. At ~0.6% of
cells a 1,000-block radius holds about 1.5 expected samples, so roughly one seed in five
will miss that bar by chance on the Terralith stack; this is a property of the gate, not
of this fix, and is left on record rather than tuned away.

## Not covered

- The client render battery was not rerun: nothing in 0.1.3 draws. The 0.1.2 frames stand.
- Biome presence in the live server world is a fresh-chunk question.

# Waldschatten 0.1.4 verification (2026-08-22)

## The report, and what the world said

Jesse: the witch hut "filled with wood", "trees way way too high, some surpass 300 blocks".
His current world, censused from the 26.2 `dimensions/minecraft/overworld/region` files
(`census.js`, 16,479 chunks): **105,120 `waldschatten:twisted_log` and 2,052,873
`twisted_leaves` above y=150, the highest at y=319** — the build ceiling — and
`minecraft:dirt` at y=148 inside the hut's footprint, with 399 twisted logs in a 32×32 box
whose template holds 28.

Cause: `twisted_forest_vegetation`'s `random_selector` named its trees as bare
`{feature, placement: []}`. Vanilla's selectors name `oak_checked`-style placed features
whose one modifier is `would_survive <sapling>`. Without it, the biome-level `count 16` on
an `OCEAN_FLOOR` heightmap (leaves and logs block motion, so they *are* the floor) lands
attempts on canopies, and `TreeFeature` plants dirt under the trunk wherever it is — so the
next attempt finds dirt and plants again. Towers.

## The fix, and the gate that would have caught it

- `tools/gen-worldgen.js` gains `checked(id)`: every tree entry in the selector carries
  `block_predicate_filter` → `would_survive waldschatten:twisted_sapling`. Regenerated; the
  generator's cross-check still passes.
- The headless survey generates the 25 chunks around the nearest Waldschatten cell and
  asserts the tallest `twisted_log` column span is ≤ 32 (one tree is ≤ 15 of trunk plus
  branch steps; a neighbour's branch reaching into the column measured 17–18; stacking is
  all-or-nothing across 400 attempts and measures 80+).
- **Found on the way — the relief probe had never measured anything.** It queried heights
  on chunks it had not generated; 26.2 answers those from an empty placeholder at the
  world floor, so every column read −64, relief 0, and "all six seeds flat" was never
  true. `groundHeight` now generates the chunk and reads ground through logs and leaves.
  Honest values on the six seeds: 0, 0, 14, 17, 29, 36 blocks over ~64 connected chunk
  centres — gently rolling, as the erosion slice promises — so the bar moves from the
  uncalibrated 24 to 48. A mountain reads 100+.

## Release battery — 12/12, one jar, combined set

Staged beside the mod as for 0.1.3 (vibranium 1.8.1, enchanted-forest 0.1.11; Terralith
rows add Terralith 2.6.4, lithostitched, empire_worldgen 0.2.0).

| seed | stack | waldschatten | enchanted_forest | tallest log column | relief |
|---|---|---|---|---|---|
| 0 | vanilla | 271 (6.41%) | 172 (4.07%) | 17 | – |
| 1 | vanilla | 302 (7.15%) | 193 (4.57%) | 15 | – |
| −1 | vanilla | 267 (6.32%) | 146 (3.46%) | 18 | – |
| 8675309 | vanilla | 187 (4.43%) | 171 (4.05%) | 15 | – |
| −160353759327030922 | vanilla | 392 (9.28%) | 185 (4.38%) | 15 | – |
| 9223372036854775807 | vanilla | 188 (4.45%) | 106 (2.51%) | 15 | – |
| 0 | Terralith | 25 (0.59%) | 39 (0.92%) | 15 | 14 |
| 1 | Terralith | 93 (2.20%) | 63 (1.49%) | 17 | 0 |
| −1 | Terralith | 28 (0.66%) | 68 (1.61%) | 15 | 0 |
| 8675309 | Terralith | 26 (0.62%) | 40 (0.95%) | 14 | 36 |
| −160353759327030922 | Terralith | 48 (1.14%) | 64 (1.51%) | 15 | 29 |
| 9223372036854775807 | Terralith | 45 (1.07%) | 22 (0.52%) | 15 | 17 |

Biome shares, spawn distances and hut distances are unchanged from 0.1.3 (a placement
filter on trees cannot move a biome). Survey servers bind `server-port=0` since 0.1.3.

## The hut, for the record

The hut was also pasted over by a warfront `aegis_town` (206 stripped birch logs and 174
stone bricks in its box, none in the template; its own dark-oak planks down to 27 of 84)
and footed by warfront's `SiteFoundations`. Both are fixed in **warfront 0.4.20**: footing
is per piece from the piece's own bottom row, and bases/castles keep clear of
`waldschatten:witch_huts` (and bases of `set_pieces`) via `avoid_sets`. See
`warfront/VERIFY.md`.

## Not covered

- Render battery not rerun: nothing drawn changes. Existing worlds keep their towers —
  only fresh chunks get the checked placement.
