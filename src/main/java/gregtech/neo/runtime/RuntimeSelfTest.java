package gregtech.neo.runtime;

import gregtech.neo.GT6Neo;
import gregtech.neo.block.BasicMachineBlockEntity;
import gregtech.neo.block.BurningBoxBlock;
import gregtech.neo.block.BurningBoxBlockEntity;
import gregtech.neo.block.CrucibleBlock;
import gregtech.neo.block.CrucibleBlockEntity;
import gregtech.neo.block.GeneratorBlockEntity;
import gregtech.neo.block.BasicCrusherBlockEntity;
import gregtech.neo.block.KineticConverterBlock;
import gregtech.neo.block.LargeCrusherBlock;
import gregtech.neo.block.LargeCrusherBlockEntity;
import gregtech.neo.block.MachinePortBlock;
import gregtech.neo.block.MachinePortMode;
import gregtech.neo.block.MechanicalEnergyNetwork;
import gregtech.neo.block.SmallCrucibleBlockEntity;
import gregtech.neo.block.SmallCrucibleBlock;
import gregtech.neo.block.TemperatureSensorBlockEntity;
import gregtech.neo.block.MoldBlockEntity;
import gregtech.neo.block.FaucetBlock;
import gregtech.neo.block.FaucetBlockEntity;
import gregtech.neo.casting.CastingPart;
import gregtech.neo.casting.GTMoldRegistry;
import gregtech.neo.machine.GTMachineRegistry;
import gregtech.neo.machine.MachinePart;
import gregtech.neo.material.GTMaterialItems;
import gregtech.neo.material.GTMaterialGameplayRegistry;
import gregtech.neo.material.MaterialPart;
import gregtech.neo.registry.GTDataComponents;
import gregtech.neo.registry.GTBlocks;
import gregtech.neo.recipe.GTAlloyRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import gregtech.neo.core.EnergyKind;
import gregtech.neo.core.EnergyPacket;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Opt-in integration test used by the isolated development client.
 * Enable with -Dgregtech.runtimeSelfTest=true on a world that is already loaded.
 */
public final class RuntimeSelfTest {
    private static final boolean ENABLED = Boolean.getBoolean("gregtech.runtimeSelfTest");
    private static boolean armed;
    private static int ticks;
    private static BlockPos controllerPos;
    private static BlockPos basicCrusherPos;
    private static BlockPos crankCrusherPos;
    private static BlockPos crankPos;
    private static BlockPos genericMachinePos;
    private static BlockPos largeItemInputPortPos;
    private static BlockPos cruciblePos;
    private static BlockPos burningBoxPos;
    private static BlockPos smallCruciblePos;
    private static BlockPos moldPos;
    private static BlockPos faucetPos;
    private static BlockPos faucetMoldPos;
    private static BlockPos temperatureSensorPos;
    private static boolean genericCapabilityInserted;
    private static boolean burningHeatObserved;
    private static boolean burningBoxLitObserved;
    private static boolean creativeCatalogPass;
    private static boolean castingPoured;
    private static boolean castingOutputPass;
    private static boolean castingCooled;
    private static boolean alloyCatalogPass;
    private static boolean smallDropObserved;
    private static boolean faucetPoured;
    private static boolean faucetOutputPass;

    private RuntimeSelfTest() {
    }

    public static void install() {
        if (ENABLED) {
            NeoForge.EVENT_BUS.addListener(RuntimeSelfTest::onServerStarted);
            NeoForge.EVENT_BUS.addListener(RuntimeSelfTest::onServerTick);
        }
    }

