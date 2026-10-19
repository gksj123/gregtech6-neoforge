# GregTech 6: NeoForge 1.21.1

This directory is the modern NeoForge 1.21.1 port workspace for GregTech 6. The
legacy 1.7.10 source is kept in the separate sibling directory
`../gregtech6-legacy` and remains the gameplay reference.

> **Contributors (human or AI): read [AGENTS.md](AGENTS.md) first.** The port's
> governing rule is a complete, lossless replication of GT6 1.7.10 — no
> rebalancing, redesign, or "improvements". The legacy feature index at
> [`../gregtech6-legacy/FEATURE_INDEX/INDEX.md`](../gregtech6-legacy/FEATURE_INDEX/INDEX.md)
> maps every legacy package and file to the content it defines, with source line
> references, and is the required entry point before porting any feature.

## Current Status

This is a runnable **vertical slice**, not a complete GregTech 6 port. It deliberately prioritizes the three defining systems requested for the modern version:

1. GUI-free in-world machine control.
2. Packet-based energy with separate size/amount, voltage tiers, amperage, loss, and overload.
3. A formed multiblock machine that can be assembled and operated in survival.

The first playable machines are:

- A single-block **Kinetic Crusher** that accepts only KU.
- A source-shaped 5x5x3 **Large Crusher** that accepts only RU.
- A source-shaped 3x3x3 **Large Crucible** with material thermal mass.
- 39 source-defined material variants of the single-block **Smelting Crucible**.
- A **Thermometer** tool and analog-redstone **Thermometer Sensor**.
- GUI-free solid-fuel **Burning Boxes** that emit HU.
- Ignited Burning Box animation, particles, contact damage and water extinguishing.
- Source-shaped **casting molds**, faucets and 5x5 carved shape masks.
- Steam boiler tanks, reciprocal steam engines and steam turbines using real fluid handlers.

GT6's energy families are kept separate. RU, KU, EU, and LU are not interchangeable scalar values. Converters explicitly transform one family into another and apply their own losses.

## Content Migration Foundation

The port now loads a generated inventory of the legacy source at startup:

- 1112 unique material-name keys
- 326 material prefixes/forms
- 324 registered material-form items
- 11 generic material block families
- 1366 multitile machine registrations indexed for migration
- 430 recipe-map handler registrations extracted from the legacy source
- 40 initial source handlers enabled only when their conditions are provably safe
- Explicit vanilla smashing recipes extracted from GT6's recipe loader
- Modern item capabilities for machine inputs, outputs, and configured ports

Material items use one modern data component to store the original material and prefix instead of reviving 1.7.10 metadata. Material blocks use a block entity so the same block family can represent many materials without thousands of Java classes.

The generated migration inventory is documented in [SOURCE_OF_TRUTH.md](SOURCE_OF_TRUTH.md) and produced by `tools/inventory_gt6.py`.
Platform-forced differences from the 1.7.10 source are listed in
[DEVIATIONS.md](DEVIATIONS.md).
The source interaction audit is documented in
[GAMEPLAY_INTERACTIONS.md](GAMEPLAY_INTERACTIONS.md).

Repeated gameplay properties are generated separately:

```sh
python3 tools/inventory_gt6.py
python3 tools/export_gameplay_properties.py
python3 tools/export_alloys.py
python3 tools/export_molds.py
python3 tools/import_gt6_assets.py
python3 tools/generate_crucible_models.py
```

The generators look for `../gregtech6-legacy` by default. Set
`GT6_LEGACY_HOME=/absolute/path/to/gregtech6-legacy` when the legacy checkout
is stored somewhere else.

The gameplay extractor currently resolves:

- 492 of 1115 materials to explicit/constructor/inherited heat values.
- 381 materials with non-default tool types.
- Hardness and resistance for all 1366 multitile registrations.
- Block sound classification for all 1366 registrations.
- Event sound profiles for 78 source classes.
- 135 resolved crucible alloy configurations, with 2 dynamic source loops marked unresolved.
- 39 blank mold and 39 faucet material variants, 30 named 5x5 shapes, and 549 source-transformed masks.
- 107 casting recipes: 38 material molds, 39 faucets, and 30 raw ceramic mold smelting recipes.
- Actual Steam and Distilled Water fluids, boiler/engine/turbine fluid capabilities, and dimensional steam-pipe routing.

