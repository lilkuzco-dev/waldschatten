package dev.lilkuzco.waldschatten.client;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * The render battery.
 *
 * <p>CLAUDE.md rule 9: this mod is almost entirely a thing to look at, so no amount of
 * green server-side checking says anything about whether it works. Two cosmos releases
 * passed every other gate while visually broken — one crashed the render thread, one
 * shipped an invisible rocket — and the only thing that would have caught either is a
 * screenshot somebody looked at.
 *
 * <p>What each frame is evidence <em>for</em>:
 * <ul>
 *   <li>{@code blocks} — every block has a model and a texture. A checkerboard or a black
 *       box here is the failure this battery exists to catch.</li>
 *   <li>{@code canopy} — the stand from outside: the canopy closes over the floor.</li>
 *   <li>{@code biome_day} — fog, sky, grass and foliage colours, at eye level, at noon.</li>
 *   <li>{@code witch_hut} — the hut is a hut.</li>
 *   <li>{@code set_pieces} — the five vignettes read at a glance.</li>
 *   <li>{@code dark_night} / {@code dark_soul_torch} / {@code dark_again} — the ruling of
 *       2026-08-18. These three frames are the whole proof: black with an ordinary torch
 *       burning, lit near a soul torch, black again out of its reach. If the first two
 *       look alike, the rule is not working, whatever the logs say.</li>
 * </ul>
 *
 * <h2>Two traps this battery has already fallen into</h2>
 * <ol>
 *   <li><b>fillbiome silently refuses large volumes.</b> Its ceiling is the
 *       {@code max_block_modifications} gamerule, default 32768 — roughly a 32-cube. Every
 *       scene here is bigger, the refusal is chat the test never reads, and the first run
 *       photographed six frames of vanilla-green grass and called them Waldschatten.
 *       Hence the raised gamerule, and hence {@link #probeBiome}: colour is the thing
 *       under test, so "am I in the biome" may not be an assumption.</li>
 *   <li><b>Structures ignore a floor built by fill.</b> {@code /place structure} projects
 *       to {@code WORLD_SURFACE_WG}, the <em>worldgen</em> heightmap, which is computed at
 *       chunk generation and knows nothing about blocks a command placed afterwards. So a
 *       structure aimed at a staged platform lands on the original terrain, under it. The
 *       photos therefore use {@code /place template}, which puts the piece exactly where
 *       it is told. {@code /place structure} is still run for the hut, because its log
 *       line is the evidence that the jigsaw actually resolves.</li>
 * </ol>
 *
 * <p>Runs only under {@code ./gradlew runGametest}; normal clients never invoke it.
 */
public class WaldschattenRenderTest implements FabricClientGameTest {

	private static final String BIOME = "waldschatten:waldschatten";

	/** Every block, in creative-tab order, laid out 4x4 so each is big enough to judge. */
	private static final String[] BLOCKS = {
		"twisted_log", "stripped_twisted_log", "twisted_wood", "stripped_twisted_wood",
		"twisted_planks", "twisted_leaves", "twisted_sapling", "gnarled_roots",
		"witch_hazel_bush", "nightshade_plant", "mandrake_root", "bone_chime",
		"ashen_soil", "mossy_cairn_stone", "iron_lantern", "thorn_vine",
	};

	@Override
	public void runTest(ClientGameTestContext context) {
		// Render distance must be set BEFORE the world is created: the integrated server
		// snapshots view distance at connect, and setting it mid-session never widens the
		// chunks actually served — the camera just looks at fog.
		context.runOnClient(client -> client.options.renderDistance().set(16));

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(80);
			TestServerContext server = world.getServer();
			// 26.x renamed the gamerules snake_case and reworded them: doDaylightCycle is
			// `advance_time`, doWeatherCycle is `advance_weather`. The old names parse as
			// "Incorrect argument for command" and otherwise do nothing, which is how the
			// first run of this battery spent its night frames in daylight.
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("gamerule max_block_modifications 4000000");
			server.runCommand("difficulty peaceful");
			server.runCommand("gamemode creative @p");
			server.runCommand("time set noon");
			server.runCommand("kill @e[type=!minecraft:player]");
			context.waitTicks(20);

			// The acceptance question nothing else here answers: is the biome actually IN
			// this world's generator? Every scene below paints it on with /fillbiome, which
			// proves its colours and its rules but says nothing about whether the
			// multi-noise injection landed — and that injection is a silent no-op under any
			// mod that replaces the biome source (Terralith, most of all).
			//
			// Asked of the live biome source rather than with `/locate biome`: locate
			// depends on a finite search radius finding a narrow niche, so a miss there
			// would be ambiguous, and its console feedback does not reach the log anyway.
			// possibleBiomes() is a straight yes or no.
			server.runOnServer(mcServer -> {
				// The assertion that matters, and it is asked of the vanilla overworld
				// PRESET rather than of this world: the preset is what
				// OverworldBiomeBuilderMixin appends to, so this is a direct yes/no on
				// whether the injection took, in any world, forever.
				var preset = net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD;
				long total = preset.usedBiomes().count();
				boolean injected = preset.usedBiomes()
						.anyMatch(key -> key.equals(dev.lilkuzco.waldschatten.worldgen.WaldschattenWorldgen.WALDSCHATTEN));
				dev.lilkuzco.waldschatten.Waldschatten.LOGGER.info(
						"WALDSCHATTEN_PLACEMENT vanilla overworld preset contains waldschatten: {} "
								+ "({} biomes in the preset)", injected, total);

				// And for context, this world's own biome source. The gametest world is a
				// SINGLE-BIOME world (possibleBiomes() == 1), so it cannot show placement
				// either way — which is exactly why the real assertion is the one above.
				// Do not read a `false` here as a broken injection.
				net.minecraft.world.level.biome.BiomeSource source =
						mcServer.overworld().getChunkSource().getGenerator().getBiomeSource();
				dev.lilkuzco.waldschatten.Waldschatten.LOGGER.info(
						"WALDSCHATTEN_PLACEMENT (context) this gametest world offers {} biome(s), so it "
								+ "exercises colour and rules, not placement", source.possibleBiomes().size());

				// Structure wiring. /place proves a template can be stamped down; it proves
				// nothing about whether the game would ever choose to. That chain is four
				// files agreeing on names that appear nowhere else — structure -> biome tag
				// -> structure_set -> pool — and when they disagree the structure simply
				// never generates, with no error anywhere. So resolve each one from the
				// registry and check it is actually gated to this biome.
				var registries = mcServer.registryAccess();
				var biome = registries.lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
						.getOrThrow(dev.lilkuzco.waldschatten.worldgen.WaldschattenWorldgen.WALDSCHATTEN);
				var structures = registries.lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
				var structureSets = registries.lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET);

				java.util.List<String> problems = new java.util.ArrayList<>();
				for (String name : new String[] { "witch_hut", "gallows", "stone_circle", "grimm_shrine", "sunken_cottage", "fairy_ring" }) {
					var key = net.minecraft.resources.ResourceKey.create(
							net.minecraft.core.registries.Registries.STRUCTURE, dev.lilkuzco.waldschatten.Waldschatten.id(name));
					var holder = structures.get(key);
					if (holder.isEmpty()) {
						problems.add(name + " is not registered");
					} else if (!holder.get().value().biomes().contains(biome)) {
						problems.add(name + " is not gated to the waldschatten biome");
					}
				}
				int sets = 0;
				for (String name : new String[] { "witch_huts", "set_pieces" }) {
					var key = net.minecraft.resources.ResourceKey.create(
							net.minecraft.core.registries.Registries.STRUCTURE_SET, dev.lilkuzco.waldschatten.Waldschatten.id(name));
					if (structureSets.get(key).isEmpty()) {
						problems.add("structure set " + name + " is not registered");
					} else {
						sets++;
					}
				}
				dev.lilkuzco.waldschatten.Waldschatten.LOGGER.info(
						"WALDSCHATTEN_STRUCTURES 6 structures + {} sets wired to the biome; problems: {}",
						sets, problems.isEmpty() ? "none" : problems);

				// The wood set has to be craftable, and the way that breaks is quiet: a
				// recipe whose ingredient tag does not resolve still loads, it just matches
				// nothing, and the only symptom is a crafting grid that refuses to work.
				// So check the tag has members AND that each recipe is in the manager.
				var logTag = registries.lookupOrThrow(net.minecraft.core.registries.Registries.ITEM)
						.get(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
								dev.lilkuzco.waldschatten.Waldschatten.id("twisted_logs")));
				int tagged = logTag.map(holders -> holders.size()).orElse(0);
				java.util.List<String> missingRecipes = new java.util.ArrayList<>();
				for (String name : new String[] { "twisted_planks", "twisted_wood", "stripped_twisted_wood" }) {
					var key = net.minecraft.resources.ResourceKey.create(
							net.minecraft.core.registries.Registries.RECIPE, dev.lilkuzco.waldschatten.Waldschatten.id(name));
					if (mcServer.getRecipeManager().byKey(key).isEmpty()) {
						missingRecipes.add(name);
					}
				}
				dev.lilkuzco.waldschatten.Waldschatten.LOGGER.info(
						"WALDSCHATTEN_RECIPES #waldschatten:twisted_logs resolves to {} item(s); missing recipes: {}",
						tagged, missingRecipes.isEmpty() ? "none" : missingRecipes);
			});
			context.waitTicks(10);

			blockBoard(context, server);
			canopy(context, server);
			witchHut(context, server);
			setPieces(context, server);
			theDark(context, server);
		}
	}

	// ------------------------------------------------------------------
	/**
	 * Move somewhere fresh and build a flat stage <em>under wherever the player already
	 * is</em>.
	 *
	 * <p>Building the floor relative to the player, rather than moving the player down to
	 * a floor, is what keeps this deterministic. A player leaving spectator keeps flying,
	 * so their height drifts upward scene by scene; every earlier version of this battery
	 * eventually photographed the ground from nine blocks too high without noticing.
	 */
	private static void stage(ClientGameTestContext context, TestServerContext server, int dx, int dz, int r, boolean paintBiome) {
		server.runCommand("gamemode creative @p");
		server.runCommand("execute at @p run tp @p ~" + dx + " ~ ~" + dz);
		// Long enough for the chunks around the new position to load. `/fill` and
		// `/fillbiome` both refuse an area that is not fully loaded, and the refusal is
		// chat rather than a log line — the set-piece scene lost BOTH its floor and its
		// biome to a 5-tick wait and the only visible symptom was one NOT_IN_BIOME probe.
		context.waitTicks(40);
		server.runCommand("execute at @p run fill ~-" + r + " ~-1 ~-" + r + " ~" + r + " ~-1 ~" + r + " minecraft:grass_block");
		server.runCommand("execute at @p run fill ~-" + r + " ~ ~-" + r + " ~" + r + " ~30 ~" + r + " minecraft:air");
		if (paintBiome) {
			// Painted from well BELOW the floor, not from it. Biome lookups are fuzzy —
			// BiomeManager offsets the sample by a couple of blocks — so a player standing
			// on the bottom edge of the painted volume reads the biome underneath it about
			// as often as not. That is precisely what NOT_IN_BIOME caught on the dark
			// scene while the identically-built scenes above it happened to pass.
			server.runCommand("execute at @p run fillbiome ~-" + r + " ~-8 ~-" + r + " ~" + r + " ~30 ~" + r + " " + BIOME);
		}
		context.waitTicks(15);
	}

	/**
	 * Assert, out loud and in the log, that the camera is standing in the biome.
	 *
	 * <p>{@code at @p} is not optional: runCommand executes from the server console at
	 * world spawn, so a bare {@code ~ ~ ~} probes the spawn chunk and cheerfully reports
	 * on a place the camera is nowhere near.
	 */
	private static void probeBiome(ClientGameTestContext context, TestServerContext server, String where) {
		server.runCommand("execute at @p if biome ~ ~ ~ " + BIOME + " run say WALDSCHATTEN_PROBE " + where + " OK");
		server.runCommand("execute at @p unless biome ~ ~ ~ " + BIOME + " run say WALDSCHATTEN_PROBE " + where + " NOT_IN_BIOME");
		context.waitTicks(5);
	}

	// ------------------------------------------------------------------
	private static void blockBoard(ClientGameTestContext context, TestServerContext server) {
		stage(context, server, 0, 0, 24, false);

		// A 4x4 grid rather than a 16-long row: a row that fits in frame puts the camera
		// far enough back that each block is twenty pixels of mush, which is not a frame
		// anyone can spot a bad texture in.
		for (int i = 0; i < BLOCKS.length; i++) {
			int x = (i % 4) * 3;
			int z = (i / 4) * 3;
			server.runCommand("execute at @p run setblock ~" + x + " ~-1 ~" + z + " minecraft:dirt");
			if (BLOCKS[i].equals("bone_chime")) {
				// The chime hangs. Placed on the ground with nothing above it it pops off
				// at once, leaving a gap that looks exactly like a missing model — the
				// failure this frame exists to detect, faked by the test itself.
				server.runCommand("execute at @p run setblock ~" + x + " ~2 ~" + z + " minecraft:dark_oak_planks");
				server.runCommand("execute at @p run setblock ~" + x + " ~1 ~" + z + " waldschatten:bone_chime");
			} else {
				server.runCommand("execute at @p run setblock ~" + x + " ~ ~" + z + " waldschatten:" + BLOCKS[i]);
			}
		}
		context.waitTicks(20);

		// Spectator: a creative player falls during the chunk-render wait and the shot
		// ends up taken from the floor.
		server.runCommand("gamemode spectator @p");
		server.runCommand("execute at @p run tp @p ~4 ~5 ~-6 0 30");
		context.waitTicks(40);
		context.takeScreenshot("waldschatten_blocks");
	}

	// ------------------------------------------------------------------
	private static void canopy(ClientGameTestContext context, TestServerContext server) {
		stage(context, server, 200, 0, 44, true);
		probeBiome(context, server, "canopy");

		// A stand, not a specimen: the claustrophobia claim is about canopies overlapping,
		// and one tree cannot show that.
		// A 4-block grid, not a 5: the biome places 16 trees per chunk, which is one per
		// ~16 square blocks, so 4 is what the real wood is like. Sparser than that and the
		// battery is photographing an orchard and measuring an orchard's light.
		for (int dx = -16; dx <= 16; dx += 4) {
			for (int dz = -16; dz <= 16; dz += 4) {
				server.runCommand("execute at @p run place feature waldschatten:twisted_tree ~" + dx + " ~ ~" + dz);
			}
		}
		context.waitTicks(40);

		// Every chime the canopy hung must be able to SURVIVE where it hangs, not merely be
		// present. Worldgen writes blocks without neighbour updates, so a block that cannot
		// survive still sits there looking correct until the first update reaches it — which
		// is exactly how a screenshot of doomed bone chimes once passed this battery. The
		// photograph cannot tell the difference; canSurvive can.
		server.runOnServer(mcServer -> {
			net.minecraft.server.level.ServerLevel level = mcServer.overworld();
			if (level.players().isEmpty()) {
				return;
			}
			net.minecraft.core.BlockPos origin = level.players().get(0).blockPosition();
			int found = 0;
			int doomed = 0;
			net.minecraft.core.BlockPos.MutableBlockPos cursor = new net.minecraft.core.BlockPos.MutableBlockPos();
			for (int dx = -24; dx <= 24; dx++) {
				for (int dz = -24; dz <= 24; dz++) {
					for (int dy = -4; dy <= 30; dy++) {
						cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
						net.minecraft.world.level.block.state.BlockState state = level.getBlockState(cursor);
						if (state.is(dev.lilkuzco.waldschatten.WaldschattenBlocks.BONE_CHIME)) {
							found++;
							if (!state.canSurvive(level, cursor)) {
								doomed++;
							}
						}
					}
				}
			}
			dev.lilkuzco.waldschatten.Waldschatten.LOGGER.info(
					"WALDSCHATTEN_CHIMES hung by the canopy: {} found, {} cannot survive where they hang",
					found, doomed);
		});
		context.waitTicks(10);

		// From outside and above the stand — far enough out that the canopy is a silhouette
		// rather than the inside of a leaf block.
		server.runCommand("gamemode spectator @p");
		server.runCommand("execute at @p run tp @p ~ ~12 ~-38 0 14");
		context.waitTicks(60);
		context.takeScreenshot("waldschatten_canopy");

		// At eye level just outside the treeline, looking in: this is the frame that shows
		// how far you can actually see. Deliberately OUTSIDE the stand — an earlier version
		// aimed for the middle of the wood and put the camera inside a trunk, which
		// photographs as a completely black frame and looks like a rendering bug.
		// z_c-30: the stand reaches +-16 and its canopy spreads a few blocks past that, so
		// anything nearer than this photographs the inside of a leaf block.
		server.runCommand("execute at @p run tp @p ~ ~-10 ~8 0 0");
		context.waitTicks(50);
		context.takeScreenshot("waldschatten_biome_day");

		// The "danger in daylight" beat is a claim about light LEVELS, and a screenshot
		// cannot settle it — a dim frame and a frame at spawn-threshold darkness look the
		// same. So measure it: stand under the closed canopy at noon and read the light.
		// Overworld hostiles need the raw brightness at their feet to reach 0, so sky light
		// under the canopy is the number that decides whether this biome keeps its promise.
		// INTO the stand, not further out of it — offset off the 5-block planting grid so
		// the reading is taken between trunks rather than inside one.
		server.runCommand("execute at @p run tp @p ~2 ~ ~22");
		context.waitTicks(30);
		context.runOnClient(client -> {
			if (client.level == null || client.player == null) {
				return;
			}
			// One sample proves nothing — a canopy is patchy by design, and the beat is
			// about the dark patches, not the average. So sweep the floor under the stand
			// and report the distribution: what matters is how much of it reaches 0.
			net.minecraft.core.BlockPos origin = client.player.blockPosition();
			int min = 15, max = 0, total = 0, samples = 0, atZero = 0;
			for (int dx = -14; dx <= 14; dx += 2) {
				for (int dz = -14; dz <= 14; dz += 2) {
					net.minecraft.core.BlockPos pos = origin.offset(dx, 0, dz);
					int sky = client.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos);
					min = Math.min(min, sky);
					max = Math.max(max, sky);
					total += sky;
					samples++;
					if (sky == 0) {
						atZero++;
					}
				}
			}
			dev.lilkuzco.waldschatten.Waldschatten.LOGGER.info(
					"WALDSCHATTEN_LIGHT canopy floor at noon over {} samples: sky min={} max={} mean={} "
							+ "-- {}% of the floor is at 0 (the level overworld hostiles need)",
					samples, min, max, String.format("%.1f", (double) total / samples),
					String.format("%.0f", 100.0 * atZero / samples));
		});
		context.waitTicks(10);
	}

	// ------------------------------------------------------------------
	private static void witchHut(ClientGameTestContext context, TestServerContext server) {
		stage(context, server, 200, 0, 30, true);
		probeBiome(context, server, "hut");

		// Evidence that the jigsaw resolves: this lands on natural terrain somewhere near
		// by, and its "Generated structure" log line is the thing being asserted. It is
		// not what gets photographed, because it does not land on the staged floor.
		server.runCommand("execute at @p run place structure waldschatten:witch_hut ~ ~ ~40");

		// The photograph. `place template` puts the piece exactly where it is told.
		server.runCommand("execute at @p run place template waldschatten:witch_hut/witch_hut ~-6 ~ ~-6");
		context.waitTicks(20);
		// A raw template keeps its jigsaw blocks — natural generation swaps in final_state,
		// `place template` does not — so clear the two the hut carries before the shot.
		server.runCommand("execute at @p run setblock ~6 ~ ~0 minecraft:air");
		server.runCommand("execute at @p run setblock ~-6 ~ ~4 minecraft:air");
		context.waitTicks(20);

		server.runCommand("gamemode spectator @p");
		// From the FRONT (yaw 180), which is where the door, the stone path, the lantern on
		// its path stone and the chime under the eave all are. Shooting the back wall
		// photographs none of them.
		server.runCommand("execute at @p run tp @p ~1 ~7 ~19 180 20");
		context.waitTicks(60);
		context.takeScreenshot("waldschatten_witch_hut");
	}

	// ------------------------------------------------------------------
	private static void setPieces(ClientGameTestContext context, TestServerContext server) {
		stage(context, server, 200, 0, 40, true);
		probeBiome(context, server, "set_pieces");

		String[] pieces = { "gallows", "stone_circle", "grimm_shrine", "sunken_cottage", "fairy_ring" };
		for (int i = 0; i < pieces.length; i++) {
			server.runCommand("execute at @p run place template waldschatten:set_piece/" + pieces[i]
					+ " ~" + (i * 13 - 30) + " ~ ~0");
		}
		context.waitTicks(30);

		server.runCommand("gamemode spectator @p");
		server.runCommand("execute at @p run tp @p ~2 ~13 ~-24 0 28");
		context.waitTicks(60);
		context.takeScreenshot("waldschatten_set_pieces");
	}

	// ------------------------------------------------------------------
	/**
	 * The dark, and the one way out of it.
	 *
	 * <p>Survival, not creative: WaldschattenDarkness deliberately exempts creative and
	 * spectator players, so a battery that stayed in creative would photograph two
	 * identical lit frames and call the rule proven.
	 */
	private static void theDark(ClientGameTestContext context, TestServerContext server) {
		stage(context, server, 200, 0, 20, true);
		probeBiome(context, server, "dark");

		server.runCommand("time set midnight");
		server.runCommand("gamemode survival @p");
		context.waitTicks(20);

		// An ordinary torch, close enough that its light unambiguously reaches the player.
		// This is the control: the frame must show a torch that is plainly not helping.
		server.runCommand("execute at @p run setblock ~2 ~ ~2 minecraft:torch");
		context.waitTicks(120);
		context.takeScreenshot("waldschatten_dark_night");

		// Now a soul torch inside the radius. Same place, same time, the ordinary torch
		// still burning — the only change is the soul flame.
		server.runCommand("execute at @p run setblock ~-2 ~ ~2 minecraft:soul_torch");
		context.waitTicks(80);
		context.takeScreenshot("waldschatten_dark_soul_torch");

		// And take the soul torch away again, to show the dark comes back rather than
		// having been spent. Done in place: an earlier version staged a whole new scene
		// sixty blocks away for this and spent two runs losing its floor and its biome to
		// the chunk-load race for no gain — the claim is about the torch, so the torch is
		// the only thing that should change.
		server.runCommand("execute at @p run setblock ~-2 ~ ~2 minecraft:air");
		probeBiome(context, server, "dark_again");
		context.waitTicks(120);
		context.takeScreenshot("waldschatten_dark_again");

		server.runCommand("gamemode creative @p");
	}
}
