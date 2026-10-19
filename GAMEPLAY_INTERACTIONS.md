# GT6 Gameplay Interaction Audit

This document records the 1.7.10 source behavior used by the current demo
machines. Legacy paths are relative to `../gregtech6-legacy/src/main/java`.

## Burning Box

Source: `gregtech/tileentity/energy/generators/MultiTileEntityGeneratorSolid.java`

| Source behavior | Port status |
| --- | --- |
| Burning state drives an animated active overlay | Implemented as `lit=true` and the GT6 animated front overlay |
| Requires air/oxygen in front before consuming fuel | Implemented |
| Can ignite from a lit fire in front | Implemented with a 1/200 tick source check |
| Flint/igniter starts combustion | Implemented with flint and steel, fire charge, or direct test ignition |
| Extinguisher stops combustion | Implemented with a water bucket |
| HU is emitted upward | Implemented |
| Ash/output slot is extracted from the front | Implemented |
| Burning emits smoke, flame, and occasional lava particles | Implemented in `animateTick` |
| Contact causes heat/fire damage | Implemented |
| Front face has a reduced 14px collision height | Implemented |

## Small Smelting Crucible

Source: `gregtech/tileentity/tools/MultiTileEntitySmeltery.java`

| Source behavior | Port status |
| --- | --- |
| 14x14 footprint with 2px walls, open top, and hollow center | Implemented |
| Actual wall/floor collision instead of a full cube | Implemented |
| Content height changes the rendered liquid surface | Implemented with eight fill levels |
| Molten content uses GT6's animated molten texture and material tint | Implemented |
| Item entities in the top intake are pulled into the crucible | Implemented |
| HU heats the contents | Implemented |
| KU with oxygen above creates alloying Air | Implemented as stored oxygen |
| Crucible component recipes are evaluated dynamically | Implemented from all 135 statically resolved GT6 recipes |
| CU removes heat | Implemented |
| Rain adds a small amount of water to an open crucible | Implemented |
| Hot crucible damages entities touching it | Implemented |
| Over-temperature wall melts down into lava and damages nearby entities | Implemented |
| Thermometer reports Kelvin | Implemented |

## Crucible Alloy Selection

Source: `gregtech/tileentity/tools/MultiTileEntitySmeltery.java`,
`gregtech/tileentity/multiblocks/MultiTileEntityCrucible.java`, and
`gregapi/oredict/OreDictMaterial.java`

| Source behavior | Port status |
| --- | --- |
| `setAloy`, `uumAloy`, and `addAlloyingRecipe` all feed `mAlloyCreationRecipes` | Extracted by `tools/export_alloys.py` |
| Start from a molten non-air component | Implemented |
| Allow at most one non-molten component | Implemented |
| Compare conversions × common divider and choose the largest | Implemented |
| Consume undivided component amounts and emit common divider × conversions | Implemented |
| Steel uses Wrought Iron + Air; Meteoric Steel uses Meteoric Iron + Air | Implemented through generated data, not hard-coded gameplay |
| Dynamic `ANY.Glowstone.mToThis` recipes | Explicitly unresolved rather than guessed |

## Molds and Casting

Source: `gregtech/tileentity/tools/MultiTileEntityMold.java`,
`gregtech/tileentity/tools/MultiTileEntityFaucet.java`, and
`gregtech/loaders/b/Loader_MultiTileEntities.java`