Unresolved computed or default values remain marked in JSON instead of being
silently guessed.

## Verified Environment

- Minecraft 1.21.1
- NeoForge 21.1.227
- Java 21
- Gradle wrapper 8.14.2
- Isolated runtime client launched and entered the world
- Runtime self-test formed the exact 5x5x3 crusher, transferred RU/KU, and produced
  3 gravel through each crusher path plus 9 material outputs through the generic KU machine
- Runtime self-test melted iron in both crucible types and verified that the adjacent
  thermometer sensor reported the same temperature and a nonzero analog signal

The build intentionally compiles against the supplied local Minecraft installation. It does not need a global Gradle installation and does not currently download NeoGradle dependencies.

## Build

```sh
cd gregtech6-neoforge
./gradlew build
```

The main artifact is:

```text
gregtech6-neoforge/build/libs/gregtech-neoforge-1.21.1-6.17.06-alpha.1.jar
```

If the Minecraft installation is somewhere else, change `minecraft_home` in `gradle.properties`.

## Isolated Development Client

```sh
./gradlew build
python3 tools/run_client.py
```

This uses a separate `run-client` directory and does not add the development jar to the existing Create modpack. The launcher assumes the supplied `.minecraft/versions/create` runtime by default. Override paths with:

```sh
GT6_MINECRAFT_HOME=/path/to/.minecraft \
GT6_MINECRAFT_VERSION=create \
python3 tools/run_client.py
```

## Runtime Self-Test

The automation opens the prepared save named `world` by default, builds a complete machine, fuels the generator, inserts cobblestone, and expects gravel:

```sh
GT6_JVM_ARGS='-Dgregtech.runtimeSelfTest=true' python3 tools/run_client.py
```

If `world` has no `level.dat`, the launcher automatically selects the only valid
save in `run-client/saves`. Set `GT6_RUNTIME_WORLD` explicitly when multiple
saves are present.

A successful run ends with:

```text
GT6_RUNTIME_SELFTEST_PASS RU=3 EU_KU=3 CRANK_KU=3 GENERIC_KU=9 CRUCIBLE=1996K SMALL_CRUCIBLE=2094K CRUCIBLE_DROPS=true/true FILL=true/true STEELMAKING=true CASTING=true/true/true FAUCET=true/true ALLOYS=135 BURNING_BOX_LIT=true MOLD_INTERACTION=true MOLD_AUTO=true STEAM_FLOW=true STEAM_NO_VOID=true STEAM_TURBINE=true CASTING_RECIPES=true MOLD_RIGHT_CLICK=true LARGE_WALL_MOLD=true MOLD_SHAPES=true
```

The runtime test requires the configured save under `run-client/saves/`. A flat test world can be generated using the vanilla 1.21.1 server jar and copied there.

On a machine without a usable OpenGL display, install the matching NeoForge
dedicated server into `run-server` once, then run the same test headlessly:

```sh
java -jar neoforge-21.1.227-installer.jar --installServer run-server
GT6_JVM_ARGS='-Dgregtech.runtimeSelfTest=true' python3 tools/run_server.py
```

## In-World Controls

No machine menu is opened.

| Tool or action | Result |
| --- | --- |
| Screwdriver on a controller | Toggle machine on/off |
| Screwdriver on a port | Cycle energy/item/fluid port mode |
| Wrench on a controller | Rotate the facing and request a structure check |
| Soft hammer | Reset a port or force a structure check |
| Magnifying glass or electrometer | Print machine, cable, and energy diagnostics |
| Chisel on a mold's top face | Carve one cell of the 5x5 shape |
| Pincers on a mold | Remove a casting without temperature damage |
| Water bucket on a steam boiler | Insert 1000 L of water |
| Soft hammer on an engine or turbine | Toggle the machine on/off |
| Right-click an `ITEM_IN` port with an item | Insert into the controller input bus |
| Right-click an `ITEM_OUT` port with an empty hand | Extract from the controller output bus |
| Right-click the generator with fuel | Insert solid fuel |
| Right-click the manual crank | Emit RU 16 to its facing side |
| Right-click without a tool | Print compact machine status |

