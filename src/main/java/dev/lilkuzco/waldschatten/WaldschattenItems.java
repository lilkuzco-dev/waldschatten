package dev.lilkuzco.waldschatten;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * One {@link BlockItem} per block, built from {@link WaldschattenBlocks#all()} rather than
 * written out by hand — a block added there cannot be forgotten here, which is the failure
 * this mod would otherwise ship every time it grew a block.
 */
public final class WaldschattenItems {

	private static final Map<String, Item> ALL = new LinkedHashMap<>();

	public static Map<String, Item> all() {
		return java.util.Collections.unmodifiableMap(ALL);
	}

	public static void register() {
		for (Map.Entry<String, Block> entry : WaldschattenBlocks.all().entrySet()) {
			String path = entry.getKey();
			Item item = new BlockItem(entry.getValue(), new Item.Properties()
					.useBlockDescriptionPrefix()
					.setId(ResourceKey.create(Registries.ITEM, Waldschatten.id(path))));
			Registry.register(BuiltInRegistries.ITEM, Waldschatten.id(path), item);
			ALL.put(path, item);
		}
	}

	private WaldschattenItems() {
	}
}
