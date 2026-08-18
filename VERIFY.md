# Waldschatten 0.1.0 verification

Date: 2026-08-18. Target: Minecraft 26.2, Fabric Loader 0.19.3,
Fabric API 0.157.0+26.2, Loom 1.17, JDK 25.

## Build and worldgen

- `./gradlew clean test build` — PASS from release commit `4df96d3`.
- `./gradlew runGametest` — PASS. Registry, recipe, structure, block-survival,
  canopy-light, ordinary-torch darkness, soul-torch relief, and render assertions
  completed without a Waldschatten exception.
- The eight render frames were read. The complete block board, dense canopy, witch
  hut, five set-pieces, night darkness, and soul-torch lighting all rendered without
  missing models or checkerboard textures.
- The vanilla survey exposed Waldschatten in a 56-biome source and found the nearest
  sampled biome 1,249 blocks from origin and the nearest witch hut 4,655 blocks away.
- `./gradlew runTerralithSurvey` staged Terralith 2.6.4 and Lithostitched
  1.8.0+beta3 beside the mod — PASS. The real 147-biome source contained
  Waldschatten; 84 of 4,225 samples (1.99%) resolved to it, the nearest sampled biome
  was 1,505 blocks away, and the nearest witch hut was 3,170 blocks away.

## Release and shared-pack gate

- GitHub release `v0.1.0` published from tagged commit `4df96d3` with the clean
  119,911-byte `waldschatten-0.1.0.jar`.
- The independently downloaded release asset is byte-for-byte identical to the clean
  build and has SHA-512
  `0a67bc26092a9b84a9c7f9d5d0f9bb72f83c2c8203d4a88a939ab7a760f0955a68d5370f82a5b0832b6389c1e3e13e3ce2c35ef7ac6480110267a73146a79123`.
- The pushed shared manifest ships the exact release on both client and server.
  `tools/postship-check.sh` — PASS: the real client was updated, convergence produced
  a zero-change plan, every direct-JAR hash matched, and the complete 54-mod set plus
  107 bundled components passed load compatibility. Client backup:
  `/Users/jessehagy/Library/Application Support/minecraft/mods-backup-20260818-192738`.
