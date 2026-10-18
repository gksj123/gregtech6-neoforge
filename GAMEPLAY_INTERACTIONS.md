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
| KU with oxygen above creates steelmaking air | Implemented as stored oxygen |
| Iron/Wrought Iron plus oxygen becomes Steel at 2046K | Implemented from GT6's alloy recipe |
| CU removes heat | Implemented |
| Rain adds a small amount of water to an open crucible | Implemented |
| Hot crucible damages entities touching it | Implemented |
| Over-temperature wall melts down into lava and damages nearby entities | Implemented |
| Thermometer reports Kelvin | Implemented |

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

- Full GT6 alloy recipe graph beyond the source Iron/Wrought Iron + Air steel recipe.
- Fluid container filling, molds, faucets, casting, and molten output.
- Material property evaluation for every flammable, explosive, acidic, and radioactive source case.
- Per-registration front overlays for all 251 Basic Machine catalog entries.
