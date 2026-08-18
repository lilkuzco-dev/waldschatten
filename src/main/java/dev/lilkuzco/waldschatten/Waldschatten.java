package dev.lilkuzco.waldschatten;

import dev.lilkuzco.waldschatten.worldgen.WaldschattenWorldgen;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Waldschatten — "Forest Shadow".
 *
 * <p>A Brothers Grimm reading of the Schwarzwald: a canopy closed tightly enough that the
 * floor stays dark at noon, ground that fights back, and small wrong things left in
 * clearings by somebody who has not gone away.
 */
public class Waldschatten implements ModInitializer {
	public static final String MOD_ID = "waldschatten";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		WaldschattenBlocks.register();
		WaldschattenBlocks.registerInteractions();
		WaldschattenItems.register();
		WaldschattenTab.register();
		WaldschattenWorldgen.register();
		WaldschattenDarkness.register();

		// Every block that a player can hold must appear in the tab. A block registered
		// and then forgotten here is invisible in creative and effectively unshipped, so
		// the mismatch is a startup failure rather than something noticed months later.
		WaldschattenTab.assertComplete();

		LOGGER.info("Waldschatten initialised: {} blocks, biome {}.",
				WaldschattenBlocks.all().size(), WaldschattenWorldgen.WALDSCHATTEN.identifier());
	}
}
