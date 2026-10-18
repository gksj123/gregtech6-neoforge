package gregtech.neo.registry;

import gregtech.neo.GT6Neo;
import gregtech.neo.block.CableBlock;
import gregtech.neo.block.BasicCrusherBlock;
import gregtech.neo.block.BasicMachineBlock;
import gregtech.neo.block.BurningBoxBlock;
import gregtech.neo.block.CrucibleBlock;
import gregtech.neo.block.AxleBlock;
import gregtech.neo.block.GeneratorBlock;
import gregtech.neo.block.KineticConverterBlock;
import gregtech.neo.block.LargeCrusherBlock;
import gregtech.neo.block.ManualCrankBlock;
import gregtech.neo.block.MachinePortBlock;
import gregtech.neo.block.SmallCrucibleBlock;
import gregtech.neo.block.TemperatureSensorBlock;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import gregtech.neo.material.GTMaterialBlocks;

public final class GTBlocks {
    private GTBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(GT6Neo.MOD_ID);

    public static final DeferredBlock<Block> MACHINE_CASING = registerBlock(
        "machine_casing",
        () -> new Block(machineProperties(MapColor.METAL, 5.0F, 8.0F)),
        MapColor.METAL
    );

    public static final DeferredBlock<Block> CRUSHER_WHEELS = registerBlock(
        "crusher_wheels",
        () -> new Block(machineProperties(MapColor.METAL, 6.0F, 10.0F)),
        MapColor.METAL
    );

    public static final DeferredBlock<Block> TUNGSTENSTEEL_WALL = registerBlock(
        "tungstensteel_wall",
        () -> new Block(machineProperties(MapColor.METAL, 12.5F, 12.5F)),
        MapColor.METAL
    );

    public static final DeferredBlock<MachinePortBlock> MACHINE_PORT = registerBlock(
        "machine_port",
        () -> new MachinePortBlock(machineProperties(MapColor.METAL, 5.0F, 8.0F)),
        MapColor.METAL
    );

    public static final DeferredBlock<LargeCrusherBlock> LARGE_CRUSHER = registerBlock(
        "large_crusher",
        () -> new LargeCrusherBlock(machineProperties(MapColor.METAL, 6.0F, 12.0F)),
        MapColor.METAL
    );

    public static final DeferredBlock<BasicCrusherBlock> BASIC_CRUSHER = registerBlock(
        "basic_crusher",
        () -> new BasicCrusherBlock(machineProperties(MapColor.METAL, 5.0F, 9.0F)),
        MapColor.METAL
    );

    // This block has a metadata-bearing custom item, registered by GTItems.
    public static final DeferredBlock<BasicMachineBlock> BASIC_MACHINE = BLOCKS.register(
        "basic_machine",
        () -> new BasicMachineBlock(machineProperties(MapColor.METAL, 5.0F, 9.0F))
    );

    public static final DeferredBlock<BurningBoxBlock> BURNING_BOX = BLOCKS.register(
        "burning_box",
        () -> new BurningBoxBlock(machineProperties(MapColor.METAL, 6.0F, 8.0F))
    );

    public static final DeferredBlock<CrucibleBlock> CRUCIBLE = registerBlock(
        "crucible",
        () -> new CrucibleBlock(machineProperties(MapColor.METAL, 6.0F, 10.0F)),
        MapColor.METAL
    );

    public static final DeferredBlock<SmallCrucibleBlock> SMALL_CRUCIBLE = BLOCKS.register(
        "small_crucible",
        () -> new SmallCrucibleBlock(machineProperties(MapColor.METAL, 4.0F, 8.0F))
    );

    public static final DeferredBlock<TemperatureSensorBlock> TEMPERATURE_SENSOR = registerBlock(
        "temperature_sensor",
        () -> new TemperatureSensorBlock(machineProperties(MapColor.METAL, 2.0F, 5.0F)),
        MapColor.METAL
    );

    public static final DeferredBlock<Block> CRUCIBLE_WALL = registerBlock(
        "crucible_wall",
        () -> new Block(machineProperties(MapColor.METAL, 6.0F, 10.0F)),
        MapColor.METAL
    );

    public static final DeferredBlock<GeneratorBlock> LV_GENERATOR = registerBlock(
        "lv_generator",
        () -> new GeneratorBlock(machineProperties(MapColor.METAL, 4.0F, 8.0F)),
        MapColor.METAL
    );

    public static final DeferredBlock<ManualCrankBlock> MANUAL_CRANK = registerBlock(
        "manual_crank",
        () -> new ManualCrankBlock(machineProperties(MapColor.WOOD, 2.0F, 3.0F)),
        MapColor.WOOD
    );

    public static final DeferredBlock<KineticConverterBlock> ELECTRIC_MOTOR = registerBlock(
        "electric_motor",
        () -> new KineticConverterBlock(machineProperties(MapColor.METAL, 4.0F, 8.0F)),
        MapColor.METAL
    );

    public static final DeferredBlock<KineticConverterBlock> ELECTRIC_ENGINE = registerBlock(
        "electric_engine",
        () -> new KineticConverterBlock(machineProperties(MapColor.METAL, 4.0F, 8.0F)),
        MapColor.METAL
    );

    public static final DeferredBlock<KineticConverterBlock> ROTATION_ENGINE = registerBlock(
        "rotation_engine",
        () -> new KineticConverterBlock(machineProperties(MapColor.METAL, 4.0F, 8.0F)),
        MapColor.METAL
    );

    public static final DeferredBlock<AxleBlock> AXLE = registerBlock(
        "axle",
        () -> new AxleBlock(machineProperties(MapColor.METAL, 2.0F, 4.0F).noOcclusion()),
        MapColor.METAL
    );

    public static final DeferredBlock<CableBlock> TIN_CABLE = registerBlock(
        "tin_cable",
        () -> CableBlock.tin(machineProperties(MapColor.COLOR_GRAY, 1.5F, 3.0F).noOcclusion()),
        MapColor.COLOR_GRAY
    );

    public static final DeferredBlock<CableBlock> COPPER_CABLE = registerBlock(
        "copper_cable",
        () -> CableBlock.copper(machineProperties(MapColor.TERRACOTTA_ORANGE, 1.8F, 3.5F).noOcclusion()),
        MapColor.TERRACOTTA_ORANGE
    );

    private static BlockBehaviour.Properties machineProperties(MapColor color, float hardness, float resistance) {
        return BlockBehaviour.Properties.of()
            .mapColor(color)
            .sound(SoundType.METAL)
            .strength(hardness, resistance);
    }

    private static <B extends Block> DeferredBlock<B> registerBlock(
        String name,
        java.util.function.Supplier<B> block,
        MapColor color
    ) {
        DeferredBlock<B> holder = BLOCKS.register(name, block);
        GTItems.ITEMS.registerSimpleBlockItem(name, holder);
        return holder;
    }

    public static Item.Properties itemProperties() {
        return new Item.Properties();
    }

    static {
        GTMaterialBlocks.register(BLOCKS);
    }
}
