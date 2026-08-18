# Waldschatten — what was built, and where

A Brothers Grimm reading of the Schwarzwald for **Minecraft 26.2 / Fabric**. Not 1.21, not
Yarn, no datagen — see [Porting notes](#porting-notes-what-the-121-era-brief-got-wrong) if
you arrived here from a 1.21 tutorial, because most of what it would tell you is wrong now.

---

## The two rulings this mod is built around

**1. The palette is derived, not invented.** *"The haunted forest should be a shade or two
darker than regular forests. Just hue shift it."* (Jesse, 2026-08-18)

Every colour in the mod — biome fog, sky, water, grass, foliage, and every block texture —
is vanilla **forest** run through one transform in `tools/palette.js`:

| knob | value | effect |
|---|---|---|
| `HUE_SHIFT` | −35° | forest green → olive; sky blue → cold teal |
| `SATURATION` | ×0.55 | toward grey |
| `LIGHTNESS` | ×0.60 | "a shade or two darker" |
| `LIGHTNESS_SKY` / `SATURATION_SKY` | ×0.35 / ×0.22 | sky and fog start far lighter than leaves, so they take a harder pull |

The vanilla baselines are read out of the real 26.2 jars at generation time — grass and
foliage from `colormap/grass.png` and `foliage.png` sampled at forest's own climate point
(0.7 / 0.8), water and sky from `forest.json`, fog from the overworld dimension. Nothing is
transcribed, so nothing can drift.

To retune the whole mod's mood, change those numbers and re-run the generators. Run
`node tools/palette.js` on its own to print the before/after table.

**2. The dark.** *"At night it is pitch black just like the warden's territory, and even
torches do not penetrate. The only way to get normal light is a soul torch."*

Implemented in two halves, because a per-biome tint cannot tell a torch from a soul torch
and the rule turns on exactly that difference:

- **Look** — the biome's `visual/block_light_tint` is a cold blue (`#128de3`), so every
  flame in the wood reads wrong and only a soul flame looks like it belongs.
- **Gameplay** — `WaldschattenDarkness` applies the Warden's own `MobEffects.DARKNESS` to
  survival players in the biome after dark, *unless* a lit soul flame is within **9 blocks**.

Nine is forced by physics, not chosen: a soul torch emits light level **10** (a regular torch
emits 14), so its light is spent after ten blocks and a player standing further off receives
nothing from it. Nine is the largest radius for which "the flame lights you" and "the flame
counts" are the same statement — which is what lets the check answer in a **single block
lookup** for the common case of a player standing in the dark. An earlier version claimed 14
and was quietly inconsistent: the fast path admitted a soul torch at thirteen blocks only when
some *unrelated* light happened to be burning nearby.

Soul light sources are the `#waldschatten:soul_light` block tag, so a pack can add its own.
Creative and spectator players are exempt, and so is anyone more than 8 blocks below the
surface: this rule is about being out in the wood after dark, and a player down a cave under
the biome is in an ordinary dark hole that the ordinary dark already covers. The heightmap
used ignores leaves, so standing on the forest floor under a closed canopy still counts as
being at the surface — which is the entire population the rule exists for.

The class only ever lifts darkness it applied itself. `removeEffect` cannot ask where an
effect came from, so an unguarded call would cancel a *Warden's* darkness for anyone who
stepped near a soul torch.

---

## Proof, not assertion

`./gradlew runGametest` writes eight frames to `build/run-gametest/screenshots/`.
Per CLAUDE.md rule 9 the ship is not green until they have been **looked at**.

| frame | what it is evidence for |
|---|---|
| `blocks` | all 16 blocks in a 4×4 grid — no checkerboards, no black boxes |
| `canopy` | the stand from outside: an unbroken olive wall |
| `biome_day` | the treeline at eye level — sightlines, hanging bone chimes, drifting ash |
| `witch_hut` | the hut is a hut: stilts, steep roof, chimney, scarecrow, herb garden |
| `set_pieces` | all five vignettes read at a glance |
| `dark_night` | **near-black with an ordinary torch burning two blocks away** |
| `dark_soul_torch` | **the same spot, lit, cold-blue — only the soul torch changed** |
| `dark_again` | the torch removed; the dark returns, the ordinary torch still useless |

The battery also asserts six things a screenshot cannot:

- **`WALDSCHATTEN_STRUCTURES ... problems: none`** — all six structures resolve from the
  registry and every one is gated to this biome, and both structure sets exist. `/place`
  proves a template can be stamped down; it proves nothing about whether the game would ever
  choose to. That chain is four files agreeing on names that appear nowhere else, and when
  they disagree the structure simply never generates, with no error anywhere.
- **`WALDSCHATTEN_RECIPES ... resolves to 4 item(s); missing recipes: none`** — the wood set
  is craftable. A recipe whose ingredient tag does not resolve still loads; it just matches
  nothing, and the only symptom is a crafting grid that quietly refuses to work.

- **`WALDSCHATTEN_CHIMES ... 0 cannot survive where they hang`** — every bone chime the
  canopy hung can actually stay there. Worldgen writes blocks without neighbour updates, so a
  block that cannot survive still sits in the world looking correct until the first update
  reaches it; a screenshot taken at generation time cannot tell the difference and once
  passed a canopy full of doomed chimes. `canSurvive` can. `tools/lint-structures.js` is the
  static counterpart for the hand-built templates.

- **`WALDSCHATTEN_PLACEMENT ... contains waldschatten: true (56 biomes in the preset)`** —
  the mixin injection actually took. Asked of the **vanilla overworld preset**, not of the
  test world, because the preset is what the mixin appends to, so the answer holds for any
  world. (The gametest world offers exactly **1** biome — it is a single-biome world, so it
  exercises colour and rules and cannot show placement either way. Do not read its `false` as
  a broken injection; that mistake is one line above the assertion in the source.)
- **`WALDSCHATTEN_PROBE <scene> OK`** — the camera is genuinely inside the biome. Colour is
  the thing under test, so this may not be assumed. It has caught a whole run of
  vanilla-green frames more than once.
- **`WALDSCHATTEN_LIGHT`** — sweeps 225 floor positions under a closed canopy at noon and
  reports the distribution. **The figure is noisy and should be read as directional, not as a
  spec**: the test stand is only ~80 trees and the tree feature is random, so observed values
  range from **4% to 35% of the floor at light 0** (mean sky light 3.6–8.0), with a minimum of
  0 every single time. So the "danger in daylight" beat is real — there is always fully dark
  floor under a closed canopy — but *how much* of it varies more than one run can tell you. A
  trustworthy number needs a wider stand and several seeds; the honest summary today is
  "somewhere between a little and a third of it".

`node tools/contact-sheet.js` composites every texture scaled up on a checkerboard so the
placeholders can be judged as art and their alpha seen rather than guessed at.

---

## File map

```
tools/                       generators — EDIT THESE, never their output (rule 4)
  palette.js                 the one colour transform; run standalone to print the table
  png.js                     dependency-free PNG encode/decode, dimension-agnostic
  nbt.js                     NBT writer, copied from warfront (fixes go there and re-copy)
  structure-lib.js           the template builder used by gen-structures
  gen-biome.js               biome JSON: colours from palette, ore/carver steps from vanilla,
                             and refuses to write a feature order that would cycle
  gen-textures.js            16 placeholder block textures
  gen-assets.js              blockstates, models, item defs, loot tables, en_us
  gen-worldgen.js            configured + placed features, and cross-checks them
  gen-tags.js                block/item/biome tags
  lint-structures.js         every block in every template can survive where it is put
  gen-structures.js          NBT templates + pools + structures + structure sets
  contact-sheet.js           texture proof sheet

src/main/java/dev/lilkuzco/waldschatten/
  Waldschatten.java          entrypoint
  WaldschattenBlocks.java    16 blocks
  WaldschattenItems.java     one BlockItem per block, built FROM the block map
  WaldschattenTab.java       creative tab + assertComplete()
  WaldschattenDarkness.java  the dark, and the soul-torch exemption
  block/                     ThornVineBlock, BoneChimeBlock, GnarledRootsBlock, plants
  worldgen/                  biome key + the multi-noise climate niche
  mixin/                     OverworldBiomeBuilderMixin — the only way to place a biome

src/client/java/.../client/
  WaldschattenClient.java    deliberately empty; read it before adding anything
  WaldschattenRenderTest.java the battery
```

Regenerate everything:

```sh
node tools/gen-textures.js && node tools/gen-assets.js && node tools/gen-biome.js \
  && node tools/gen-worldgen.js && node tools/gen-tags.js && node tools/gen-structures.js
```

`gen-worldgen.js` and `gen-structures.js` **fail the command** if a feature the biome asks
for does not exist, if a placed feature points at nothing, if a feature is written but never
referenced, or if a template pool names an NBT file that is not there. The first run of
`gen-worldgen.js` caught a missing `forest_floor` reference before the game ever saw it.

---

## Where the biome actually generates

Fabric API has **no overworld biome placement API** — only `NetherBiomes` and `TheEndBiomes`.
So this is a mixin. Two things about it are not obvious and both were learned the hard way.

**1. Appending a parameter point can never work.** Multi-noise scores a sample as

```
fitness = SUM over parameters of (distance from the sample to that range)^2
```

`distance` is **zero** whenever the sample lies inside the range, and the search keeps the
first point with a *strictly* lower fitness — vanilla's, because vanilla is added first. A
point covering a sub-box of dark forest's climate scores exactly what dark forest scores,
ties, and loses every tie. **You cannot beat zero with zero.** The first version did this and
generated **0 of 4225 samples** while every registry assertion reported success.

So Waldschatten **claims** ground: every dark forest entry overlapping the **hilly erosion
band** (−0.375 … −0.2225) is re-pointed at Waldschatten. It inherits dark forest's whole
climate envelope and takes a defined, explainable piece of it — *the dark forest that grows on
broken ground*, which is the rolling terrain the design wanted anyway.

**2. The claim happens at the biome SOURCE, not at `OverworldBiomeBuilder`.** There are two
overworlds in play and only that layer sees both:

- On **vanilla** worldgen the list comes from `OverworldBiomeBuilder`.
- On the **empire server** it does not. Terralith ships a
  `multi_noise_biome_source_parameter_list/overworld.json` whose `lithostitched:biomes` field
  supplies the entries outright, so the vanilla builder's output is discarded — a claim made
  there vanishes with it. Measured, not assumed: a survey with Terralith and lithostitched
  installed reported 146 biomes and Waldschatten **absent**, while the claim had logged 72
  successful substitutions into a list nobody used.

Both routes end in a `MultiNoiseBiomeSource` resolving a `Climate.ParameterList`, so
`MultiNoiseBiomeSourceMixin` takes the list as it comes out of there (memoised — it is called
per biome cell). It does not care who wrote the list, needs no new dependency, and never
fights another mod over a file path. That last point matters: lithostitched has **no
biome-adding modifier**, so the only lithostitched-shaped answer would be shipping the same
`overworld.json` Terralith already owns, and one of the two would silently win.

`MultiNoiseBiomeSourceParameterListMixin` exists only to borrow the `HolderGetter<Biome>` on
its way past — that constructor is the one place in the chain handed a biome lookup, and
rewriting a list requires a `Holder` for the biome being written in.

### Measured, in real worlds

`WaldschattenWorldSurvey` runs in a **normal** world (the render battery's world offers exactly
one biome and can prove nothing about placement). `./gradlew runGametest` covers vanilla;
`./gradlew runTerralithSurvey` stages the real Terralith and lithostitched jars beside the mod
and asks again.

| | vanilla | Terralith + lithostitched |
|---|---|---|
| biomes in the world | 56 | 147 |
| **Waldschatten** | **0.95 – 1.16%** | **1.04 – 1.44%** |
| vanilla dark forest | 0.97 – 1.40% | 0.24 – 0.57% |
| vanilla forest | 11.3 – 11.8% | 4.2 – 5.5% |
| entries claimed | 102 of 7594 | 30 of 1710 |
| nearest, from origin | 614 – 1970 blocks | 251 – 804 blocks |
| nearest witch hut | 3.8k – 6.5k blocks | 3.8k blocks |

4225 samples on a 128-block grid over 8192×8192 at y=64, taken off the biome source with no
chunks generated. Roughly one percent of the world in both, comparable to vanilla dark forest,
findable on foot, with the hut a genuine expedition.

The survey points `runTerralithSurvey` at `~/Desktop/mc-server/server-mods-staging` by default;
set `WALDSCHATTEN_TERRALITH_MODS` to any folder holding the two jars. They are staged into the
run directory only — never a build dependency, never shipped.

### Tag reach — deliberate, not an oversight

Waldschatten is in `#minecraft:is_forest` and `#c:is_forest`. That is what lets other content
treat it as the forest it is, and it cuts both ways: other mods' forest features will
generate here too. Removing it scopes the biome tighter at the cost of making it invisible to
everything that asks "is this a forest". Note also that **Terralith folds its ~85 biomes into
the vanilla `minecraft:is_*` tags**, so tag-keyed rules inherit far more than you would
expect — resolve tags against the real jars before trusting a name.

---

## Placeholder textures — all 16 need real art

Every texture in `assets/waldschatten/textures/block/` is procedurally generated by
`tools/gen-textures.js`. They are readable, palette-correct, and correctly alpha'd, and they
are **all placeholders**. Replace the PNGs directly; nothing depends on them being generated
except the ability to re-tint the mod from `palette.js`.

`twisted_log` · `twisted_log_top` · `stripped_twisted_log` · `stripped_twisted_log_top` ·
`twisted_planks` · `twisted_leaves` · `twisted_sapling` · `gnarled_roots` ·
`witch_hazel_bush` · `nightshade_plant` · `mandrake_root` · `bone_chime` · `ashen_soil` ·
`mossy_cairn_stone` · `iron_lantern` · `thorn_vine`

Worth an artist's attention first: **`twisted_log`** (so near-black that its moss streaks are
invisible in game) and **`twisted_leaves`** (carries the whole biome — it is most of what the
player looks at).

