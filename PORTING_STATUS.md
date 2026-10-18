# Porting Status

## Implemented

- NeoForge 21.1.227 mod bootstrap without a remote build dependency
- Java 21 Gradle wrapper build
- Block, item, block entity, creative tab, model, texture, loot, and recipe registration
- In-world wrench/screwdriver/soft-hammer/magnifier control
- Energy kinds and GT6 voltage tiers
- Separate EU, RU, KU, and LU families
- Packet size/amount model with no implicit family conversion
- EU cable voltage/amperage/overload and loss simulation
- RU axle transmission and direct-only KU delivery
- Electric Engine EU → KU and Electric Motor EU → RU converters
- Rotation Engine RU → KU and manual RU crank
- GUI-free port role cycling
- Source-correct 5x5x3 multiblock formation and rotation
- Kinetic Crusher processing with KU only
- Large Crusher processing with RU only
- LV solid-fuel generator
- Isolated runtime launcher
- Automated integrated-server acceptance test
- Generated source inventory: 1115 material declarations, 326 prefixes, 1366 multitile registrations
- Runtime material registry with 1112 unique material-name keys
- 324 registered material-form items backed by a modern NeoForge data component
- 11 generic material block families with block entities, placement metadata, tint hooks, and metadata-preserving drops
- 430 GT6 recipe-map handlers extracted from source
- Data-driven basic-machine recipe matching with 9 input and 9 output slots
- Source parallelism, `parallel_duration`, efficiency, and cheap-overclock flags
- Modern NeoForge item capabilities on machines and configured ports
- 18-block Crusher Wheels layer for the Large Crusher
- Automated material heat/tool extraction with confidence reporting
- Automated machine hardness, resistance, block sound, and event-sound extraction
- Source-shaped 3x3x3 Large Crucible structure
- 39 source-defined single-block Smelting Crucible material variants
- GUI-free solid-fuel Burning Boxes with source HU output and efficiency
- Density-derived thermal mass and material melting/boiling behavior
- Environmental cooling and crucible wall temperature limits
- GUI-free thermometer readings and analog-redstone Thermometer Sensor
- Source CC0 machine textures with original colored/overlay composition
- Hollow open-top small crucible model and material-based tint
- Source-shaped crucible, sensor, crank, axle, and cable collision boxes
- Item-entity insertion into both small and large crucibles
- Seven source-oriented creative inventory tabs covering all current content
- All-material primary-form and material-block creative catalogs
- In-game GUI-free demo guide item and detailed source/material tooltips

## Acceptance Evidence

```text
GT6 creative catalog: demo=29 materials=10368 blocks=12232 machines=251 multiblocks=35 tools=16 small_crucibles=41 small_crucible_heat_data=39
GT6 runtime self-test: large RU structure at BlockPos{x=0, y=40, z=2}, EU/KU chain at BlockPos{x=10, y=40, z=0}, RU/KU chain crank at BlockPos{x=14, y=40, z=-3}
GT6 runtime self-test: controller formed=true
GT6_RUNTIME_SELFTEST_PASS RU=3 EU_KU=3 CRANK_KU=3 GENERIC_KU=9 CRUCIBLE=1996K SMALL_CRUCIBLE=1896K CRUCIBLE_DROPS=true/true
```

Material bootstrap evidence:

```text
Loaded GT6 source inventory: 1112 materials, 326 material prefixes
```

## Not Yet Implemented

- Full GT6 material and ore registry
- Source-exact material colors and localized names
- Resolve remaining constructor/alloy heat calculations currently marked `default_unverified`
- Recipe generation from all 37 legacy loader families
- Most GT6 machines and all remaining large multiblocks
- Full GT6 recipe map migration
- Material property/condition evaluation for conditional source handlers
- Covers and complete side automation
- Fluids, tanks, and fluid recipe inputs/outputs
- Gas and fluidized-bed Burning Box fuel maps
- Crucible faucet, molds, casting, and molten-fluid capabilities
- Heat, laser, quantum, and remaining specialized energy transfer networks
- A survival RU source capable of reaching the Large Crusher's original 512 speed threshold
- WAILA/Jade, JEI/EMI, and other mod integrations
- GT6 world generation and progression
- Save migration from 1.7.10 worlds

## Important Design Boundary

The 1.7.10 source is not being mechanically renamed to 1.21 classes. GT6's old tile entity, rendering, block metadata, inventory, network, and recipe APIs do not translate one-to-one. The modern port extracts gameplay rules and reimplements them on NeoForge primitives.
