# Waldschatten 0.1.1 verification

Date: 2026-08-18. Target: Minecraft 26.2, Fabric Loader 0.19.3,
Fabric API 0.157.0+26.2, Loom 1.17, JDK 25.

## Build and worldgen

- JDK 25 clean `test build` — passed remotely from commit `81ad017a8e`; no local Java was
  started.
- Six vanilla worlds and six worlds with the live terrain stack (Terralith 2.6.4,
  lithostitched 1.8.0+beta3, Empire Worldgen 0.1.0) — **12/12 passed**.
- Waldschatten was within 1,000 blocks of actual spawn in all 12 worlds. Vanilla distances
  were 0–607 blocks; live-stack distances were 32–289 blocks.
- The Waldschatten witch hut was locatable in all 12 worlds. The live-stack hut distances
  were 75–374 blocks from spawn.
- Connected-patch terrain probes sampled up to 64 chunk centres in every live-stack world.
  All six measured 0 blocks of relief (24 blocks is the failure threshold).
- Biome-source samples found Waldschatten in 4.43–9.28% of vanilla cells and 0.59–2.20% of
  live-stack cells over the 8,192×8,192 survey square.
- The client render battery was not rerun for 0.1.1 because the remote container cannot
  execute Minecraft's extracted graphics natives. The 0.1.0 frames remain the visual proof;
  0.1.1 changes placement, structure placement, and release-only server surveying, not the
  models, textures, structures, palette, canopy features, or client rendering.

## Release and shared-pack gate

- GitHub release, independent download/hash verification, shared manifest update, client
  convergence, and server deployment follow this completed 0.1.1 verification.
