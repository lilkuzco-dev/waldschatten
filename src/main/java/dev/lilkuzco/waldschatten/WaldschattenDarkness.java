package dev.lilkuzco.waldschatten;

import dev.lilkuzco.waldschatten.worldgen.WaldschattenWorldgen;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The dark.
 *
 * <p>RULING (Jesse, 2026-08-18): after sunset Waldschatten is pitch black the way the
 * Warden's territory is, an ordinary torch barely dents it, and a soul torch is the one
 * light that behaves normally.
 *
 * <p>It is implemented with the Warden's own effect, because "just like the warden's
 * territory" is a promise about a feeling the player already has a memory of, and
 * {@link MobEffects#DARKNESS} is the thing that memory is made of. What this class adds is
 * the exemption: stand inside {@link #SOUL_LIGHT_RADIUS} blocks of a soul flame and the
 * darkness lifts. The radius is a regular torch's light level, so a soul torch lights the
 * area a torch would have lit in a forest that was not trying to kill you.
 *
 * <p>The cold cast on every flame is the other half of the rule and lives in the biome's
 * {@code visual/block_light_tint} — see {@code tools/gen-biome.js}. A tint cannot tell a
 * torch from a soul torch, and the rule turns on exactly that difference, so the gameplay
 * half has to be here.
 *
 * <h2>Why this hangs off the server tick</h2>
 * Per CLAUDE.md rule 7 anything unattended belongs on the server tick. This one is
 * player-driven and could not run unattended by definition, but it stays on
 * {@code END_SERVER_TICK} anyway: it iterates the player list, which is the level's
 * business rather than any one entity's, and it costs nothing when nobody is in the wood.
 */
public final class WaldschattenDarkness {

	/**
	 * How far a soul flame holds the dark back, in blocks.
	 *
	 * <p>Nine, not the fourteen this once claimed, and the number is forced by physics
	 * rather than chosen. A soul torch emits light level <b>10</b> (a regular torch emits
	 * 14), so its light is spent after ten blocks of travel and a player standing further
	 * off receives nothing from it at all. The old value promised a torch's reach from an
	 * emitter that cannot produce it, and the cheap test below — "no block light means no
	 * soul light" — was justified by an invariant that was simply false past nine blocks.
	 * The symptom was worse than a short radius: any unrelated light source flipped the
	 * test, so the same soul torch was found at thirteen blocks when an ordinary torch
	 * happened to be burning nearby and missed when it was not.
	 *
	 * <p>Nine is the largest radius for which "the flame lights you" and "the flame counts"
	 * are the same statement, which is what makes the one-lookup fast path honest — and the
	 * fast path is the whole performance story, since it answers for every player standing
	 * in the dark, which is most of them, most of the time.
	 */
	public static final int SOUL_LIGHT_RADIUS = 9;

	private static final int SCAN_INTERVAL_TICKS = 10;
	/**
	 * Comfortably longer than the effect's own 22-tick blend, so a player who is genuinely
	 * in the dark never sees it begin to fade before it is renewed.
	 */
	private static final int DARKNESS_DURATION_TICKS = 60;
	/** Renew only once it drops this low — three times fewer effect packets than renewing blind. */
	private static final int REFRESH_BELOW_TICKS = 35;
	/** How often the *expensive* search may run for a player who has found no soul light. */
	private static final int FULL_SCAN_INTERVAL_TICKS = 40;

	/**
	 * Every offset inside the radius, ordered nearest first, packed as x,y,z triples.
	 *
	 * <p>A sphere rather than the old cylinder, because the fast path admits any source
	 * within nine blocks in <em>any</em> direction and a search that cannot see everything
	 * the gate admits is the same inconsistency in a new place. It is still barely a third
	 * of the volume the old 14-by-8 cylinder swept.
	 *
	 * <p>Nearest-first ordering is what makes the miss cost theoretical: a player who has
	 * lit a torch to stand by is answered within a few dozen lookups, not three thousand.
	 * Built once at class load, then read allocation-free forever.
	 */
	private static final int[] OFFSETS = buildOffsets();

	private static int[] buildOffsets() {
		int r = SOUL_LIGHT_RADIUS;
		List<int[]> found = new ArrayList<>();
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					int d2 = dx * dx + dy * dy + dz * dz;
					if (d2 <= r * r) {
						found.add(new int[] { dx, dy, dz, d2 });
					}
				}
			}
		}
		found.sort(Comparator.comparingInt(o -> o[3]));
		int[] packed = new int[found.size() * 3];
		for (int i = 0; i < found.size(); i++) {
			packed[i * 3] = found.get(i)[0];
			packed[i * 3 + 1] = found.get(i)[1];
			packed[i * 3 + 2] = found.get(i)[2];
		}
		return packed;
	}

	/** Data-driven so a pack can add its own soul flames without touching this class. */
	public static final TagKey<Block> SOUL_LIGHT =
			TagKey.create(Registries.BLOCK, Waldschatten.id("soul_light"));

	/**
	 * Last soul light each player was standing in. Re-verifying one remembered block is a
	 * single lookup; finding one from nothing is thousands. Players do not usually walk
	 * away from the only working light in the forest, so the cache hits nearly always.
	 */
	private static final Map<UUID, BlockPos> LAST_SOUL_LIGHT = new HashMap<>();
	private static final Map<UUID, Long> LAST_FULL_SCAN = new HashMap<>();

	public static void register() {
		// Both caches are keyed by player UUID, so a player who logs out while in the wood
		// at night would otherwise leave an entry behind for the lifetime of the server.
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.getPlayer()));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % SCAN_INTERVAL_TICKS != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tickPlayer(player);
			}
		});
	}

	private static void tickPlayer(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (player.isSpectator() || player.isCreative()) {
			// Creative and spectator players are usually here to look at the place, and a
			// pulsing black screen is the enemy of that. Survival is where the rule bites.
			forget(player);
			return;
		}

		BlockPos pos = player.blockPosition();
		boolean inWood = level.getBiome(pos).is(WaldschattenWorldgen.WALDSCHATTEN);
		if (!inWood || !level.isDarkOutside()) {
			forget(player);
			return;
		}

		if (soulLightNear(level, player, pos)) {
			// The exemption. Lift it immediately rather than waiting the effect out, or a
			// player who has just lit the right torch stands in the dark wondering why.
			if (player.hasEffect(MobEffects.DARKNESS)) {
				player.removeEffect(MobEffects.DARKNESS);
			}
			return;
		}

		// Renew only when it is running out. Re-adding blind every scan pushed a fresh
		// effect packet to every affected player several times a second for no visible gain.
		MobEffectInstance current = player.getEffect(MobEffects.DARKNESS);
		if (current == null || current.getDuration() < REFRESH_BELOW_TICKS) {
			player.addEffect(new MobEffectInstance(
					MobEffects.DARKNESS, DARKNESS_DURATION_TICKS, 0, false, false));
		}
	}

	/**
	 * Is there a lit soul flame within reach?
	 *
	 * <p>Three tiers, cheapest first, because the naive answer — walk every block in a
	 * 14-block ball — is about fourteen thousand lookups per player per scan and this runs
	 * several times a second.
	 *
	 * <ol>
	 *   <li>the block the player last stood in the light of, still there: one lookup</li>
	 *   <li>no block light reaching the player at all: one lookup, and a definitive no,
	 *       since a soul flame in range would necessarily be lighting them</li>
	 *   <li>only then, the full search — and at most once every two seconds</li>
	 * </ol>
	 */
	private static boolean soulLightNear(ServerLevel level, ServerPlayer player, BlockPos pos) {
		UUID id = player.getUUID();

		BlockPos cached = LAST_SOUL_LIGHT.get(id);
		if (cached != null && withinReach(cached, pos) && isSoulLight(level, cached)) {
			return true;
		}
		LAST_SOUL_LIGHT.remove(id);

		// The fast path, and now a sound one: a soul flame emits light 10, so one within
		// nine blocks necessarily puts at least light 1 on the player. No block light at all
		// is therefore proof there is no soul flame in range — for a single lookup, which is
		// what keeps this affordable for every player standing in the dark.
		//
		// It also means the exemption follows light rather than line-of-sight distance: a
		// soul torch walled off from you does not count, which is the behaviour you would
		// want anyway.
		if (level.getBrightness(LightLayer.BLOCK, pos) == 0) {
			return false;
		}

		long now = level.getGameTime();
		Long last = LAST_FULL_SCAN.get(id);
		if (last != null && now - last < FULL_SCAN_INTERVAL_TICKS) {
			return false;
		}
		LAST_FULL_SCAN.put(id, now);

		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int i = 0; i < OFFSETS.length; i += 3) {
			cursor.set(pos.getX() + OFFSETS[i], pos.getY() + OFFSETS[i + 1], pos.getZ() + OFFSETS[i + 2]);
			if (isSoulLight(level, cursor)) {
				LAST_SOUL_LIGHT.put(id, cursor.immutable());
				return true;
			}
		}
		return false;
	}

	private static boolean withinReach(BlockPos light, BlockPos player) {
		return light.distSqr(player) <= (double) SOUL_LIGHT_RADIUS * SOUL_LIGHT_RADIUS;
	}

	private static boolean isSoulLight(ServerLevel level, BlockPos pos) {
		// isLoaded guards the search running off the edge of loaded chunks, which would
		// otherwise pull chunks in from a light check.
		if (!level.isLoaded(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		if (!state.is(SOUL_LIGHT)) {
			return false;
		}
		// A soul campfire can be doused, and a dead fire is not a light. The tag cannot
		// say that — tags name blocks, not block states — so it is said here.
		return !state.hasProperty(BlockStateProperties.LIT) || state.getValue(BlockStateProperties.LIT);
	}

	private static void forget(ServerPlayer player) {
		LAST_SOUL_LIGHT.remove(player.getUUID());
		LAST_FULL_SCAN.remove(player.getUUID());
	}

	private WaldschattenDarkness() {
	}
}
