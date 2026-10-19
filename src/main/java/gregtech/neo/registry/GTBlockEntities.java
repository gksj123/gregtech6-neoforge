package gregtech.neo.registry;

import gregtech.neo.GT6Neo;
import gregtech.neo.block.CableBlockEntity;
import gregtech.neo.block.BasicCrusherBlockEntity;
import gregtech.neo.block.BasicMachineBlockEntity;
import gregtech.neo.block.BurningBoxBlockEntity;
import gregtech.neo.block.CrucibleBlockEntity;
import gregtech.neo.block.FaucetBlockEntity;
import gregtech.neo.block.AxleBlockEntity;
import gregtech.neo.block.KineticConverterBlockEntity;
import gregtech.neo.block.GeneratorBlockEntity;
import gregtech.neo.block.LargeCrusherBlockEntity;
import gregtech.neo.block.MachinePortBlockEntity;
import gregtech.neo.block.MaterialStorageBlockEntity;
import gregtech.neo.block.MoldBlockEntity;
import gregtech.neo.block.SmallCrucibleBlockEntity;
import gregtech.neo.block.SteamBoilerBlockEntity;
import gregtech.neo.block.SteamEngineBlockEntity;
import gregtech.neo.block.SteamPipeBlockEntity;
import gregtech.neo.block.SteamTurbineBlockEntity;
import gregtech.neo.block.TemperatureSensorBlockEntity;
import gregtech.neo.material.GTMaterialBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GTBlockEntities {
    private GTBlockEntities() {
    }

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, GT6Neo.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LargeCrusherBlockEntity>> LARGE_CRUSHER =
        BLOCK_ENTITIES.register("large_crusher", () -> BlockEntityType.Builder.of(
            LargeCrusherBlockEntity::new,
            GTBlocks.LARGE_CRUSHER.get()
        ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BasicCrusherBlockEntity>> BASIC_CRUSHER =
        BLOCK_ENTITIES.register("basic_crusher", () -> BlockEntityType.Builder.of(
            BasicCrusherBlockEntity::new,
            GTBlocks.BASIC_CRUSHER.get()
        ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MachinePortBlockEntity>> MACHINE_PORT =
        BLOCK_ENTITIES.register("machine_port", () -> BlockEntityType.Builder.of(
            MachinePortBlockEntity::new,
            GTBlocks.MACHINE_PORT.get()
        ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GeneratorBlockEntity>> LV_GENERATOR =
        BLOCK_ENTITIES.register("lv_generator", () -> BlockEntityType.Builder.of(
            GeneratorBlockEntity::new,
            GTBlocks.LV_GENERATOR.get()
        ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KineticConverterBlockEntity>> KINETIC_CONVERTER =
        BLOCK_ENTITIES.register("kinetic_converter", () -> BlockEntityType.Builder.of(
            KineticConverterBlockEntity::new,
            GTBlocks.ELECTRIC_MOTOR.get(),
            GTBlocks.ELECTRIC_ENGINE.get(),
            GTBlocks.ROTATION_ENGINE.get()
        ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AxleBlockEntity>> AXLE =
        BLOCK_ENTITIES.register("axle", () -> BlockEntityType.Builder.of(
            AxleBlockEntity::new,
            GTBlocks.AXLE.get()
        ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CableBlockEntity>> TIN_CABLE =
        BLOCK_ENTITIES.register("tin_cable", () -> BlockEntityType.Builder.of(
            CableBlockEntity::new,
            GTBlocks.TIN_CABLE.get()
        ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CableBlockEntity>> COPPER_CABLE =
        BLOCK_ENTITIES.register("copper_cable", () -> BlockEntityType.Builder.of(
            CableBlockEntity::new,
            GTBlocks.COPPER_CABLE.get()
        ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MaterialStorageBlockEntity>> MATERIAL_STORAGE =
        BLOCK_ENTITIES.register("material_storage", () -> BlockEntityType.Builder.of(
            MaterialStorageBlockEntity::new,
            GTMaterialBlocks.BLOCKS.values().stream().map(DeferredBlock::get).toArray(net.minecraft.world.level.block.Block[]::new)
        ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BasicMachineBlockEntity>> BASIC_MACHINE =
        BLOCK_ENTITIES.register("basic_machine", () -> BlockEntityType.Builder.of(
            BasicMachineBlockEntity::new,
            GTBlocks.BASIC_MACHINE.get()
        ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BurningBoxBlockEntity>> BURNING_BOX =
        BLOCK_ENTITIES.register("burning_box", () -> BlockEntityType.Builder.of(
            BurningBoxBlockEntity::new,
            GTBlocks.BURNING_BOX.get()
        ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrucibleBlockEntity>> CRUCIBLE =
        BLOCK_ENTITIES.register("crucible", () -> BlockEntityType.Builder.of(
            CrucibleBlockEntity::new,
            GTBlocks.CRUCIBLE.get()
        ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SmallCrucibleBlockEntity>> SMALL_CRUCIBLE =
        BLOCK_ENTITIES.register("small_crucible", () -> BlockEntityType.Builder.of(
            SmallCrucibleBlockEntity::new,
            GTBlocks.SMALL_CRUCIBLE.get()
        ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TemperatureSensorBlockEntity>> TEMPERATURE_SENSOR =
        BLOCK_ENTITIES.register("temperature_sensor", () -> BlockEntityType.Builder.of(
            TemperatureSensorBlockEntity::new,
            GTBlocks.TEMPERATURE_SENSOR.get()
        ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MoldBlockEntity>> MOLD =
        BLOCK_ENTITIES.register("mold", () -> BlockEntityType.Builder.of(
            MoldBlockEntity::new,
            GTBlocks.MOLD.get()
        ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FaucetBlockEntity>> FAUCET =
        BLOCK_ENTITIES.register("faucet", () -> BlockEntityType.Builder.of(
            FaucetBlockEntity::new,
            GTBlocks.FAUCET.get()
        ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteamBoilerBlockEntity>> STEAM_BOILER =
        BLOCK_ENTITIES.register("steam_boiler", () -> BlockEntityType.Builder.of(
            SteamBoilerBlockEntity::new,
            GTBlocks.STEAM_BOILER.get()
        ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteamEngineBlockEntity>> STEAM_ENGINE =
        BLOCK_ENTITIES.register("steam_engine", () -> BlockEntityType.Builder.of(
            SteamEngineBlockEntity::new,
            GTBlocks.STEAM_ENGINE.get()
        ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteamTurbineBlockEntity>> STEAM_TURBINE =
        BLOCK_ENTITIES.register("steam_turbine", () -> BlockEntityType.Builder.of(
            SteamTurbineBlockEntity::new,
            GTBlocks.STEAM_TURBINE.get()
        ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteamPipeBlockEntity>> STEAM_PIPE =
        BLOCK_ENTITIES.register("steam_pipe", () -> BlockEntityType.Builder.of(
            SteamPipeBlockEntity::new,
            GTBlocks.STEAM_PIPE.get()
        ).build(null));
}