**Rule for any replacement:** use alpha 0 or 255 and nothing between. 26.2 derives the render
layer from the sprite's own transparency (`ChunkSectionLayer.byTransparency`), so a partly
transparent pixel silently promotes the block to TRANSLUCENT and it sorts wrongly against
everything around it.

**No audio was authored.** The mood, additions and music all reuse vanilla sound events
(`ambient.cave`, `entity.creaking.sway`, `entity.creaking.twitch`, `entity.witch.ambient`,
`music.overworld.deep_dark`). Registering custom sound events without shipping `.ogg` files
would be a silent failure, so it was not done — see phase 2.

---

## Porting notes: what the 1.21-era brief got wrong

Recorded because every one of these cost a debugging cycle, and the next person arriving from
the same docs will hit them in the same order.

| 1.21 knowledge | 26.2 reality |
|---|---|
| Yarn mappings | Yarn ends at 1.21.11. Loom 1.17+ defaults to **Mojang official mappings**; there is no `mappings` line. |
| biome `effects` holds fog/sky/mood/music/particles | `effects` holds **only** water/foliage/dry-foliage/grass colour and the grass modifier. Everything else moved to an `attributes` map keyed by `minecraft:visual/*` and `minecraft:audio/*`. |
| `minecraft:random_patch` for ground scatter | **Does not exist.** Scatter is a `minecraft:simple_block` configured feature plus placement modifiers. |
| `minecraft:white_ash` particle | Does not exist. It is `minecraft:ash`. |
| `BlockRenderLayerMap` to mark cutout blocks | **Gone from Fabric API.** The layer is derived from the texture's alpha; there is nothing to register. |
| `LeavesBlock` is concrete | Abstract — use `UntintedParticleLeavesBlock` (fixed colour) or `TintedParticleLeavesBlock`. |
| `random_selector` names configured features | Its `default` and `feature` fields are **placed** features. Naming a configured feature fails at world load with `Unbound values in registry ... placed_feature`, which does not mention the selector that caused it. |
| gamerules `doDaylightCycle` / `doWeatherCycle` | `advance_time` / `advance_weather`. The old names parse as "Incorrect argument for command" and otherwise do nothing. |
| `ResourceKey.location()` | `ResourceKey.identifier()`. |
| `InsideBlockEffectApplier` in `world.level.block` | `net.minecraft.world.entity`. |

