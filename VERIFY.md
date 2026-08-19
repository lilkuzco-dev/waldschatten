# Waldschatten 0.1.1 verification

Date: 2026-08-18. Target: Minecraft 26.2, Fabric Loader 0.19.3,
Fabric API 0.157.0+26.2, Loom 1.17, JDK 25.

## Build and worldgen

- JDK 25 clean build — pending final 0.1.1 commit (run remotely; no local Java).
- `./gradlew runGametest` — pending 0.1.1 remote verification of registry, recipe,
  structure, block-survival, canopy-light, darkness, soul-torch, and render assertions.
- The eight 0.1.1 render frames are pending review. The 0.1.0 frames covered the complete
  block board, dense canopy, witch hut, five set-pieces, night darkness, and soul-torch
  lighting without missing models or checkerboard textures.
- The six-seed vanilla and Terralith spawn-distance surveys are pending. They fail if the
  biome is absent within 1,000 blocks of actual spawn or if the patch hut is unlocatable.

## Release and shared-pack gate

- GitHub release, independent download/hash verification, shared manifest update, client
  convergence, and server deployment are pending the completed 0.1.1 verification.