    private static void onServerStarted(ServerStartedEvent event) {
        if (armed) {
            return;
        }
        armed = true;
        ticks = 0;
        genericCapabilityInserted = false;
        burningHeatObserved = false;
        burningBoxLitObserved = false;
        creativeCatalogPass = false;
        castingPoured = false;
        castingOutputPass = false;
        castingCooled = false;
        alloyCatalogPass = GTAlloyRegistry.resolvedRecipeCount() >= 100
            && GTAlloyRegistry.hasRecipeFor("steel")
            && GTAlloyRegistry.hasRecipeFor("meteoricsteel")
            && GTAlloyRegistry.hasRecipeFor("bronze")
            && GTAlloyRegistry.hasRecipeFor("stainlesssteel");
        smallDropObserved = false;
        faucetPoured = false;
        faucetOutputPass = false;
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        controllerPos = new BlockPos(spawn.getX(), spawn.getY() + 100, spawn.getZ() + 2);
        basicCrusherPos = controllerPos.offset(10, 0, -2);
        crankCrusherPos = basicCrusherPos.offset(4, 0, 0);
        crankPos = crankCrusherPos.offset(0, 0, -3);
        genericMachinePos = controllerPos.offset(20, 0, 5);
        cruciblePos = controllerPos.offset(-10, 0, 5);
        burningBoxPos = cruciblePos.below();
        smallCruciblePos = controllerPos.offset(-20, 0, 5);
        temperatureSensorPos = smallCruciblePos.east();
        moldPos = smallCruciblePos.north();
        faucetPos = smallCruciblePos.south();
        faucetMoldPos = faucetPos.below();
        buildStructure(level);
        buildKineticChain(level);
        buildGenericMachine(level);
        buildCrucible(level);
        buildSmallCrucible(level);
        buildCastingMold(level);
        creativeCatalogPass = validateCreativeCatalog(server);
        GT6Neo.LOGGER.info(
            "GT6 runtime self-test: large RU structure at {}, EU/KU chain at {}, RU/KU chain crank at {}",
            controllerPos,
            basicCrusherPos,
            crankPos
        );
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (!armed) {
            return;
        }
        ticks++;
        if (ticks == 1) {
            spawnCrucibleDropTests(event.getServer().overworld());
            if (event.getServer().overworld().getBlockEntity(controllerPos) instanceof LargeCrusherBlockEntity crusher) {
                crusher.forceStructureCheck();
                GT6Neo.LOGGER.info("GT6 runtime self-test: controller formed={}", crusher.isFormed());
                long euResult = crusher.receivePacket(
                    new EnergyPacket(EnergyKind.ELECTRICITY, 32, 1),
                    Direction.UP,
                    true
                );
                long kuResult = crusher.receivePacket(
                    new EnergyPacket(EnergyKind.KINETIC, 32, 1),
                    Direction.UP,
                    true
                );
                if (euResult != 0 || kuResult != 0) {
                    GT6Neo.LOGGER.error("GT6 runtime self-test: RU machine accepted mixed energy");
                }
                IItemHandler input = levelCapability(event, largeItemInputPortPos);
                if (input != null) {
                    input.insertItem(0, new ItemStack(Items.COBBLESTONE, 3), false);
                } else {
                    GT6Neo.LOGGER.error("GT6 runtime self-test: missing large crusher input-port item capability");
                    crusher.insertItem(new ItemStack(Items.COBBLESTONE, 3));
                }
                crusher.receivePacket(
                    new EnergyPacket(EnergyKind.ROTATION, 512, 8),
                    Direction.UP,
                    false
                );
            }
        }
        if (ticks <= 320) {
            ServerLevel level = event.getServer().overworld();
            for (int turn = 0; turn < 2; turn++) {
                MechanicalEnergyNetwork.distribute(
                    level,
                    crankPos,
                    Direction.SOUTH,
                    new EnergyPacket(EnergyKind.ROTATION, 16, 1),
                    false
                );
            }
            if (level.getBlockEntity(genericMachinePos) instanceof BasicMachineBlockEntity machine) {
                machine.receivePacket(
                    new EnergyPacket(EnergyKind.KINETIC, 32, 2),
                    Direction.UP,
                    false
                );
            }
        }
        if (ticks == 30) {
            ServerLevel level = event.getServer().overworld();
            if (level.getBlockEntity(cruciblePos) instanceof CrucibleBlockEntity crucible) {
                long packetSize = 1024;
                long targetTemperature = Math.min(1950, crucible.maximumTemperature() - 100);
                long requiredHeat = Math.max(0, targetTemperature - crucible.temperature())
                    * crucible.requiredHeatPerKelvin();
                long packets = Math.max(1, (requiredHeat + packetSize - 1) / packetSize);
                crucible.receivePacket(
                    new EnergyPacket(EnergyKind.HEAT, packetSize, packets),
                    Direction.UP,
                    false
                );
            }
            if (level.getBlockEntity(smallCruciblePos) instanceof SmallCrucibleBlockEntity crucible) {
                long packetSize = 512;
                long targetTemperature = Math.min(2100, crucible.maximumTemperature() - 100);
                long requiredHeat = Math.max(0, targetTemperature - crucible.temperature())
                    * crucible.requiredHeatPerKelvin();
                long packets = Math.max(1, (requiredHeat + packetSize - 1) / packetSize);
                crucible.receivePacket(new EnergyPacket(EnergyKind.HEAT, packetSize, packets),
                    Direction.UP, false);
            }
        }
        if (ticks == 31) {
            ServerLevel level = event.getServer().overworld();
            if (level.getBlockEntity(smallCruciblePos) instanceof SmallCrucibleBlockEntity crucible) {
                crucible.receivePacket(
                    new EnergyPacket(EnergyKind.KINETIC, 16, 10_000),
                    Direction.UP,
                    false
                );
            }
        }
        if (ticks == 120) {
            ServerLevel level = event.getServer().overworld();
            if (level.getBlockEntity(smallCruciblePos) instanceof SmallCrucibleBlockEntity crucible
                && level.getBlockEntity(moldPos) instanceof MoldBlockEntity mold) {
                castingPoured = crucible.pourInto(mold);
                if (castingPoured) {
                    long before = mold.contentTemperature();
                    mold.receivePacket(
                        new EnergyPacket(EnergyKind.CRYO, 2048, 2),
                        Direction.UP,
                        false
                    );
                    castingCooled = mold.contentTemperature() < before;
                    mold.forceSolidifyForTesting();
                }
            }
        }
        if (ticks == 121) {
            ServerLevel level = event.getServer().overworld();
            if (level.getBlockEntity(faucetPos) instanceof FaucetBlockEntity faucet) {
                faucetPoured = faucet.tryPour();
            }
            if (level.getBlockEntity(faucetMoldPos) instanceof MoldBlockEntity mold) {
                mold.forceSolidifyForTesting();
            }
        }
        if (ticks == 20) {
            ServerLevel level = event.getServer().overworld();
            if (level.getBlockEntity(cruciblePos) instanceof CrucibleBlockEntity crucible) {
                burningHeatObserved = crucible.storedHeat() > 0 || crucible.temperature() > 293;
            }
            if (level.getBlockEntity(burningBoxPos) instanceof BurningBoxBlockEntity box) {
                burningBoxLitObserved = box.isBurning()
                    && level.getBlockState(burningBoxPos).getValue(BurningBoxBlock.LIT);
            }
            if (level.getBlockEntity(smallCruciblePos) instanceof SmallCrucibleBlockEntity crucible) {
                smallDropObserved = crucible.contents().stream()
                    .anyMatch(content -> "copper".equals(content.material()));
            }
        }
        if (ticks < 360) {
            return;
        }
        ServerLevel level = event.getServer().overworld();
        boolean basicPass = false;
        int basicCount = 0;
        if (level.getBlockEntity(basicCrusherPos) instanceof BasicCrusherBlockEntity basic) {
            basicPass = basic.peekOutputForTesting().is(Items.GRAVEL);
            basicCount = basic.peekOutputForTesting().getCount();
        }
        boolean crankPass = false;
        int crankCount = 0;
        if (level.getBlockEntity(crankCrusherPos) instanceof BasicCrusherBlockEntity crankCrusher) {
            crankPass = crankCrusher.peekOutputForTesting().is(Items.GRAVEL);
            crankCount = crankCrusher.peekOutputForTesting().getCount();
        }
        boolean genericPass = false;
        int genericCount = 0;
        if (level.getBlockEntity(genericMachinePos) instanceof BasicMachineBlockEntity machine) {
            ItemStack output = machine.peekOutputForTesting();
            MaterialPart part = output.get(GTDataComponents.MATERIAL_PART.get());
            genericPass = part != null
                && "iron".equals(part.material())
                && "crushedTiny".equals(part.prefix());
            genericCount = output.getCount();
        }
        boolean cruciblePass = false;
        boolean largeDropAbsorbed = false;
        boolean largeFillPass = false;
        long crucibleTemperature = 0;
        if (level.getBlockEntity(cruciblePos) instanceof CrucibleBlockEntity crucible) {
            crucibleTemperature = crucible.temperature();
            largeDropAbsorbed = crucible.contents().stream()
                .anyMatch(content -> "copper".equals(content.material()));
            largeFillPass = crucible.fillLevel() > 0
                && level.getBlockState(cruciblePos).getValue(CrucibleBlock.FILL)
                    == crucible.fillLevel();
            cruciblePass = burningHeatObserved
                && burningBoxLitObserved
                && largeFillPass
                && crucible.isFormed()
                && crucibleTemperature >= 1811
                && crucible.moltenUnits() >= GTMaterialGameplayRegistry.MATERIAL_UNIT;
        }
        boolean smallCruciblePass = false;
        boolean smallDropAbsorbed = false;
        boolean smallSteelmakingPass = false;
        boolean smallFillPass = false;
        long smallCrucibleTemperature = 0;
        if (level.getBlockEntity(smallCruciblePos) instanceof SmallCrucibleBlockEntity crucible) {
            smallCrucibleTemperature = crucible.temperature();
            smallDropAbsorbed = crucible.contents().stream()
                .anyMatch(content -> "copper".equals(content.material())
                    || "annealedcopper".equals(content.material()))
                || smallDropObserved;
            smallSteelmakingPass = crucible.contents().stream()
                .anyMatch(content -> "steel".equals(content.material()));
            smallFillPass = crucible.fillLevel() > 0
                && level.getBlockState(smallCruciblePos).getValue(SmallCrucibleBlock.FILL)
                    == crucible.fillLevel();
            smallCruciblePass = smallDropAbsorbed
                && smallFillPass
                && smallCrucibleTemperature >= 1811
                && crucible.moltenUnits() >= GTMaterialGameplayRegistry.MATERIAL_UNIT;
        }
        boolean sensorPass = level.getBlockEntity(temperatureSensorPos) instanceof TemperatureSensorBlockEntity sensor
            && sensor.temperatureKelvin() == smallCrucibleTemperature
            && sensor.analogSignal() > 0;
        if (level.getBlockEntity(moldPos) instanceof MoldBlockEntity mold) {
            ItemStack output = mold.takeOutputForTesting();
            MaterialPart part = output.get(GTDataComponents.MATERIAL_PART.get());
            castingOutputPass = part != null
                && "ingot".equals(part.prefix())
                && GTMaterialGameplayRegistry.material(part.material()) != null;
        }
        if (level.getBlockEntity(faucetMoldPos) instanceof MoldBlockEntity mold) {
            ItemStack output = mold.takeOutputForTesting();
            MaterialPart part = output.get(GTDataComponents.MATERIAL_PART.get());
            faucetOutputPass = part != null
                && "ingot".equals(part.prefix())
                && GTMaterialGameplayRegistry.material(part.material()) != null;
        }
        if (level.getBlockEntity(controllerPos) instanceof LargeCrusherBlockEntity crusher) {
            ItemStack output = crusher.peekOutputForTesting();
            if (output.is(Items.GRAVEL) && basicPass && crankPass && genericPass
                && genericCapabilityInserted && cruciblePass && largeDropAbsorbed
                && smallCruciblePass && smallDropAbsorbed
                && smallSteelmakingPass && sensorPass && creativeCatalogPass
                && castingPoured && castingCooled && castingOutputPass
                && faucetPoured && faucetOutputPass && alloyCatalogPass) {
                GT6Neo.LOGGER.info(
                    "GT6_RUNTIME_SELFTEST_PASS RU={} EU_KU={} CRANK_KU={} GENERIC_KU={} CRUCIBLE={}K SMALL_CRUCIBLE={}K CRUCIBLE_DROPS={}/{} FILL={}/{} STEELMAKING={} CASTING={}/{}/{} FAUCET={}/{} ALLOYS={} BURNING_BOX_LIT={}",
                    output.getCount(),
                    basicCount,
                    crankCount,
                    genericCount,
                    crucibleTemperature,
                    smallCrucibleTemperature,
                    largeDropAbsorbed,
                    smallDropAbsorbed,
                    largeFillPass,
                    smallFillPass,
                    smallSteelmakingPass,
                    castingPoured,
                    castingCooled,
                    castingOutputPass,
                    faucetPoured,
                    faucetOutputPass,
                    GTAlloyRegistry.resolvedRecipeCount(),
                    burningBoxLitObserved
                );
            } else {
                GT6Neo.LOGGER.error(
                    "GT6_RUNTIME_SELFTEST_FAIL formed={} RU output={} EU/KU pass={} CRANK/KU pass={} GENERIC/KU pass={} GENERIC_CAPABILITY={} CRUCIBLE pass={} temperature={} LARGE_DROP={} LARGE_FILL={} SMALL_CRUCIBLE pass={} temperature={} SMALL_DROP={} SMALL_FILL={} STEELMAKING={} SENSOR pass={} CASTING={}/{}/{} FAUCET={}/{} ALLOYS={} BURNING_BOX_LIT={} CREATIVE_CATALOG={}",
                    crusher.isFormed(),
                    output,
                    basicPass,
                    crankPass,
                    genericPass,
                    genericCapabilityInserted,
                    cruciblePass,
                    crucibleTemperature,
                    largeDropAbsorbed,
                    largeFillPass,
                    smallCruciblePass,
                    smallCrucibleTemperature,
                    smallDropAbsorbed,
                    smallFillPass,
                    smallSteelmakingPass,
                    sensorPass,
                    castingPoured,
                    castingCooled,
                    castingOutputPass,
                    faucetPoured,
                    faucetOutputPass,
                    alloyCatalogPass,
                    burningBoxLitObserved,
                    creativeCatalogPass
                );
            }
        } else {
            GT6Neo.LOGGER.error("GT6_RUNTIME_SELFTEST_FAIL missing controller block entity");
        }
        armed = false;
        event.getServer().halt(false);
    }