### The one that crashes world generation

**A biome's feature list is not just a list — its ORDER must agree with every other biome in
the game.** Minecraft demands a single globally consistent ordering of features per decoration
step and throws `IllegalStateException: Feature order cycle found` mid-chunk-generation when
two biomes disagree. It names the two biomes and leaves you to work out which features.

Waldschatten shipped with `patch_grass_forest` after the mushrooms while every vanilla forest
puts it before them. The result was a hard failure generating chunks next to
`old_growth_birch_forest`.

**A cycle needs two biomes to disagree, so a single-biome test world can never produce one** —
which is why this survived the entire render battery, every probe, and every assertion, and
only appeared the first time a real world was generated. `tools/gen-biome.js` now builds the
ordering graph from every vanilla biome plus ours and refuses to write a biome that would
cycle, naming the offending feature pair rather than the biomes.

Two more that are about commands rather than the API, both of which cost this battery a full
run of wrong screenshots:

- **`/fill` and `/fillbiome` refuse volumes above the `max_block_modifications` gamerule**
  (default 32768, about a 32-cube), and the refusal is chat, not a log line.
- **`/fill` and `/fillbiome` also refuse an area that is not fully loaded**, so a scene staged
  right after a long teleport silently gets neither floor nor biome.
- **`/place structure` projects to `WORLD_SURFACE_WG`**, the *worldgen* heightmap, which knows
  nothing about blocks a command placed afterwards — so a structure aimed at a staged platform
  lands on the original terrain, underneath it. The battery photographs with `/place template`
  and keeps `/place structure` only for its "Generated structure" log line.