## Creative Demo Inventory

The demo exposes eight source-oriented creative tabs:

| Tab | Current entries | Contents |
| --- | ---: | --- |
| Demo Kit | 34 | tools, controllers, crucible, mold, faucet, sensor, steam starter, structure materials, ingots and fuel |
| Material Forms | 10768 | all materials in nine primary forms, plus all 30 mold-output forms for core materials |
| Material Blocks | 12232 | all 11 current material block families across the material registry |
| Machines | 251 | all GT6 Basic Machine registrations |
| Multiblocks & Heat | 61 | crushers, crucible, thermometer sensor, walls, ports, wheels, Burning Boxes and all boiler tanks |
| Tools & Energy | 62 | tools, generators, converters, axles, cables, all steam engines and turbines |
| Smelting Crucibles | 41 | 39 material crucibles, the quicksilver thermometer and the thermometer sensor |
| Molds & Casting | 110 | 30 raw ceramic shapes, 39 blank wall-material molds, 39 faucets, chisel, pincers and thermometer |

The **GT6 NeoForge Demo Guide** item prints the interaction sequence in chat.
Material and material-block tooltips include the original inventory category,
source material/prefix, melting point, boiling point, density and tool stats
when those values could be resolved from the GT6 source.

NeoForge creative tabs accept only count-one templates; players choose the final
stack size through normal creative-mode pickup behavior.

## Large Crusher Assembly

The source shape is a 5x5x3 basin, matching `MultiTileEntityCrusher`:

- Place the controller at the center of the bottom side, facing outward.
- The bottom layer is a full 5x5 wall of Machine Casings, with ports allowed in place of casings.
- The middle and top layers have a 5x5 casing perimeter.
- Their shared 3x3 interior is filled with 18 Crusher Wheels.
- The source structure uses 56 Tungstensteel walls plus the controller; the current port block can replace walls.
- Set one boundary port to `ENERGY_IN` with a screwdriver.
- Set one boundary port to `ITEM_IN` and another to `ITEM_OUT`.
- Connect an RU source to the energy port using axles.
- The LV electric motor currently produces only RU 16, so it does not yet meet the Large Crusher's original RU 512 minimum.

The controller checks its structure every second. The soft hammer forces an immediate check.

## Energy Model

GT6 energy is not a single scalar. `EnergyPacket` stores:

- `kind`: EU, RU, KU, HU, CU, LU, MU, NU, QU, or TU.
- `size`: voltage for electricity, speed for rotation, pressure for air, and so on.
- `amount`: amperage for electricity or the corresponding packet count for another type.

All energy packets follow GT6's core size/amount convention. The family-specific implementations are:

- EU: size is voltage, amount is amperage.
- RU: size is rotational speed, amount is packet count. RU travels through axles.
- KU: size is push/pull, amount is packet count. KU is delivered directly to the facing block and cannot travel through electric cable.
- LU: size is beam size and amount is frequency. LU is reserved for laser machines and is not accepted by crushers.
- Steam is not an energy family in GT6: it is a fluid moved through fluid capabilities and pipes.

The original machine mapping is preserved:

- Normal Crusher IDs 20021–20024: KU, size 16–64, recommended 32.
- Large Crusher ID 17108: RU, size 512–4096, recommended 512, 50% efficiency.
- Electric Engine: EU → KU, 50% energy loss.
- Electric Motor: EU → RU, 50% energy loss.
- Rotation Engine: RU → KU, 50% energy loss.
- Manual Crank: RU 16 per turn.