    private static boolean validateCreativeCatalog(MinecraftServer server) {
        CreativeModeTab.ItemDisplayParameters parameters = new CreativeModeTab.ItemDisplayParameters(
            server.overworld().enabledFeatures(),
            true,
            server.registryAccess()
        );
        int demo = creativeSize("demo", parameters);
        int materials = creativeSize("materials", parameters);
        int blocks = creativeSize("material_blocks", parameters);
        int machines = creativeSize("machine_catalog", parameters);
        int multiblocks = creativeSize("multiblocks_and_heat", parameters);
        int tools = creativeSize("tools_and_energy", parameters);
        int smallCrucibles = creativeSize("smelting_crucibles", parameters);
        int casting = creativeSize("casting", parameters);
        long smallCrucibleHeatData = GTMachineRegistry.machines().stream()
            .filter(machine -> "Smelting Crucibles".equals(machine.category()))
            .filter(machine -> GTMaterialGameplayRegistry.material(machine.material_slug()) != null)
            .count();
        GT6Neo.LOGGER.info(
            "GT6 creative catalog: demo={} materials={} blocks={} machines={} multiblocks={} tools={} small_crucibles={} casting={} small_crucible_heat_data={}",
            demo,
            materials,
            blocks,
            machines,
            multiblocks,
            tools,
            smallCrucibles,
            casting,
            smallCrucibleHeatData
        );
        return demo >= 25
            && materials >= 10_000
            && blocks >= 12_000
            && machines >= 251
            && multiblocks >= 30
            && tools >= 10
            && smallCrucibles >= 40
            && casting >= 1_200
            && smallCrucibleHeatData == 39;
    }

