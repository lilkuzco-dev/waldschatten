package dev.lilkuzco.waldschatten;

import dev.lilkuzco.waldschatten.worldgen.WaldschattenWorldgen;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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

	/** A regular torch's light level, in blocks — what a soul torch buys you here. */
	public static final int SOUL_LIGHT_RADIUS = 14;
	/** Vertical reach of the search. Light sources a player cares about are near their eyes. */
	private static final int SOUL_LIGHT_HEIGHT = 8;

	private static final int SCAN_INTERVAL_TICKS = 10;
	/** Short, and refreshed constantly, so walking into soul light clears it quickly. */
	private static final int DARKNESS_DURATION_TICKS = 45;
	/** How often the *expensive* search may run for a player who has found no soul light. */
	private static final int FULL_SCAN_INTERVAL_TICKS = 40;

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

		player.addEffect(new MobEffectInstance(
				MobEffects.DARKNESS, DARKNESS_DURATION_TICKS, 0, false, false));
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

		// A soul flame inside the radius cannot fail to put *some* block light on the
		// player, so no block light is proof there is none, for the price of one lookup.
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
		for (int dy = -SOUL_LIGHT_HEIGHT; dy <= SOUL_LIGHT_HEIGHT; dy++) {
			for (int dx = -SOUL_LIGHT_RADIUS; dx <= SOUL_LIGHT_RADIUS; dx++) {
				for (int dz = -SOUL_LIGHT_RADIUS; dz <= SOUL_LIGHT_RADIUS; dz++) {
					if (dx * dx + dz * dz > SOUL_LIGHT_RADIUS * SOUL_LIGHT_RADIUS) {
						continue;
					}
					cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
					if (isSoulLight(level, cursor)) {
						LAST_SOUL_LIGHT.put(id, cursor.immutable());
						return true;
					}
				}
			}
		}
		return false;
	}

	private static boolean withinReach(BlockPos light, BlockPos player) {
		int dx = light.getX() - player.getX();
		int dy = light.getY() - player.getY();
		int dz = light.getZ() - player.getZ();
		return Math.abs(dy) <= SOUL_LIGHT_HEIGHT
				&& dx * dx + dz * dz <= SOUL_LIGHT_RADIUS * SOUL_LIGHT_RADIUS;
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
