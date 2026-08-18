package dev.lilkuzco.waldschatten.client;

import dev.lilkuzco.waldschatten.Waldschatten;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client setup — deliberately empty.
 *
 * <p>This class exists to record why it is empty, because the obvious thing to add here is
 * wrong on 26.2 and someone will try.
 *
 * <p><b>Do not add render-layer registration.</b> {@code BlockRenderLayerMap} is gone from
 * Fabric API (it is not in fabric-rendering-v1 25.3.2, which is what this mod builds
 * against) and there is nothing to replace it with, because the layer is no longer a
 * property of the block. {@code BakedQuad.MaterialInfo.of} calls
 * {@code ChunkSectionLayer.byTransparency(...)}: the game reads the alpha of the sprite and
 * picks SOLID, CUTOUT or TRANSLUCENT itself. Leaves and plants come out CUTOUT because
 * their textures contain fully transparent pixels, and nothing needs to say so.
 *
 * <p>The practical consequence for this mod is a rule about textures rather than code: a
 * texture that wants CUTOUT must use alpha 0 or 255 and nothing between, or the block
 * silently becomes TRANSLUCENT and sorts wrongly against everything around it.
 * {@code tools/gen-textures.js} only ever writes those two values.
 *
 * <p>Ambience that would otherwise live here is data instead: drifting ash is the biome's
 * {@code visual/ambient_particles}, the closed-in fog is its {@code fog_start_distance} /
 * {@code fog_end_distance}, and the fireflies around the hut and the fairy ring are real
 * {@code minecraft:firefly_bush} blocks placed by the features.
 */
public class WaldschattenClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		Waldschatten.LOGGER.debug("Waldschatten client init: nothing to do, by design.");
	}
}
