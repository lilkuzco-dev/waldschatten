# Waldschatten

*Forest Shadow.* A Brothers Grimm reading of the Schwarzwald for **Minecraft 26.2 / Fabric**.

The canopy closes over your head, the floor fights back, and after dark the wood takes your
torch away from you. A quarter of the forest floor sits at light level 0 at noon, so the
things that hunt you do not wait for evening.

## What it adds

- **The biome.** Temperate, wet, and mostly flat beneath an oppressive canopy. Every colour is
  vanilla forest hue-shifted a shade or two darker, so the haunting comes from the darkness
  and twisted silhouettes rather than mountain terrain.
- **16 blocks** — a full twisted-wood set that strips, burns and crafts like any other wood;
  thorn vines that slow and scratch anything shoving through them; gnarled floor roots,
  nightshade, mandrake, witch hazel, bone chimes that hang from the canopy, ashen soil, cairn
  stone, and an iron lantern that is the only warm light in the biome.
- **The dark.** At night Waldschatten is pitch black the way the Warden's territory is, and an
  ordinary torch barely dents it. A **soul torch** is the one light that behaves normally, out
  to nine blocks — as far as a soul flame's own light actually carries. Go down a cave and
  the rule lets you be; it is about the wood, not the dark in general.
- **A witch hut in every forest patch** — a crooked cottage on uneven stilts with a steep roof,
  a smoking chimney, a scarecrow, a fenced herb garden, a cauldron in the yard, and somebody
  already home.
- **Five set-pieces** scattered through the wood: a gallows clearing, a standing stone circle,
  a shrine at the foot of an old tree, a sunken cottage full of cobwebs, and a fairy ring that
  glows after dark.

## Building

Requires JDK 25.

```sh
./gradlew build          # the shipping jar lands in build/libs/
./gradlew runGametest    # the client render battery
```

`runGametest` writes eight screenshots to `build/run-gametest/screenshots/`. **Read them.**
This mod is almost entirely a thing to look at, and no server-side check can tell you whether
any of it is drawn — see CLAUDE.md rule 9.

## Working on it

Almost every resource in this mod is generated. **Edit the generator in `tools/`, never its
output**, or your change is destroyed on the next regeneration:

```sh
node tools/palette.js        # print the colour transform's before/after table
node tools/gen-textures.js   # block textures
node tools/gen-assets.js     # blockstates, models, item defs, loot tables, en_us
node tools/gen-biome.js      # the biome
node tools/gen-worldgen.js   # features (self-checking)
node tools/gen-tags.js       # tags
node tools/gen-structures.js # NBT templates, pools, structures, sets (self-checking)
node tools/lint-structures.js # every block in every template can survive where it is put
node tools/contact-sheet.js  # texture proof sheet -> build/texture-contact-sheet.png
```

All 16 textures are **placeholders** awaiting real art.

Two survey tasks answer "does this biome actually turn up":

```sh
./gradlew runGametest           # includes a survey of a normal vanilla world
./gradlew runTerralithSurvey    # the same survey with real Terralith + lithostitched loaded
```

The release survey checks six reproducible seeds on vanilla and on the exact live terrain
stack. Version 0.1.1 passed all 12 worlds: Waldschatten was 0–607 blocks from spawn on vanilla
and 32–289 blocks away with Terralith, lithostitched, and Empire Worldgen. It also fails if a
patch-anchored witch hut cannot be located or if a connected live-stack patch has more than
24 blocks of relief.

See [WALDSCHATTEN.md](WALDSCHATTEN.md) for the design rulings, the file map, the phase 2 list,
and a table of everything 1.21-era worldgen knowledge gets wrong on 26.2.

## Licence

MIT. No Mojang or third-party assets are redistributed; every texture is generated from
scratch by `tools/gen-textures.js`.