EU cables do not accept RU or KU. They cannot become a hidden universal-energy bridge.

Tin cable: 32 EU voltage, 2 A, 1 V/block loss.

Copper cable: 64 EU voltage, 4 A, 1 V/block loss.

## Kinetic Crusher Example

The single-block Kinetic Crusher is the easiest working energy chain.

Electric route:

```text
LV Generator (EU 32 × 2)
  -> Electric Engine, facing the crusher (EU -> KU 16, 50% loss)
  -> Kinetic Crusher (KU only)
```

Manual route:

```text
Manual Crank (RU 16 per turn)
  -> one or more Axles (RU only)
  -> Rotation Engine, facing the crusher (RU -> KU 16, 50% loss)
  -> Kinetic Crusher (KU only)
```

Right-click the crank repeatedly. The rotation engine needs 32 RU buffered before it emits one 16 KU packet. Right-click the crusher with an input item and right-click it empty-handed to extract output.

## Recipe Engine

The machine recipe layer reads generated data from the GT6 source rather than a
GT5/GTCEu voltage table:

- `data/gregtech/gt6/recipe_handlers.json` stores every extracted handler registration.
- `data/gregtech/gt6/recipes.json` stores source-backed explicit item recipes.
- Material handlers are enabled only when their output stays in the same material,
  no fluid or extra item is required, and the source condition only contains
  trivially true checks such as `ANTIMATTER.NOT` or `MT.Empty.NOT`.
- Handler duration uses the source prefix material amounts and the extracted
  duration/multiplier fallback. Material tool-quality-dependent duration is not
  yet extracted, so complex conditional recipes remain disabled.
- Generic basic machines have 9 input and 9 output slots, source parallelism,
  `parallel_duration`, efficiency, and cheap-overclocking flags.

## Heat, Burning Boxes, and Crucible

The burning box implementation reads its source registration instead of using a
single hard-coded heat value. For example, the Steel Solid Burning Box is source
line 528: it emits HU 32, has efficiency 7000/10000, and uses the Furnace fuel map.
Only Furnace-map boxes are currently exposed as playable variants; gas and
fluidized-bed maps remain pending fluid support.

The large crucible follows `MultiTileEntityCrucible`:

- 3x3x3 hollow arrangement.
- Bottom-center controller.
- Eight Crucible Walls on each of the three horizontal layers.
- Empty 1x1 vertical center.
- HU input from the burning box directly below the controller.
- Right-click the controller with a material-form item to add one item.

Temperature uses source-derived thermal mass:

```text
material mass = density × 111.111111 × material units / U
required HU per K = 1 + floor(total thermal mass / 100)
```

The total mass includes the steel crucible body and every inserted material.
Melting, boiling, evaporation, environmental cooling, and a wall-material
temperature limit are checked separately. Material density, heat, and tool data
come from `materials_gameplay.json`; unresolved density values remain marked
with their confidence field.

The small crucible follows `MultiTileEntitySmeltery`:

- All 39 `Smelting Crucibles` registrations from the GT6 source are exposed as data variants.
- Capacity is `16U`; the wall contributes `7U` to thermal mass.
- The wall can be heated to 1.25 times its source melting point before it melts into lava.
- The rendered block is an open 14x14 footprint with 2px walls and a hollow top.
- Collision covers the actual walls and floor; item entities dropped into the opening are consumed.
- Content height drives an animated, material-tinted surface in eight visual levels.
- The surface adds collision, KU creates steelmaking oxygen, and CU removes heat.
- HU is accepted from any side, while materials are inserted by right-clicking their item form.
- The Thermometer prints Kelvin and the wall limit; the Thermometer Sensor reads an
  adjacent temperature provider every 10 ticks and emits a proportional 0-15 redstone signal.

The large crucible also pulls material-form item entities from its 3x3 interior,
matching the source `WD.suck` behavior. Its animated content surface is rendered
through a block entity renderer. Thermometer sensors are 2px wall-mounted panels
with directional shapes rather than full cubes.

