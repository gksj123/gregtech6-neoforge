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
import gregtech.neo.block.KineticConverterBlockEntity;
import gregtech.neo.block.LargeBoilerBlock;
import gregtech.neo.block.LargeBoilerBlockEntity;
import gregtech.neo.block.LargeBoilerWallBlockEntity;
import gregtech.neo.block.LargeCrusherBlock;
import gregtech.neo.block.LargeCrusherBlockEntity;
import gregtech.neo.block.MachinePortBlock;
import gregtech.neo.block.MachinePortMode;
import gregtech.neo.block.MechanicalEnergyNetwork;
import gregtech.neo.block.SmallCrucibleBlockEntity;
import gregtech.neo.block.SmallCrucibleBlock;
import gregtech.neo.block.SteamBoilerBlockEntity;
import gregtech.neo.block.SteamEngineBlock;
import gregtech.neo.block.SteamEngineBlockEntity;
import gregtech.neo.block.SteamPipeBlockEntity;
import gregtech.neo.block.SteamTurbineBlock;
import gregtech.neo.block.SteamTurbineBlockEntity;
import gregtech.neo.block.TemperatureSensorBlockEntity;
import gregtech.neo.block.HeatTransmitterBlockEntity;
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
import gregtech.neo.registry.GTItems;
import gregtech.neo.registry.GTBlocks;
import gregtech.neo.recipe.GTAlloyRegistry;
import gregtech.neo.fluid.GTFluidUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
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
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

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
    private static BlockPos interactionMoldPos;
    private static BlockPos autoMoldPos;
    private static BlockPos autoCruciblePos;
    private static BlockPos steamTestPos;
    private static BlockPos turbineTestPos;
    private static BlockPos largeBoilerTestPos;
    private static BlockPos rightClickMoldPos;
    private static BlockPos largeWallMoldPos;
    private static boolean genericCapabilityInserted;
    private static boolean burningHeatObserved;
    private static boolean burningBoxLitObserved;
    private static boolean crucibleMeltObserved;
    private static boolean creativeCatalogPass;
    private static boolean castingPoured;
    private static boolean castingOutputPass;
    private static boolean castingCooled;
    private static boolean alloyCatalogPass;
    private static boolean smallDropObserved;
    private static boolean faucetPoured;
    private static boolean faucetOutputPass;
    private static boolean moldInteractionPass;
    private static boolean moldAutoPullPass;
    private static boolean steamFlowPass;
    private static boolean steamNoVoidPass;
    private static boolean steamTurbinePass;
    private static boolean steamLargeBoilerPass;
    private static boolean steamLargeExplosionPass;
    private static boolean castingRecipePass;
    private static boolean rightClickMoldPass;
    private static boolean largeWallMoldPass;
    private static boolean moldShapeOutputPass;

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
        crucibleMeltObserved = false;
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
        moldInteractionPass = false;
        moldAutoPullPass = false;
        steamFlowPass = false;
        steamNoVoidPass = false;
        steamTurbinePass = false;
        steamLargeBoilerPass = false;
        steamLargeExplosionPass = false;
        castingRecipePass = false;
        rightClickMoldPass = false;
        largeWallMoldPass = false;
        moldShapeOutputPass = false;
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
        interactionMoldPos = controllerPos.offset(30, 0, 0);
        autoCruciblePos = controllerPos.offset(40, 0, 0);
        autoMoldPos = autoCruciblePos.north();
        steamTestPos = controllerPos.offset(-35, 0, -15);
        turbineTestPos = steamTestPos.offset(20, 0, 0);
        largeBoilerTestPos = steamTestPos.offset(0, 0, 30);
        rightClickMoldPos = controllerPos.offset(50, 0, 10);
        largeWallMoldPos = cruciblePos.offset(-2, 1, 0);
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
        long castingRecipes = server.getRecipeManager().getRecipeIds()
            .filter(id -> id.getNamespace().equals(GT6Neo.MOD_ID))
            .filter(id -> id.getPath().startsWith("mold_")
                || id.getPath().startsWith("faucet_")
                || id.getPath().startsWith("ceramic_mold_"))
            .count();
        castingRecipePass = castingRecipes == 107;
        GT6Neo.LOGGER.info(
            "GT6 runtime self-test: casting recipes={} pass={}",
            castingRecipes,
            castingRecipePass
        );
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
            moldInteractionPass = runMoldInteractionTest(event.getServer().overworld());
            moldAutoPullPass = runMoldAutoPullTest(event.getServer().overworld());
            boolean[] steamResults = runSteamTests(event.getServer().overworld());
            steamFlowPass = steamResults[0];
            steamNoVoidPass = steamResults[1];
            steamTurbinePass = steamResults[2];
            steamLargeBoilerPass = steamResults[3];
            steamLargeExplosionPass = steamResults[4];
            rightClickMoldPass = runMoldRightClickPourTest(
                event.getServer().overworld()
            );
            moldShapeOutputPass = runAllMoldShapeOutputTest(
                event.getServer().overworld()
            );
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
                castingPoured = crucible.pourInto(mold, Direction.SOUTH);
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
            if (level.getBlockEntity(cruciblePos) instanceof CrucibleBlockEntity crucible
                && level.getBlockEntity(largeWallMoldPos) instanceof MoldBlockEntity mold) {
                long targetTemperature = 2100;
                long requiredHeat = Math.max(
                    0,
                    targetTemperature - crucible.temperature()
                ) * crucible.requiredHeatPerKelvin();
                if (requiredHeat > 0) {
                    long packetSize = 1024;
                    long packets = Math.max(1, (requiredHeat + packetSize - 1) / packetSize);
                    crucible.receivePacket(
                        new EnergyPacket(EnergyKind.HEAT, packetSize, packets),
                        Direction.UP,
                        false
                    );
                }
                crucibleMeltObserved = crucible.temperature() >= 1811
                    && crucible.moltenUnits() >= GTMaterialGameplayRegistry.MATERIAL_UNIT;
                Player player = FakePlayerFactory.getMinecraft(level);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                BlockHitResult wallMoldHit = new BlockHitResult(
                    new Vec3(
                        largeWallMoldPos.getX() + 0.9,
                        largeWallMoldPos.getY() + 0.2,
                        largeWallMoldPos.getZ() + 0.5
                    ),
                    Direction.UP,
                    largeWallMoldPos,
                    false
                );
                level.getBlockState(largeWallMoldPos)
                    .useWithoutItem(level, player, wallMoldHit);
                largeWallMoldPass = mold.contentUnits() > 0;
                mold.forceSolidifyForTesting();
                largeWallMoldPass = largeWallMoldPass && mold.outputCount() > 0;
                mold.takeOutputForTesting();
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
                && crucibleMeltObserved;
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
                && faucetPoured && faucetOutputPass && alloyCatalogPass
                && moldInteractionPass && moldAutoPullPass
                && steamFlowPass && steamNoVoidPass && steamTurbinePass
                && steamLargeBoilerPass && steamLargeExplosionPass
                && castingRecipePass && rightClickMoldPass
                && largeWallMoldPass && moldShapeOutputPass) {
                GT6Neo.LOGGER.info(
                    "GT6_RUNTIME_SELFTEST_PASS RU={} EU_KU={} CRANK_KU={} GENERIC_KU={} CRUCIBLE={}K SMALL_CRUCIBLE={}K CRUCIBLE_DROPS={}/{} FILL={}/{} STEELMAKING={} CASTING={}/{}/{} FAUCET={}/{} ALLOYS={} BURNING_BOX_LIT={} MOLD_INTERACTION={} MOLD_AUTO={} STEAM_FLOW={} STEAM_NO_VOID={} STEAM_TURBINE={} STEAM_LARGE_BOILER={} STEAM_LARGE_EXPLOSION={} CASTING_RECIPES={} MOLD_RIGHT_CLICK={} LARGE_WALL_MOLD={} MOLD_SHAPES={}",
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
                    burningBoxLitObserved,
                    moldInteractionPass,
                    moldAutoPullPass,
                    steamFlowPass,
                    steamNoVoidPass,
                    steamTurbinePass,
                    steamLargeBoilerPass,
                    steamLargeExplosionPass,
                    castingRecipePass,
                    rightClickMoldPass,
                    largeWallMoldPass,
                    moldShapeOutputPass
                );
            } else {
                GT6Neo.LOGGER.error(
                    "GT6_RUNTIME_SELFTEST_FAIL formed={} RU output={} EU/KU pass={} CRANK/KU pass={} GENERIC/KU pass={} GENERIC_CAPABILITY={} CRUCIBLE pass={} temperature={} LARGE_DROP={} LARGE_FILL={} SMALL_CRUCIBLE pass={} temperature={} SMALL_DROP={} SMALL_FILL={} STEELMAKING={} SENSOR pass={} CASTING={}/{}/{} FAUCET={}/{} ALLOYS={} BURNING_BOX_LIT={} CREATIVE_CATALOG={} MOLD_INTERACTION={} MOLD_AUTO={} STEAM_FLOW={} STEAM_NO_VOID={} STEAM_TURBINE={} STEAM_LARGE_BOILER={} STEAM_LARGE_EXPLOSION={} CASTING_RECIPES={} MOLD_RIGHT_CLICK={} LARGE_WALL_MOLD={} MOLD_SHAPES={}",
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
                    creativeCatalogPass,
                    moldInteractionPass,
                    moldAutoPullPass,
                    steamFlowPass,
                    steamNoVoidPass,
                    steamTurbinePass,
                    steamLargeBoilerPass,
                    steamLargeExplosionPass,
                    castingRecipePass,
                    rightClickMoldPass,
                    largeWallMoldPass,
                    moldShapeOutputPass
                );
            }
        } else {
            GT6Neo.LOGGER.error("GT6_RUNTIME_SELFTEST_FAIL missing controller block entity");
        }
        armed = false;
        event.getServer().halt(false);
    }

    private static boolean runMoldInteractionTest(ServerLevel level) {
        level.removeBlock(interactionMoldPos, false);
        level.setBlockAndUpdate(interactionMoldPos, GTBlocks.MOLD.get().defaultBlockState());
        if (!(level.getBlockEntity(interactionMoldPos) instanceof MoldBlockEntity mold)) {
            GT6Neo.LOGGER.error("GT6 mold interaction test: missing mold block entity");
            return false;
        }
        mold.configure(new CastingPart("steel", 1072, GTMoldRegistry.ingotMask(), false));
        long down = mold.fillMold(
            "iron",
            mold.requiredUnits(),
            1800,
            Direction.DOWN
        );
        long filled = mold.fillMold(
            "iron",
            mold.requiredUnits(),
            1800,
            Direction.UP
        );
        Player player = FakePlayerFactory.getMinecraft(level);
        BlockHitResult hit = new BlockHitResult(
            Vec3.atCenterOf(interactionMoldPos).add(0, 0.5, 0),
            Direction.UP,
            interactionMoldPos,
            false
        );
        mold.forceSolidifyForTesting();
        GT6Neo.LOGGER.info(
            "GT6 mold interaction test: filled={} required={} mask=0x{} solid={} output={}",
            filled,
            mold.requiredUnits(),
            Integer.toHexString(mold.shapeMask()),
            mold.solidPrefix(),
            mold.outputCount()
        );
        level.getBlockState(interactionMoldPos).useWithoutItem(level, player, hit);
        GT6Neo.LOGGER.info(
            "GT6 mold interaction test: player inventory count={}",
            player.getInventory().getItem(0)
        );
        GT6Neo.LOGGER.info(
            "GT6 mold interaction test: after empty-hand content={} output={} temp={}",
            mold.contentUnits(),
            mold.outputCount(),
            mold.contentTemperature()
        );
        ItemStack chisel = new ItemStack(GTItems.CHISEL.get());
        level.getBlockState(interactionMoldPos).useItemOn(
            chisel,
            level,
            player,
            InteractionHand.MAIN_HAND,
            hit
        );
        ItemStack wrench = new ItemStack(GTItems.WRENCH.get());
        level.getBlockState(interactionMoldPos).useItemOn(
            wrench,
            level,
            player,
            InteractionHand.MAIN_HAND,
            hit
        );
        ItemStack monkey = new ItemStack(GTItems.MONKEY_WRENCH.get());
        level.getBlockState(interactionMoldPos).useItemOn(
            monkey,
            level,
            player,
            InteractionHand.MAIN_HAND,
            hit
        );
        level.getBlockState(interactionMoldPos).useItemOn(
            new ItemStack(GTItems.SOFT_HAMMER.get()),
            level,
            player,
            InteractionHand.MAIN_HAND,
            hit
        );
        level.getBlockState(interactionMoldPos).useItemOn(
            new ItemStack(GTItems.THERMOMETER.get()),
            level,
            player,
            InteractionHand.MAIN_HAND,
            hit
        );
        boolean picked = mold.outputCount() == 0
            && !player.getMainHandItem().isEmpty();

        // Non-tool side click still picks up a solidified output.
        mold.configure(new CastingPart("steel", 1072, GTMoldRegistry.ingotMask(), false));
        mold.fillMold("iron", mold.requiredUnits(), 1800, Direction.UP);
        mold.forceSolidifyForTesting();
        BlockHitResult sideHit = new BlockHitResult(
            Vec3.atCenterOf(interactionMoldPos).add(0, 0.1, 0.45),
            Direction.SOUTH,
            interactionMoldPos,
            false
        );
        level.getBlockState(interactionMoldPos).useItemOn(
            new ItemStack(Items.STICK),
            level,
            player,
            InteractionHand.MAIN_HAND,
            sideHit
        );
        boolean sidePicked = mold.outputCount() == 0;

        // Blank mold -> one chisel cell -> fallback nugget recipe.
        mold.configure(new CastingPart("steel", 1072, 0, false));
        boolean blank = mold.shapeMask() == 0;
        level.getBlockState(interactionMoldPos).useItemOn(
            chisel,
            level,
            player,
            InteractionHand.MAIN_HAND,
            hit
        );
        boolean carved = mold.shapeMask() != 0;
        long carvedRequired = mold.requiredUnits();
        long carvedFill = carvedRequired > 0
            ? mold.fillMold("iron", carvedRequired, 1800, Direction.UP)
            : 0;
        mold.forceSolidifyForTesting();
        boolean carvedOutput = mold.outputCount() > 0;
        mold.takeOutputForTesting();

        mold.configure(new CastingPart("steel", 1072, GTMoldRegistry.ingotMask(), false));
        long solidifyingFill = mold.fillMold(
            "water",
            mold.requiredUnits(),
            300,
            Direction.UP
        );
        mold.forceSolidifyForTesting();
        boolean solidifyingTarget = "ice".equals(mold.solidMaterial());
        mold.takeOutputForTesting();

        mold.configure(new CastingPart("steel", 1072, GTMoldRegistry.ingotMask(), false));
        boolean acidRejected = mold.fillMold(
            "sulfuricacid",
            mold.requiredUnits(),
            1800,
            Direction.UP
        ) == 0;

        mold.fillMold("iron", mold.requiredUnits(), 1800, Direction.UP);
        mold.forceSolidifyForTesting();
        mold.receivePacket(
            new EnergyPacket(EnergyKind.CRYO, 2048, 1),
            Direction.UP,
            false
        );
        IItemHandler moldOutput = level.getCapability(
            Capabilities.ItemHandler.BLOCK,
            interactionMoldPos,
            Direction.NORTH
        );
        ItemStack simulatedOutput = moldOutput == null
            ? ItemStack.EMPTY
            : moldOutput.extractItem(0, 1, true);
        ItemStack extractedOutput = moldOutput == null
            ? ItemStack.EMPTY
            : moldOutput.extractItem(0, 1, false);
        boolean automationOutput = !simulatedOutput.isEmpty()
            && !extractedOutput.isEmpty()
            && mold.outputCount() == 0;
        GT6Neo.LOGGER.info(
            "GT6 mold interaction test: completed mask=0x{} content={} output={} picked={} sidePicked={} downRejected={} blank={} carved={} carvedFill={} carvedOutput={} solidifying={} acidRejected={} automation={}",
            Integer.toHexString(mold.shapeMask()),
            mold.contentUnits(),
            mold.outputCount(),
            picked,
            sidePicked,
            down == 0,
            blank,
            carved,
            carvedFill,
            carvedOutput,
            solidifyingTarget,
            acidRejected,
            automationOutput
        );
        return filled > 0 && down == 0 && picked && sidePicked
            && blank && carved && carvedOutput
            && solidifyingFill > 0 && solidifyingTarget
            && acidRejected && automationOutput;
    }

    private static boolean runMoldAutoPullTest(ServerLevel level) {
        for (int x = -3; x <= 3; x++) {
            for (int y = -2; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    level.setBlockAndUpdate(autoCruciblePos.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        level.setBlockAndUpdate(autoCruciblePos, GTBlocks.SMALL_CRUCIBLE.get().defaultBlockState());
        if (!(level.getBlockEntity(autoCruciblePos) instanceof SmallCrucibleBlockEntity crucible)) {
            GT6Neo.LOGGER.error("GT6 mold auto-pull test: missing crucible");
            return false;
        }
        crucible.setPart(new MachinePart(267, "Smelting Crucibles"));
        crucible.addMaterial("iron", "ingot", 3);
        long target = Math.min(2000, crucible.maximumTemperature() - 50);
        long required = Math.max(0, target - crucible.temperature())
            * crucible.requiredHeatPerKelvin();
        long packetSize = 512;
        long packets = Math.max(1, (required + packetSize - 1) / packetSize);
        crucible.receivePacket(
            new EnergyPacket(EnergyKind.HEAT, packetSize, packets),
            Direction.UP,
            false
        );
        crucible.tick();

        level.setBlockAndUpdate(autoMoldPos, GTBlocks.MOLD.get().defaultBlockState());
        if (!(level.getBlockEntity(autoMoldPos) instanceof MoldBlockEntity mold)) {
            GT6Neo.LOGGER.error("GT6 mold auto-pull test: missing mold");
            return false;
        }
        mold.configure(new CastingPart("steel", 1072, GTMoldRegistry.ingotMask(), false));
        Player player = FakePlayerFactory.getMinecraft(level);
        BlockHitResult hit = new BlockHitResult(
            new Vec3(autoMoldPos.getX() + 0.5, autoMoldPos.getY() + 0.2, autoMoldPos.getZ() + 0.9),
            Direction.UP,
            autoMoldPos,
            false
        );
        level.getBlockState(autoMoldPos).useItemOn(
            new ItemStack(GTItems.MONKEY_WRENCH.get()),
            level,
            player,
            InteractionHand.MAIN_HAND,
            hit
        );
        mold.tick();
        boolean pass = mold.contentUnits() > 0 || mold.outputCount() > 0;
        GT6Neo.LOGGER.info(
            "GT6 mold auto-pull test: temp={} directions={} content={} output={} pass={}",
            crucible.temperature(),
            Integer.toBinaryString(mold.autoPullDirections()),
            mold.contentUnits(),
            mold.outputCount(),
            pass
        );
        level.setBlockAndUpdate(autoMoldPos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(autoCruciblePos, Blocks.AIR.defaultBlockState());
        return pass;
    }

    private static boolean runMoldRightClickPourTest(ServerLevel level) {
        clearTestVolume(level, rightClickMoldPos, 4, 5, 4);
        BlockPos cruciblePos = rightClickMoldPos.south();
        level.setBlockAndUpdate(
            cruciblePos,
            GTBlocks.SMALL_CRUCIBLE.get().defaultBlockState()
        );
        level.setBlockAndUpdate(
            rightClickMoldPos,
            GTBlocks.MOLD.get().defaultBlockState()
        );
        if (!(level.getBlockEntity(cruciblePos) instanceof SmallCrucibleBlockEntity crucible)
            || !(level.getBlockEntity(rightClickMoldPos) instanceof MoldBlockEntity mold)) {
            return false;
        }
        crucible.setPart(new MachinePart(267, "Smelting Crucibles"));
        crucible.addMaterial("iron", "ingot", 3);
        long requiredHeat = Math.max(
            0,
            2000 - crucible.temperature()
        ) * crucible.requiredHeatPerKelvin();
        long packetSize = 512;
        long packets = Math.max(1, (requiredHeat + packetSize - 1) / packetSize);
        crucible.receivePacket(
            new EnergyPacket(EnergyKind.HEAT, packetSize, packets),
            Direction.UP,
            false
        );
        crucible.tick();
        mold.configure(new CastingPart(
            "steel",
            1072,
            GTMoldRegistry.ingotMask(),
            false
        ));
        Player player = FakePlayerFactory.getMinecraft(level);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        BlockHitResult hit = new BlockHitResult(
            new Vec3(
                rightClickMoldPos.getX() + 0.5,
                rightClickMoldPos.getY() + 0.2,
                rightClickMoldPos.getZ() + 0.9
            ),
            Direction.UP,
            rightClickMoldPos,
            false
        );
        level.getBlockState(rightClickMoldPos).useWithoutItem(level, player, hit);
        boolean poured = mold.contentUnits() > 0;
        if (poured) {
            mold.receivePacket(
                new EnergyPacket(EnergyKind.CRYO, 2048, 1),
                Direction.UP,
                false
            );
            mold.tick();
        }
        boolean solidified = mold.outputCount() > 0;
        GT6Neo.LOGGER.info(
            "GT6 mold right-click test: crucibleTemp={} poured={} moldTemp={} output={} pass={}",
            crucible.temperature(),
            poured,
            mold.contentTemperature(),
            mold.outputCount(),
            poured && solidified
        );
        mold.takeOutputForTesting();
        clearTestVolume(level, rightClickMoldPos, 4, 5, 4);
        return poured && solidified;
    }

    private static boolean runAllMoldShapeOutputTest(ServerLevel level) {
        BlockPos pos = controllerPos.offset(60, 0, 0);
        clearTestVolume(level, pos, 3, 3, 3);
        level.setBlockAndUpdate(pos, GTBlocks.MOLD.get().defaultBlockState());
        if (!(level.getBlockEntity(pos) instanceof MoldBlockEntity mold)) {
            return false;
        }
        boolean pass = true;
        int checked = 0;
        for (GTMoldRegistry.MoldShape shape : GTMoldRegistry.canonicalShapes()) {
            mold.configure(new CastingPart(
                "steel",
                1072,
                shape.mask(),
                false
            ));
            long required = mold.requiredUnits();
            long fill = mold.fillMold("iron", required, 1800, Direction.UP);
            mold.forceSolidifyForTesting();
            ItemStack output = mold.takeOutputForTesting();
            MaterialPart part = output.get(GTDataComponents.MATERIAL_PART.get());
            boolean valid = fill > 0
                && part != null
                && "iron".equals(part.material())
                && shape.prefix().equals(part.prefix())
                && output.getCount() > 0;
            if (!valid) {
                GT6Neo.LOGGER.error(
                    "GT6 mold shape output failed: shape={} mask=0x{} expectedPrefix={} fill={} output={} part={}",
                    shape.name(),
                    Integer.toHexString(shape.mask()),
                    shape.prefix(),
                    fill,
                    output,
                    part
                );
                pass = false;
            }
            checked++;
        }
        clearTestVolume(level, pos, 3, 3, 3);
        GT6Neo.LOGGER.info(
            "GT6 mold shape output test: checked={} pass={}",
            checked,
            pass
        );
        return pass && checked == 30;
    }

    private static boolean[] runSteamTests(ServerLevel level) {
        clearTestVolume(level, steamTestPos, 8, 6, 8);
        BlockPos boilerPos = steamTestPos;
        BlockPos pipePos = boilerPos.above();
        BlockPos enginePos = pipePos.above();
        BlockPos crusherPos = enginePos.above();
        BlockPos engineWaterSinkPos = enginePos.east();

        level.setBlockAndUpdate(boilerPos, GTBlocks.STEAM_BOILER.get().defaultBlockState());
        level.setBlockAndUpdate(pipePos, GTBlocks.STEAM_PIPE.get().defaultBlockState());
        level.setBlockAndUpdate(
            enginePos,
            GTBlocks.STEAM_ENGINE.get().defaultBlockState()
                .setValue(SteamEngineBlock.FACING, Direction.UP)
        );
        level.setBlockAndUpdate(crusherPos, GTBlocks.BASIC_CRUSHER.get().defaultBlockState());
        level.setBlockAndUpdate(
            engineWaterSinkPos,
            GTBlocks.STEAM_BOILER.get().defaultBlockState()
        );

        SteamBoilerBlockEntity boiler = level.getBlockEntity(boilerPos)
            instanceof SteamBoilerBlockEntity entity ? entity : null;
        SteamPipeBlockEntity pipe = level.getBlockEntity(pipePos)
            instanceof SteamPipeBlockEntity entity ? entity : null;
        SteamEngineBlockEntity engine = level.getBlockEntity(enginePos)
            instanceof SteamEngineBlockEntity entity ? entity : null;
        SteamBoilerBlockEntity waterSink = level.getBlockEntity(engineWaterSinkPos)
            instanceof SteamBoilerBlockEntity entity ? entity : null;
        if (boiler == null || pipe == null || engine == null || waterSink == null) {
            GT6Neo.LOGGER.error("GT6 steam flow test: missing block entity");
            return new boolean[] {false, false, false};
        }

        boiler.setPart(new MachinePart(553, "Steam Boilers"));
        engine.setPart(new MachinePart(584, "Engines"));
        waterSink.setPart(new MachinePart(553, "Steam Boilers"));
        boiler.addWater(2000);
        boiler.receivePacket(
            new EnergyPacket(EnergyKind.HEAT, 1024, 200),
            Direction.WEST,
            false
        );
        for (int tick = 0; tick < 80; tick++) {
            boiler.tick();
            pipe.tick();
            engine.tick();
        }
        boolean steamFlowPass = (engine.energyStored() > 0 || waterSink.waterAmount() > 0)
            && engine.outputBase() == 8
            && engine.energyCapacity() == 16000
            && engine.steamCapacity() == 3200;
        GT6Neo.LOGGER.info(
            "GT6 steam flow test: pipe={} engineSteam={} engineEnergy={} distilled={} pass={}",
            pipe.fluidAmount(),
            engine.steamAmount(),
            engine.energyStored(),
            waterSink.waterAmount(),
            steamFlowPass
        );

        BlockPos isolatedBoilerPos = steamTestPos.offset(0, 0, 5);
        level.setBlockAndUpdate(
            isolatedBoilerPos,
            GTBlocks.STEAM_BOILER.get().defaultBlockState()
        );
        SteamBoilerBlockEntity isolated = level.getBlockEntity(isolatedBoilerPos)
            instanceof SteamBoilerBlockEntity entity ? entity : null;
        boolean steamNoVoidPass = false;
        if (isolated != null) {
            isolated.setPart(new MachinePart(553, "Steam Boilers"));
            isolated.addWater(1100);
            isolated.receivePacket(
                new EnergyPacket(EnergyKind.HEAT, 1024, 100),
                Direction.WEST,
                false
            );
            for (int tick = 0; tick < 40; tick++) {
                isolated.tick();
            }
            long before = isolated.steamAmount();
            isolated.tick();
            long after = isolated.steamAmount();
            steamNoVoidPass = before > 0 && after >= before;
            GT6Neo.LOGGER.info(
                "GT6 steam no-void test: before={} after={} pass={}",
                before,
                after,
                steamNoVoidPass
            );
        }

        clearTestVolume(level, turbineTestPos, 6, 5, 6);
        BlockPos turbinePos = turbineTestPos;
        BlockPos rotationPos = turbinePos.above();
        BlockPos turbineWaterSinkPos = turbinePos.east();
        level.setBlockAndUpdate(
            turbinePos,
            GTBlocks.STEAM_TURBINE.get().defaultBlockState()
                .setValue(SteamTurbineBlock.FACING, Direction.UP)
        );
        level.setBlockAndUpdate(
            rotationPos,
            GTBlocks.ROTATION_ENGINE.get().defaultBlockState()
                .setValue(KineticConverterBlock.FACING, Direction.NORTH)
        );
        level.setBlockAndUpdate(
            turbineWaterSinkPos,
            GTBlocks.STEAM_BOILER.get().defaultBlockState()
        );
        SteamTurbineBlockEntity turbine = level.getBlockEntity(turbinePos)
            instanceof SteamTurbineBlockEntity entity ? entity : null;
        KineticConverterBlockEntity rotation = level.getBlockEntity(rotationPos)
            instanceof KineticConverterBlockEntity entity ? entity : null;
        SteamBoilerBlockEntity turbineWaterSink = level.getBlockEntity(turbineWaterSinkPos)
            instanceof SteamBoilerBlockEntity entity ? entity : null;
        boolean steamTurbinePass = false;
        if (turbine != null && rotation != null && turbineWaterSink != null) {
            turbine.setPart(new MachinePart(794, "Turbines"));
            turbineWaterSink.setPart(new MachinePart(553, "Steam Boilers"));
            IFluidHandler input = level.getCapability(
                Capabilities.FluidHandler.BLOCK,
                turbinePos,
                Direction.DOWN
            );
            IFluidHandler wrongSide = level.getCapability(
                Capabilities.FluidHandler.BLOCK,
                turbinePos,
                Direction.UP
            );
            int accepted = input == null
                ? 0
                : input.fill(
                    GTFluidUtil.steam(48),
                    IFluidHandler.FluidAction.EXECUTE
                );
            int wrongAccepted = wrongSide == null
                ? 0
                : wrongSide.fill(
                    GTFluidUtil.steam(1),
                    IFluidHandler.FluidAction.SIMULATE
                );
            turbine.tick();
            turbine.tick();
            if (input != null) {
                input.fill(
                    GTFluidUtil.steam(200),
                    IFluidHandler.FluidAction.EXECUTE
                );
            }
            turbine.tick();
            steamTurbinePass = accepted == 48
                && wrongAccepted == 0
                && turbine.steamCapacity() == 384
                && turbine.inputSize() == 48
                && rotation.storedEnergy() > 0
                && turbineWaterSink.waterAmount() > 0;
            GT6Neo.LOGGER.info(
                "GT6 steam turbine test: accepted={} wrongSide={} capacity={} input={} RU={} distilled={} pass={}",
                accepted,
                wrongAccepted,
                turbine.steamCapacity(),
                turbine.inputSize(),
                rotation.storedEnergy(),
                turbineWaterSink.waterAmount(),
                steamTurbinePass
            );
        }

        clearTestVolume(level, steamTestPos, 8, 6, 10);
        clearTestVolume(level, turbineTestPos, 6, 5, 6);
        boolean[] largeBoilerResults = runLargeBoilerTests(level);
        return new boolean[] {
            steamFlowPass,
            steamNoVoidPass,
            steamTurbinePass,
            largeBoilerResults[0],
            largeBoilerResults[1]
        };
    }

    private static boolean[] runLargeBoilerTests(ServerLevel level) {
        clearTestVolume(level, largeBoilerTestPos, 6, 8, 8);
        LargeBoilerBlockEntity boiler = buildLargeBoiler(
            level,
            largeBoilerTestPos,
            "stainlesssteel",
            1248
        );
        if (boiler == null) {
            GT6Neo.LOGGER.error("GT6 large boiler test: missing controller block entity");
            return new boolean[] {false, false};
        }

        BlockPos center = boiler.center();
        BlockPos enginePos = center.above(3);
        BlockPos waterSinkPos = enginePos.east();
        level.setBlockAndUpdate(
            enginePos,
            GTBlocks.STEAM_ENGINE.get().defaultBlockState()
                .setValue(SteamEngineBlock.FACING, Direction.UP)
        );
        level.setBlockAndUpdate(
            waterSinkPos,
            GTBlocks.STEAM_BOILER.get().defaultBlockState()
        );
        SteamEngineBlockEntity engine = level.getBlockEntity(enginePos)
            instanceof SteamEngineBlockEntity entity ? entity : null;
        SteamBoilerBlockEntity waterSink = level.getBlockEntity(waterSinkPos)
            instanceof SteamBoilerBlockEntity entity ? entity : null;
        if (engine == null || waterSink == null) {
            GT6Neo.LOGGER.error("GT6 large boiler test: missing engine or condensate sink");
            return new boolean[] {false, false};
        }
        engine.setPart(new MachinePart(584, "Engines"));
        waterSink.setPart(new MachinePart(553, "Steam Boilers"));

        BlockPos lowerWaterWallPos = center.west();
        IFluidHandler waterInput = level.getCapability(
            Capabilities.FluidHandler.BLOCK,
            lowerWaterWallPos,
            Direction.EAST
        );
        int initialWater = waterInput == null
            ? 0
            : waterInput.fill(
                GTFluidUtil.distilledWater(128_000),
                IFluidHandler.FluidAction.EXECUTE
            );
        BlockPos heatInputPos = center.below();
        if (level.getBlockEntity(heatInputPos) instanceof HeatTransmitterBlockEntity transmitter) {
            transmitter.receivePacket(
                new EnergyPacket(EnergyKind.HEAT, 4_096, 5_200),
                Direction.UP,
                false
            );
        }
        for (int tick = 0; tick < 160; tick++) {
            if (waterInput != null) {
                waterInput.fill(
                    GTFluidUtil.distilledWater(32_000),
                    IFluidHandler.FluidAction.EXECUTE
                );
            }
            boiler.tick();
            engine.tick();
        }

        boolean largeBoilerPass = boiler.isFormed()
            && boiler.outputRate() == 8_192
            && boiler.heatCapacity() == 81_920_000L
            && boiler.steamAmount() >= boiler.steamCapacity() / 2
            && boiler.efficiency() == 10_000
            && boiler.barometer() > 0
            && initialWater == 128_000
            && (engine.energyStored() > 0 || waterSink.waterAmount() > 0);
        GT6Neo.LOGGER.info(
            "GT6 large boiler test: formed={} output={} heatCap={} wallInput={} water={} steam={} efficiency={} barometer={} engineKU={} condensate={} pass={}",
            boiler.isFormed(),
            boiler.outputRate(),
            boiler.heatCapacity(),
            initialWater,
            boiler.waterAmount(),
            boiler.steamAmount(),
            boiler.efficiency(),
            boiler.barometer(),
            engine.energyStored(),
            waterSink.waterAmount(),
            largeBoilerPass
        );

        BlockPos explosionPos = largeBoilerTestPos.offset(0, 0, 12);
        clearTestVolume(level, explosionPos, 7, 8, 7);
        LargeBoilerBlockEntity exploding = buildLargeBoiler(
            level,
            explosionPos,
            "stainlesssteel",
            1248
        );
        boolean explosionPass = false;
        if (exploding != null) {
            int filled = exploding.fillSteamForTesting(Integer.MAX_VALUE);
            boolean formedBefore = exploding.isFormed();
            long capacity = exploding.steamCapacity();
            exploding.tick();
            explosionPass = filled >= capacity
                && formedBefore
                && !(level.getBlockState(explosionPos).getBlock() instanceof LargeBoilerBlock);
            GT6Neo.LOGGER.info(
                "GT6 large boiler explosion test: filled={} capacity={} formed={} destroyed={} pass={}",
                filled,
                capacity,
                formedBefore,
                !(level.getBlockState(explosionPos).getBlock() instanceof LargeBoilerBlock),
                explosionPass
            );
        }

        clearTestVolume(level, largeBoilerTestPos, 7, 8, 9);
        clearTestVolume(level, explosionPos, 12, 10, 12);
        return new boolean[] {largeBoilerPass, explosionPass};
    }

    private static LargeBoilerBlockEntity buildLargeBoiler(
        ServerLevel level,
        BlockPos controllerPos,
        String material,
        int sourceLine
    ) {
        BlockState controllerState = GTBlocks.LARGE_BOILER.get().defaultBlockState()
            .setValue(LargeBoilerBlock.FACING, Direction.SOUTH)
            .setValue(LargeBoilerBlock.FORMED, false);
        level.setBlockAndUpdate(controllerPos, controllerState);
        if (!(level.getBlockEntity(controllerPos) instanceof LargeBoilerBlockEntity boiler)) {
            return null;
        }
        boiler.setPart(new MachinePart(sourceLine, "Multiblock Machines"));

        BlockPos center = controllerPos.north();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos bottom = center.offset(x, -1, z);
                level.setBlockAndUpdate(
                    bottom,
                    GTBlocks.HEAT_TRANSMITTER.get().defaultBlockState()
                );
            }
        }
        for (int y = 0; y <= 2; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && z == 0) {
                        if (y == 2) {
                            setLargeBoilerWall(level, center.offset(x, y, z), material);
                        }
                        continue;
                    }
                    BlockPos target = center.offset(x, y, z);
                    if (!target.equals(controllerPos)) {
                        setLargeBoilerWall(level, target, material);
                    }
                }
            }
        }
        boiler.forceStructureCheck();
        GT6Neo.LOGGER.info(
            "GT6 large boiler structure: controller={} formed={} error={}",
            controllerPos,
            boiler.isFormed(),
            boiler.structureError()
        );
        return boiler;
    }

    private static void setLargeBoilerWall(
        ServerLevel level,
        BlockPos pos,
        String material
    ) {
        level.setBlockAndUpdate(
            pos,
            GTBlocks.LARGE_BOILER_WALL.get().defaultBlockState()
        );
        if (level.getBlockEntity(pos) instanceof LargeBoilerWallBlockEntity wall) {
            wall.setPart(new MaterialPart(material, "casingMachineDense"));
        }
    }

    private static void clearTestVolume(
        ServerLevel level,
        BlockPos center,
        int radiusX,
        int radiusY,
        int radiusZ
    ) {
        for (int x = -radiusX; x <= radiusX; x++) {
            for (int y = -1; y <= radiusY; y++) {
                for (int z = -radiusZ; z <= radiusZ; z++) {
                    level.setBlockAndUpdate(
                        center.offset(x, y, z),
                        Blocks.AIR.defaultBlockState()
                    );
                }
            }
        }
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
        return demo >= 34
            && materials >= 10_000
            && blocks >= 12_000
            && machines >= 251
            && multiblocks >= 61
            && tools >= 62
            && smallCrucibles >= 40
            && casting >= 110
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
        level.setBlockAndUpdate(largeWallMoldPos, GTBlocks.MOLD.get().defaultBlockState());
        if (level.getBlockEntity(largeWallMoldPos) instanceof MoldBlockEntity mold) {
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
