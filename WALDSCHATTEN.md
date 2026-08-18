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
  survival players in the biome after dark, *unless* a lit soul flame is within 14 blocks —
  a regular torch's light level, so a soul torch lights the area a torch would have lit in a
  forest that was not trying to kill you.

Soul light sources are the `#waldschatten:soul_light` block tag, so a pack can add its own.
Creative and spectator players are exempt.

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

The battery also asserts three things a screenshot cannot:

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
  reports the distribution. Trees are placed randomly, so the figure moves between runs:
  observed **15–33% of the floor at light 0**, mean sky light 3.6–6.5, with min 0 every time.
  That is the "danger in daylight" beat, measured rather than claimed — and the spread is
  itself the answer to "how dense should the canopy be", which is now a question with a
  number attached instead of a vibe.

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
  gen-biome.js               biome JSON: colours from palette, ore/carver steps from vanilla
  gen-textures.js            16 placeholder block textures
  gen-assets.js              blockstates, models, item defs, loot tables, en_us
  gen-worldgen.js            configured + placed features, and cross-checks them
  gen-tags.js                block/item/biome tags
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

Fabric API has **no overworld biome placement API** — only `NetherBiomes` and
`TheEndBiomes` (verified against fabric-biome-api-v1 18.0.6). The overworld's layout is
built in code by `OverworldBiomeBuilder.addBiomes`, so `OverworldBiomeBuilderMixin` appends
one multi-noise point at the tail. Adding a point can never remove a vanilla biome; the
sampler picks the nearest point, so the most Waldschatten can do is win ground inside its
own niche.

The niche is dark forest's climate band (temperate, wet) narrowed to the hilly,
low-weirdness slice — the wood next door to the dark forest, on the rolling ground the brief
asks for:

| parameter | span |
|---|---|
| temperature | 0.2 … 0.55 |
| humidity | 0.3 … 1.0 |
| continentalness | 0.03 … 1.0 |
| erosion | −0.375 … −0.2225 |
| weirdness | −0.15 … 0.15 |
| depth / offset | 0 / 0 |

### ⚠ It is a no-op on a Terralith world, silently

Terralith replaces the overworld biome source outright, so a Terralith world never asks the
vanilla preset what biomes exist and never sees these entries. **No crash, no error, no
biome.** The empire server runs Terralith (CLAUDE.md rule 2), so shipping there is a
separate decision needing a lithostitched or TerraBlender route — and lithostitched is not
currently in `mods.json`. `WaldschattenWorldgen.logPlacement` says at startup what was
injected, so a missing biome is a discrepancy between the log and the world rather than a
mystery.

### Tuning the rarity

The climate numbers are a starting point, not a measurement. Rarity is an empirical question
and `empire-worldgen/tools/survey/` already exists for exactly this kind of counting.
`offset` is the rarity dial: 0 means "win this niche outright", higher means "only when
nothing else is close".

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
- **Canopy density tuning.** 15–33% of the floor at light 0 is a real "danger in daylight"
  beat; whether it should be that or half the wood is a design call the measurement now makes
  answerable. Raising the vegetation `count` above 16 is the dial.
- **Rarity survey.** Point `empire-worldgen/tools/survey/` at a generated world and count how
  often the biome and the hut actually turn up.
