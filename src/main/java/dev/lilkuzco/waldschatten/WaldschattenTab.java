package dev.lilkuzco.waldschatten;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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
	 * registry but in no tab is invisible to anyone not typing its id, which is the same
	 * as not having shipped it — so the mismatch fails startup instead of going unnoticed.
	 */
	public static void assertComplete() {
		List<String> missing = new ArrayList<>();
		for (Map.Entry<String, net.minecraft.world.level.block.Block> entry : WaldschattenBlocks.all().entrySet()) {
			if (!WaldschattenItems.all().containsKey(entry.getKey())) {
				missing.add(entry.getKey());
			}
		}
		if (!missing.isEmpty()) {
			throw new IllegalStateException(
					"Waldschatten blocks with no item, so unreachable in creative: " + missing);
		}
	}

	private WaldschattenTab() {
	}
}