    private static int creativeSize(
        String path,
        CreativeModeTab.ItemDisplayParameters parameters
    ) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("gregtech", path);
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(id);
        if (tab == null) {
            return 0;
        }
        tab.buildContents(parameters);
        return tab.getDisplayItems().size();
    }

    private static void buildStructure(ServerLevel level) {
        for (int x = -3; x <= 3; x++) {
            for (int y = -2; y <= 4; y++) {
                for (int z = -5; z <= 2; z++) {
                    level.setBlockAndUpdate(controllerPos.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }

        for (int x = -2; x <= 2; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = 0; z <= 4; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    BlockPos target = controllerPos.offset(offset(Direction.SOUTH, x, y, z));
                    if (y == 0) {
                        level.setBlockAndUpdate(target, GTBlocks.TUNGSTENSTEEL_WALL.get().defaultBlockState());
                    } else if (isInterior(x, z)) {
                        level.setBlockAndUpdate(target, GTBlocks.CRUSHER_WHEELS.get().defaultBlockState());
                    } else if (isBoundary(x, z)) {
                        level.setBlockAndUpdate(target, GTBlocks.TUNGSTENSTEEL_WALL.get().defaultBlockState());
                    }
                }
            }
        }

        BlockPos energyPort = controllerPos.offset(offset(Direction.SOUTH, 0, 1, 0));
        BlockPos itemInput = controllerPos.offset(offset(Direction.SOUTH, 1, 1, 0));
        largeItemInputPortPos = itemInput;
        BlockPos itemOutput = controllerPos.offset(offset(Direction.SOUTH, -1, 0, 0));
        setPort(level, energyPort, MachinePortMode.ENERGY_IN);
        setPort(level, itemInput, MachinePortMode.ITEM_IN);
        setPort(level, itemOutput, MachinePortMode.ITEM_OUT);

        BlockState controllerState = GTBlocks.LARGE_CRUSHER.get().defaultBlockState()
            .setValue(LargeCrusherBlock.FACING, Direction.SOUTH)
            .setValue(LargeCrusherBlock.FORMED, false);
        level.setBlockAndUpdate(controllerPos, controllerState);

        if (level.getBlockEntity(controllerPos) instanceof LargeCrusherBlockEntity crusher) {
            crusher.forceStructureCheck();
        }
    }

    private static void buildKineticChain(ServerLevel level) {
        for (int x = -2; x <= 6; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -4; z <= 2; z++) {
                    level.setBlockAndUpdate(basicCrusherPos.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }

        level.setBlockAndUpdate(basicCrusherPos, GTBlocks.BASIC_CRUSHER.get().defaultBlockState());
        BlockPos enginePos = basicCrusherPos.north();
        level.setBlockAndUpdate(
            enginePos,
            GTBlocks.ELECTRIC_ENGINE.get().defaultBlockState()
                .setValue(KineticConverterBlock.FACING, Direction.SOUTH)
        );
        BlockPos generatorPos = enginePos.north();
        level.setBlockAndUpdate(generatorPos, GTBlocks.LV_GENERATOR.get().defaultBlockState());

        if (level.getBlockEntity(generatorPos) instanceof GeneratorBlockEntity generator) {
            generator.insertFuel(new ItemStack(Items.COAL, 3));
        }
        if (level.getBlockEntity(basicCrusherPos) instanceof BasicCrusherBlockEntity crusher) {
            crusher.insertItem(new ItemStack(Items.COBBLESTONE, 3));
        }

        BlockPos rotationEnginePos = crankCrusherPos.north();
        BlockPos axlePos = rotationEnginePos.north();
        level.setBlockAndUpdate(crankCrusherPos, GTBlocks.BASIC_CRUSHER.get().defaultBlockState());
        level.setBlockAndUpdate(
            rotationEnginePos,
            GTBlocks.ROTATION_ENGINE.get().defaultBlockState()
                .setValue(KineticConverterBlock.FACING, Direction.SOUTH)
        );
        level.setBlockAndUpdate(axlePos, GTBlocks.AXLE.get().defaultBlockState());
        level.setBlockAndUpdate(
            crankPos,
            GTBlocks.MANUAL_CRANK.get().defaultBlockState()
                .setValue(gregtech.neo.block.ManualCrankBlock.FACING, Direction.SOUTH)
        );
        if (level.getBlockEntity(crankCrusherPos) instanceof BasicCrusherBlockEntity crankCrusher) {
            crankCrusher.insertItem(new ItemStack(Items.COBBLESTONE, 3));
        }
    }

    private static void buildGenericMachine(ServerLevel level) {
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    level.setBlockAndUpdate(genericMachinePos.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        level.setBlockAndUpdate(genericMachinePos, GTBlocks.BASIC_MACHINE.get().defaultBlockState());
        if (level.getBlockEntity(genericMachinePos) instanceof BasicMachineBlockEntity machine) {
            machine.setPart(new MachinePart(1300, "Basic Machines"));
            IItemHandler input = level.getCapability(
                Capabilities.ItemHandler.BLOCK,
                genericMachinePos,
                Direction.UP
            );
            if (input != null) {
                ItemStack remainder = input.insertItem(
                    0,
                    GTMaterialItems.createPart("iron", "rawOreChunk", 3),
                    false
                );
                genericCapabilityInserted = remainder.isEmpty();
            }
        }
    }

    private static void buildCrucible(ServerLevel level) {
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 3; y++) {
                for (int z = -2; z <= 2; z++) {
                    level.setBlockAndUpdate(cruciblePos.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 0; y <= 2; y++) {
                    if (x == 0 && z == 0) {
                        continue;
                    }
                    level.setBlockAndUpdate(
                        cruciblePos.offset(x, y, z),
                        GTBlocks.CRUCIBLE_WALL.get().defaultBlockState()
                    );
                }
            }
        }
        level.setBlockAndUpdate(cruciblePos, GTBlocks.CRUCIBLE.get().defaultBlockState());
        if (level.getBlockEntity(cruciblePos) instanceof CrucibleBlockEntity crucible) {
            crucible.forceStructureCheck();
            crucible.addMaterial("iron", "ingot", 3);
        }

        level.setBlockAndUpdate(burningBoxPos, GTBlocks.BURNING_BOX.get().defaultBlockState());
        if (level.getBlockEntity(burningBoxPos) instanceof BurningBoxBlockEntity box) {
            box.setPart(new MachinePart(528, "Burning Boxes"));
            box.insertFuel(new ItemStack(Items.COAL, 8));
            box.ignite();
        }
    }

    private static void buildSmallCrucible(ServerLevel level) {
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    level.setBlockAndUpdate(smallCruciblePos.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        level.setBlockAndUpdate(smallCruciblePos, GTBlocks.SMALL_CRUCIBLE.get().defaultBlockState());
        if (level.getBlockEntity(smallCruciblePos) instanceof SmallCrucibleBlockEntity crucible) {
            crucible.setPart(new MachinePart(267, "Smelting Crucibles"));
            crucible.addMaterial("iron", "ingot", 3);
        }
        level.setBlockAndUpdate(
            temperatureSensorPos,
            GTBlocks.TEMPERATURE_SENSOR.get().defaultBlockState()
        );
    }

    private static void buildCastingMold(ServerLevel level) {
        level.setBlockAndUpdate(moldPos, GTBlocks.MOLD.get().defaultBlockState());
        if (level.getBlockEntity(moldPos) instanceof MoldBlockEntity mold) {
            mold.configure(new CastingPart(
                "steel",
                1072,
                GTMoldRegistry.ingotMask(),
                false
            ));
        }
        level.setBlockAndUpdate(
            faucetPos,
            GTBlocks.FAUCET.get().defaultBlockState()
                .setValue(FaucetBlock.FACING, Direction.NORTH)
        );
        if (level.getBlockEntity(faucetPos) instanceof FaucetBlockEntity faucet) {
            faucet.configure(new CastingPart("steel", 1722, 0, false));
        }
        level.setBlockAndUpdate(faucetMoldPos, GTBlocks.MOLD.get().defaultBlockState());
        if (level.getBlockEntity(faucetMoldPos) instanceof MoldBlockEntity mold) {
            mold.configure(new CastingPart(
                "steel",
                1072,
                GTMoldRegistry.ingotMask(),
                false
            ));
        }
    }

    private static void spawnCrucibleDropTests(ServerLevel level) {
        ItemEntity largeDrop = new ItemEntity(
            level,
            cruciblePos.getX() + 0.5,
            cruciblePos.getY() + 1.25,
            cruciblePos.getZ() + 0.5,
            GTMaterialItems.createPart("copper", "ingot", 1)
        );
        largeDrop.setNoGravity(true);
        largeDrop.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(largeDrop);

        ItemEntity smallDrop = new ItemEntity(
            level,
            smallCruciblePos.getX() + 0.5,
            smallCruciblePos.getY() + 0.75,
            smallCruciblePos.getZ() + 0.5,
            GTMaterialItems.createPart("copper", "ingot", 1)
        );
        smallDrop.setNoGravity(true);
        smallDrop.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(smallDrop);
    }

    private static IItemHandler levelCapability(
        ServerTickEvent.Post event,
        BlockPos pos
    ) {
        return event.getServer().overworld().getCapability(
            Capabilities.ItemHandler.BLOCK,
            pos,
            Direction.UP
        );
    }

    private static void setPort(ServerLevel level, BlockPos pos, MachinePortMode mode) {
        level.setBlockAndUpdate(
            pos,
            GTBlocks.MACHINE_PORT.get().defaultBlockState().setValue(MachinePortBlock.MODE, mode)
        );
    }

    private static boolean isBoundary(int x, int z) {
        return Math.abs(x) == 2 || z == 0 || z == 4;
    }

    private static boolean isInterior(int x, int z) {
        return Math.abs(x) <= 1 && z >= 1 && z <= 3;
    }

    private static BlockPos offset(Direction facing, int localX, int localY, int localZ) {
        int dx = localX;
        int dy = localY;
        int dz = -localZ;
        return switch (facing) {
            case SOUTH -> new BlockPos(dx, dy, dz);
            case NORTH -> new BlockPos(-dx, dy, -dz);
            case EAST -> new BlockPos(-dz, dy, -dx);
            case WEST -> new BlockPos(dz, dy, dx);
            default -> new BlockPos(dx, dy, dz);
        };
    }
}