Item tooltips use English GT abbreviations and colored sections. Machine items
show their `gregtech:machine_part` component, while material items show their
`gregtech:material_part` component, thermal data, tool data and source line.

## Alloying and Casting

`tools/export_alloys.py` extracts every source `setAloy` and `uumAloy`
configuration plus the explicit `addAlloyingRecipe` block. At runtime the
crucible reproduces the GT6 selector: it starts from molten content, permits at
most one non-molten component, compares conversion count multiplied by the
common divider, consumes every component, and emits
`common_divider × conversions`. KU-generated Air is consumed from the actual
oxygen buffer, so Steel and Meteoric Steel use the same source data as every
other alloy.

`tools/export_molds.py` reads the mold and faucet registrations, evaluates the
source 5×5 mask table, and applies GT6's rotation/mirror transformations.
The resulting 549 masks map to the original item prefixes. A mold is a flat
5×5 tray, not a deep box: the source uses a 1 px base, 4 px walls, twelve small
side handles and a 3 px grid of 25 cells. The block model and block-entity
renderer reproduce those dimensions, and the collision box is the source's
12×12×2 px footprint. A mold:

1. starts blank in the creative table; a chisel removes individual cells and
   wrench/screwdriver/rotator rotates the 25-bit mask;
2. accepts material from the top or any horizontal face (never the bottom),
   and pulls one complete casting amount from an adjacent molten crucible;
3. stores the pouring material and temperature;
4. cools at up to 5K/tick, or accepts CU for faster cooling;
5. solidifies below the material melting point into its configured prefix,
   including the source's COOL2CRYSTAL plate→gem substitution;
6. returns the correctly component-tagged item when clicked with an empty hand
   or a non-tool item;
7. melts into lava above the wall limit or content boiling point.

Automatic input uses the source's per-face connection mask
(`gt.connection`) and redstone mode (`gt.mode`). A monkey wrench on the top
face selects one horizontal face near an edge, or toggles the redstone
requirement on the centre; a soft hammer clears both. The source phase is
`SERVER_TIME % 20 == 5`.


The faucet can pull from a crucible behind it and pour into a mold below it in
redstone or automatic mode. Molds use the source's flat 12×12×2 px collision,
a material-tinted animated casting surface, a chisel for individual 5×5 cells,
and wrench rotation. Material and machine item tooltips keep colored `MAT`,
`SHAPE`, `REQ`, `THERM`, `CTRL`, `SRC` and component-NBT entries; the
mold and faucet tooltips use the original GT6 wording and line order.

## Current Port Limits

- Energy transfer is implemented through a bounded packet flood.
- RU transmission is implemented through axle networks.
- KU is direct-facing only.
- Item ports support direct player insertion/extraction.
- Fluid port modes are represented in the UI-free state machine but fluid-network transfer is not implemented yet.
- Hoppers and other mods cannot yet discover item ports through a NeoForge capability.
- Mold material crafting recipes and the pincer tool are not ported yet; blank molds are available in the creative table and the current recipe produces a stone mold.
- Recipe values are currently built into `CrusherRecipe`; GT6's full recipe map has not been migrated.

## Next Porting Phases

1. Add NeoForge item/fluid/energy capabilities to ports and machine controllers.
2. Port the 251 Basic Machine registrations onto one data-driven machine block and recipe-map engine.
3. Replace hard-coded structures with a data-driven pattern and rotation engine.
4. Port the large boiler and steam turbine to provide an authentic high-output RU source.
5. Port covers, side configuration, shutters, conveyor and pump modules.
6. Migrate GT6 ores, cables, circuits, and all recipe loader families.
7. Rebuild NEI-like recipe inspection without reintroducing machine GUIs.
8. Add progression data and world generation after the machine framework stabilizes.

## Licensing

Port source code is LGPL-3.0, matching GregTech 6. Reused casing, multiblock, and machine textures originate from the GregTech 6 CC0 asset set. New cable and tool textures in this directory are generated placeholders and may be replaced as the art pass proceeds.
