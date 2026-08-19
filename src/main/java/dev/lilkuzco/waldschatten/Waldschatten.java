package dev.lilkuzco.waldschatten;

import dev.lilkuzco.waldschatten.worldgen.WaldschattenWorldgen;
import dev.lilkuzco.waldschatten.worldgen.WaldschattenHeadlessSurvey;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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
		WaldschattenHutWitch.register();

		if (Boolean.getBoolean("waldschatten.headless.survey")) {
			ServerLifecycleEvents.SERVER_STARTED.register(server -> {
				long seed = Long.getLong("waldschatten.survey.seed", 0L);
				WaldschattenHeadlessSurvey.run(server.overworld(), seed);
				LOGGER.info("WALDSCHATTEN_HEADLESS all release assertions passed; stopping survey server.");
				server.halt(false);
			});
		}

		// Every block that a player can hold must appear in the tab. A block registered
		// and then forgotten here is invisible in creative and effectively unshipped, so
		// the mismatch is a startup failure rather than something noticed months later.
		WaldschattenTab.assertComplete();

		LOGGER.info("Waldschatten initialised: {} blocks, biome {}.",
				WaldschattenBlocks.all().size(), WaldschattenWorldgen.WALDSCHATTEN.identifier());
	}
}
