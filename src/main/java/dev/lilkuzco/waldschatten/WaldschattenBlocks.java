package dev.lilkuzco.waldschatten;

import dev.lilkuzco.waldschatten.block.BoneChimeBlock;
import dev.lilkuzco.waldschatten.block.GnarledRootsBlock;
import dev.lilkuzco.waldschatten.block.ThornVineBlock;
import dev.lilkuzco.waldschatten.block.WaldschattenPlantBlock;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.registry.StrippableBlockRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.UntintedParticleLeavesBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Every block Waldschatten adds.
 *
 * <p>The palette is deliberate and narrow: near-black bark, olive canopy, ash and bone.
 * Amber candlelight from {@link #IRON_LANTERN} is the only warm light source in the
 * biome, which is what makes a lit hut visible through the trunks from a long way off.
 */
public final class WaldschattenBlocks {

	/** Registration order, which is also creative-tab order. */
	private static final Map<String, Block> ALL = new LinkedHashMap<>();

	/**
	 * The tree. Bark is near-black with moss streaks; {@code twisted_wood} is the
	 * six-sided bark variant. Flammability is registered in {@link #register()} so these
	 * burn like the dark oak they stand in for.
	 */
	public static final Block TWISTED_LOG = new RotatedPillarBlock(
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_BLACK)
					.instrument(net.minecraft.world.level.block.state.properties.NoteBlockInstrument.BASS)
					.strength(2.0F)
					.sound(SoundType.WOOD)
					.ignitedByLava()
					.setId(blockKey("twisted_log")));

	public static final Block STRIPPED_TWISTED_LOG = new RotatedPillarBlock(
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_BROWN)
					.instrument(net.minecraft.world.level.block.state.properties.NoteBlockInstrument.BASS)
					.strength(2.0F)
					.sound(SoundType.WOOD)
					.ignitedByLava()
					.setId(blockKey("stripped_twisted_log")));

	public static final Block TWISTED_WOOD = new RotatedPillarBlock(
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_BLACK)
					.instrument(net.minecraft.world.level.block.state.properties.NoteBlockInstrument.BASS)
					.strength(2.0F)
					.sound(SoundType.WOOD)
					.ignitedByLava()
					.setId(blockKey("twisted_wood")));

	public static final Block STRIPPED_TWISTED_WOOD = new RotatedPillarBlock(
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_BROWN)
					.instrument(net.minecraft.world.level.block.state.properties.NoteBlockInstrument.BASS)
					.strength(2.0F)
					.sound(SoundType.WOOD)
					.ignitedByLava()
					.setId(blockKey("stripped_twisted_wood")));

	public static final Block TWISTED_PLANKS = new Block(
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_BLACK)
					.instrument(net.minecraft.world.level.block.state.properties.NoteBlockInstrument.BASS)
					.strength(2.0F, 3.0F)
					.sound(SoundType.WOOD)
					.ignitedByLava()
					.setId(blockKey("twisted_planks")));

	/**
	 * Untinted rather than biome-tinted: the canopy is a fixed sickly olive everywhere it
	 * grows, so a twisted tree planted in a plains biome still reads as the wrong tree
	 * rather than quietly turning plains-green.
	 */
	public static final Block TWISTED_LEAVES = new UntintedParticleLeavesBlock(
			0.005F,
			ParticleTypes.PALE_OAK_LEAVES,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_GREEN)
					.strength(0.2F)
					.randomTicks()
					.sound(SoundType.GRASS)
					.noOcclusion()
					.isValidSpawn((state, level, pos, type) -> false)
					.isSuffocating((state, level, pos) -> false)
					.isViewBlocking((state, level, pos) -> false)
					.ignitedByLava()
					.pushReaction(PushReaction.DESTROY)
					.isRedstoneConductor((state, level, pos) -> false)
					.setId(blockKey("twisted_leaves")));

	/**
	 * A planted sapling grows the tall tree. The mega-tree slot is deliberately empty:
	 * mega trees need four saplings in a 2x2, and {@code twisted_tree} is a single-trunk
	 * feature, so filling that slot would promise a 2x2 growth that never arrives.
	 */
	public static final TreeGrower TWISTED_TREE_GROWER = new TreeGrower(
			"waldschatten_twisted",
			java.util.Optional.empty(),
			java.util.Optional.of(ResourceKey.create(
					Registries.CONFIGURED_FEATURE, Waldschatten.id("twisted_tree"))),
			java.util.Optional.empty());

	public static final Block TWISTED_SAPLING = new SaplingBlock(
			TWISTED_TREE_GROWER,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_GREEN)
					.noCollision()
					.randomTicks()
					.instabreak()
					.sound(SoundType.GRASS)
					.pushReaction(PushReaction.DESTROY)
					.setId(blockKey("twisted_sapling")));

	/**
	 * Floor roots — the counterpart to vanilla's ceiling-hung hanging roots. No collision,
	 * so it is texture rather than an obstacle; the thorns are what obstruct.
	 */
	public static final Block GNARLED_ROOTS = new GnarledRootsBlock(
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_BROWN)
					.noCollision()
					.instabreak()
					.sound(SoundType.HANGING_ROOTS)
					.pushReaction(PushReaction.DESTROY)
					.setId(blockKey("gnarled_roots")));

	public static final Block WITCH_HAZEL_BUSH = new WaldschattenPlantBlock(
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_GREEN)
					.noCollision()
					.instabreak()
					.sound(SoundType.GRASS)
					.offsetType(BlockBehaviour.OffsetType.XZ)
					.pushReaction(PushReaction.DESTROY)
					.setId(blockKey("witch_hazel_bush")));

	public static final Block NIGHTSHADE_PLANT = new WaldschattenPlantBlock(
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_PURPLE)
					.noCollision()
					.instabreak()
					.sound(SoundType.SWEET_BERRY_BUSH)
					.offsetType(BlockBehaviour.OffsetType.XZ)
					.pushReaction(PushReaction.DESTROY)
					.setId(blockKey("nightshade_plant")));

	public static final Block MANDRAKE_ROOT = new WaldschattenPlantBlock(
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.TERRACOTTA_WHITE)
					.noCollision()
					.instabreak()
					.sound(SoundType.ROOTED_DIRT)
					.offsetType(BlockBehaviour.OffsetType.XZ)
					.pushReaction(PushReaction.DESTROY)
					.setId(blockKey("mandrake_root")));

	/** Hangs under a branch and rattles when something walks past it. */
	public static final Block BONE_CHIME = new BoneChimeBlock(
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.TERRACOTTA_WHITE)
					.noCollision()
					.strength(0.2F)
					.sound(SoundType.BONE_BLOCK)
					.pushReaction(PushReaction.DESTROY)
					.setId(blockKey("bone_chime")));

	public static final Block ASHEN_SOIL = new Block(
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_GRAY)
					.strength(0.5F)
					.sound(SoundType.ROOTED_DIRT)
					.setId(blockKey("ashen_soil")));

	public static final Block MOSSY_CAIRN_STONE = new Block(
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.STONE)
					.requiresCorrectToolForDrops()
					.strength(1.5F, 6.0F)
					.sound(SoundType.STONE)
					.setId(blockKey("mossy_cairn_stone")));

	/**
	 * Light level 10 rather than a lantern's 15: enough to read as a warm point in the
	 * dark and to keep the hut's own doorstep from spawning monsters, not enough to light
	 * the clearing around it. The dark is the biome's whole point.
	 */
	public static final Block IRON_LANTERN = new LanternBlock(
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.METAL)
					.forceSolidOn()
					.requiresCorrectToolForDrops()
					.strength(3.5F)
					.sound(SoundType.LANTERN)
					.lightLevel(state -> 10)
					.noOcclusion()
					.pushReaction(PushReaction.DESTROY)
					.setId(blockKey("iron_lantern")));

	/**
	 * Thorns. Slows anything walking through, scratches anything moving through fast, and
	 * is climbable (it is in {@code #minecraft:climbable}) so a thorn wall is a slow way
	 * up rather than a wall.
	 */
	public static final Block THORN_VINE = new ThornVineBlock(
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_RED)
					.noCollision()
					.randomTicks()
					.strength(0.2F)
					.sound(SoundType.VINE)
					.ignitedByLava()
					.pushReaction(PushReaction.DESTROY)
					.setId(blockKey("thorn_vine")));

	public static Map<String, Block> all() {
		return java.util.Collections.unmodifiableMap(ALL);
	}

	public static void register() {
		register("twisted_log", TWISTED_LOG);
		register("stripped_twisted_log", STRIPPED_TWISTED_LOG);
		register("twisted_wood", TWISTED_WOOD);
		register("stripped_twisted_wood", STRIPPED_TWISTED_WOOD);
		register("twisted_planks", TWISTED_PLANKS);
		register("twisted_leaves", TWISTED_LEAVES);
		register("twisted_sapling", TWISTED_SAPLING);
		register("gnarled_roots", GNARLED_ROOTS);
		register("witch_hazel_bush", WITCH_HAZEL_BUSH);
		register("nightshade_plant", NIGHTSHADE_PLANT);
		register("mandrake_root", MANDRAKE_ROOT);
		register("bone_chime", BONE_CHIME);
		register("ashen_soil", ASHEN_SOIL);
		register("mossy_cairn_stone", MOSSY_CAIRN_STONE);
		register("iron_lantern", IRON_LANTERN);
		register("thorn_vine", THORN_VINE);
	}

	/**
	 * The behaviours that make twisted wood behave like wood.
	 *
	 * <p>Registered separately from the blocks because none of it is a property of a block —
	 * stripping is a map held by the axe, and flammability is a table owned by the fire
	 * block. Miss them and nothing errors: the logs simply cannot be stripped (leaving both
	 * stripped variants unobtainable in survival) and a forest fire stops dead at the edge
	 * of this biome. Values are vanilla's own for the equivalent wood.
	 */
	public static void registerInteractions() {
		StrippableBlockRegistry.register(TWISTED_LOG, STRIPPED_TWISTED_LOG);
		StrippableBlockRegistry.register(TWISTED_WOOD, STRIPPED_TWISTED_WOOD);

		FlammableBlockRegistry fire = FlammableBlockRegistry.getDefaultInstance();
		for (Block log : new Block[] { TWISTED_LOG, STRIPPED_TWISTED_LOG, TWISTED_WOOD, STRIPPED_TWISTED_WOOD }) {
			fire.add(log, 5, 5);
		}
		fire.add(TWISTED_PLANKS, 5, 20);
		fire.add(TWISTED_LEAVES, 30, 60);
	}

	private static void register(String path, Block block) {
		Identifier id = Waldschatten.id(path);
		Registry.register(BuiltInRegistries.BLOCK, id, block);
		ALL.put(path, block);
	}

	private static ResourceKey<Block> blockKey(String path) {
		return ResourceKey.create(Registries.BLOCK, Waldschatten.id(path));
	}

	private WaldschattenBlocks() {
	}
}