---

## Phase 2 ideas

Deliberately **not** built, so nothing here was scope-crept in silently.

- **The Waldhexe.** A custom witch variant — model, AI, animations, sounds, loot. A large
  piece of work on its own; the hut currently spawns a vanilla `minecraft:witch` via
  `spawn_overrides`, exactly as vanilla's swamp hut does.
- **Custom audio.** Distant crows, creaking timber, a far-off cackle, and an original music
  track. Needs real `.ogg` assets; the sound *events* should not be registered until the files
  exist, or every one of them is a silent failure.
- **Brewing.** `nightshade_plant`, `mandrake_root` and `witch_hazel_bush` are currently
  decorative and loot. They are named to be reagents and the hut already ships a brewing stand.
- **Terralith placement.** The decision deferred above: lithostitched or TerraBlender, plus a
  `mods.json` entry.
- **Thatch.** The hut's roof approximates thatch with dark oak stairs. A real thatch block
  would be one texture and one entry in `gen-assets.js`.
- **A second spooky biome.** `#waldschatten:is_spooky` exists so a sibling inherits the
  darkness rule and any future content for free.
- **Canopy density tuning.** Floor-at-light-0 measures anywhere from 4% to 35% run to run,
  which is too noisy to tune against. Widen the survey stand and average several seeds first;
  then raising the vegetation `count` above 16 is the dial.
- **Rarity survey.** Point `empire-worldgen/tools/survey/` at a generated world and count how
  often the biome and the hut actually turn up.