| Source behavior | Port status |
| --- | --- |
| 39 mold wall-material registrations | Implemented as 39 blank data variants; the table no longer exposes pre-carved masks |
| 5x5 shape matrix with rotations and mirrors | 30 canonical shapes and 549 transformed masks; bit layout is the source row*5+column |
| Flat tray model (1 px base, 4 px walls, 3 px cells, side handles) | Implemented in the block model and block-entity renderer |
| Source collision/selection boxes | Implemented: 12x12x2 px collision, 16x16x3 px selection |
| Chisel adds one shape cell | Implemented on the top face only, with the source 2..14 px hit window |
| Wrench/screwdriver rotates shape | Implemented with the source 25-bit transformation |
| Empty-hand interaction from the top or a horizontal side | Implemented; a bottom click passes through |
| Top-face hit selects the adjacent crucible side | Implemented; centre hits try all four horizontal sides |
| Molds on the second layer of Large Crucible walls | Implemented; wall hits resolve the nearby formed controller |
| Mold pulls from an adjacent crucible | Implemented, restricted to the source connection mask |
| Automatic input is per-face plus an optional redstone gate | Implemented with `gt.connection` and `gt.mode`; phase %20==5 |
| Monkey wrench selects a face or toggles redstone | Implemented; soft hammer clears both |
| Mold accepts only an empty content/output state | Implemented |
| Input side is top or horizontal; bottom is rejected | Implemented and regression-tested |
| Content fills one complete output-prefix amount | Implemented; the crucible subtracts only the returned amount |
| `mTargetSolidifying` material/amount remapping | Implemented from explicit source `setSolidifying` calls, including Water→Ice and Lava→Obsidian |
| Acid-property rejection for non-acid-proof walls | Implemented from source constructor and explicit `ACID` tags |
| COOL2CRYSTAL plate -> gem substitution | Implemented for the two source materials (Ice, Glass) |
| Environmental cooling is at most 5K/tick | Implemented using the source biome formula instead of a fixed 293K |
| CU cooling follows source `size × amount` temperature reduction | Implemented |
| Solidifies below material melting point | Implemented |
| Output stays in the mold until taken | Implemented; empty hand, non-tool item, pincers or item capability can remove it |
| Output keeps material and prefix components | Implemented and runtime-tested |
| All 30 canonical outputs create their registered item forms | Runtime-tested, including plate, rod, chisel head and shovel head |
| Material output icons distinguish each mold shape | 30 source GT6 METALLIC form textures imported and tinted by material |
| Hot mold damages touching entities and the player taking output | Implemented |
| Wall-limit/boiling-point meltdown creates lava | Implemented |
| Faucet pulls behind and pours downward | Implemented |
| Faucet redstone/automatic modes | Implemented; redstone is edge-triggered, automatic uses the %20==5 phase |
| Mold/faucet/ceramic recipes | Generated from source ingredients: 38 mold recipes, 39 faucet recipes and 30 ceramic smelting recipes |

## Steam Boilers, Engines and Turbines

Sources:
`gregtech/tileentity/energy/converters/MultiTileEntityBoilerTank.java`,
`MultiTileEntityEngineSteam.java`, and `MultiTileEntityTurbineSteam.java`.

| Source behavior | Port status |
| --- | --- |
| Water + 80 HU -> 160 L steam | Implemented with actual Steam fluid stacks |
| Normal/distilled water distinction | Implemented through source tank fluid validation |
| Calcification only with remaining non-distilled water | Implemented |
| Steam only leaves the boiler through its top | Implemented with side-restricted fluid capabilities |
| Steam engine accepts steam only through its back | Implemented and runtime-tested |
| Engine capacity/output/state values | Read from exact generated `NBT_CAPACITY`/`NBT_OUTPUT` expressions |
| Engine alternating clockwise/counter-clockwise output | Implemented with directional energy packets |
| Engine and turbine distilled-water output | Implemented through adjacent fluid capabilities |
| Turbine input thresholds, tank size and conversion ratio | Implemented from exact `NBT_INPUT`/`NBT_OUTPUT` expressions |
| Turbine accepts steam only through its back | Implemented and runtime-tested |
| Soft hammer toggles engines/turbines | Implemented |
| Steam pipes | Implemented as fluid-capability pipes with actual side routing |

## Large Crucible

Source: `gregtech/tileentity/multiblocks/MultiTileEntityCrucible.java`

| Source behavior | Port status |
| --- | --- |
| 3x3x3 hollow wall structure | Implemented |
| Item entities are pulled from the 3x3 interior | Implemented |
| HU/KU/CU and oxygen steelmaking rules | Implemented |
| Fill level and material-tinted animated surface | Implemented with a block entity renderer |
| Hot content damages entities in the interior | Implemented |
| Over-temperature meltdown fills the structure with lava | Implemented |
| Thermometer reports Kelvin | Implemented |

## Other Current Machines

- Large Crusher damages entities inside a formed, active basin every 10 ticks.
- Temperature sensors are 2px wall panels with six-way facing and 0-15 analog output.
- Manual cranks, axles, and cables use non-full collision shapes.
- Current machine and material item tooltips use English labels, GT abbreviations, colored sections, and explicit NBT-style component data.

## Source Features Still Pending

- The two dynamic `ANY.Glowstone.mToThis` alloy loops.
- Material property evaluation for every flammable, explosive and radioactive source case.
- Per-registration front overlays for all 251 Basic Machine catalog entries.
