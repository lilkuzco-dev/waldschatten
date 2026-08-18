package dev.lilkuzco.waldschatten;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/** The Waldschatten creative tab, in registration order. */
public final class WaldschattenTab {

	public static final ResourceKey<CreativeModeTab> KEY =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Waldschatten.id("main"));

	public static List<Item> contents() {
		return new ArrayList<>(WaldschattenItems.all().values());
	}

	public static void register() {
		CreativeModeTab tab = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
				.title(Component.translatable("itemGroup.waldschatten.main"))
				.icon(() -> new ItemStack(WaldschattenItems.all().get("twisted_log")))
				.displayItems((parameters, output) -> contents().forEach(output::accept))
				.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KEY, tab);
	}

	/**
	 * Every registered block must be reachable in creative. A block that exists in the
	 * registry but in no tab is invisible to anyone not typing its id, which is the same as
	 * not having shipped it.
	 *
	 * <p>Asked of the BLOCK REGISTRY, deliberately. The first version of this method compared
	 * {@link WaldschattenBlocks#all()} against {@link WaldschattenItems#all()} — but the item
	 * map is built by iterating the block map, so the two key sets were identical by
	 * construction and the check could not fail for any input. It was a gate that reported
	 * success while being incapable of detecting anything, which is worse than no gate:
	 * CLAUDE.md rule 4's failure mode exactly. The registry is ground truth, so a block
	 * registered by any route that skips {@code WaldschattenBlocks.register} is now caught.
	 */
	public static void assertComplete() {
		Set<Item> inTab = new HashSet<>(contents());
		List<String> problems = new ArrayList<>();

		for (Map.Entry<ResourceKey<Block>, Block> entry : BuiltInRegistries.BLOCK.entrySet()) {
			Identifier id = entry.getKey().identifier();
			if (!id.getNamespace().equals(Waldschatten.MOD_ID)) {
				continue;
			}
			Item item = entry.getValue().asItem();
			if (item == Items.AIR) {
				problems.add(id + " (no item at all)");
			} else if (!inTab.contains(item)) {
				problems.add(id + " (item exists but is in no creative tab)");
			}
		}

		// And the reverse: an item listed in the tab that never made it into the registry
		// would show as an empty slot rather than as an error.
		for (Item item : contents()) {
			if (BuiltInRegistries.ITEM.getResourceKey(item).isEmpty()) {
				problems.add("tab lists an unregistered item: " + item);
			}
		}

		if (!problems.isEmpty()) {
			throw new IllegalStateException("Waldschatten content unreachable in creative: " + problems);
		}
	}

	private WaldschattenTab() {
	}
}
