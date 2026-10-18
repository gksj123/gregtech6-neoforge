package gregtech.neo.registry;

import gregtech.neo.GT6Neo;
import gregtech.neo.item.BasicMachineItem;
import gregtech.neo.item.BurningBoxItem;
import gregtech.neo.item.SmallCrucibleItem;
import gregtech.neo.machine.GTMachineRegistry;
import gregtech.neo.machine.MachinePart;
import gregtech.neo.material.GTMaterialBlocks;
import gregtech.neo.material.GTMaterialItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GTCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GT6Neo.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DEMO = TABS.register(
        "demo",
        () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("itemGroup.gregtech.demo"))
            .icon(() -> GTItems.SCREWDRIVER.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(GTItems.GT6_DEMO_GUIDE.get());
                output.accept(GTItems.WRENCH.get());
                output.accept(GTItems.SCREWDRIVER.get());
                output.accept(GTItems.SOFT_HAMMER.get());
                output.accept(GTItems.MAGNIFYING_GLASS.get());
                output.accept(GTItems.MONKEY_WRENCH.get());
                output.accept(GTItems.ELECTROMETER.get());
                output.accept(GTItems.THERMOMETER.get());
                output.accept(GTBlocks.BASIC_CRUSHER.get());
                output.accept(GTBlocks.LARGE_CRUSHER.get());
                output.accept(GTBlocks.CRUCIBLE.get());
                output.accept(SmallCrucibleItem.create(
                    GTItems.SMALL_CRUCIBLE.get(),
                    new MachinePart(267, "Smelting Crucibles")
                ));
                output.accept(GTBlocks.TEMPERATURE_SENSOR.get());
                output.accept(GTBlocks.LV_GENERATOR.get());
                output.accept(GTBlocks.MANUAL_CRANK.get());
                output.accept(GTBlocks.ELECTRIC_MOTOR.get());
                output.accept(GTBlocks.ELECTRIC_ENGINE.get());
                output.accept(GTBlocks.ROTATION_ENGINE.get());
                output.accept(GTBlocks.MACHINE_CASING.get());
                output.accept(GTBlocks.TUNGSTENSTEEL_WALL.get());
                output.accept(GTBlocks.CRUCIBLE_WALL.get());
                output.accept(GTBlocks.CRUSHER_WHEELS.get());
                output.accept(GTBlocks.MACHINE_PORT.get());
                output.accept(BurningBoxItem.create(
                    GTItems.BURNING_BOX.get(),
                    new MachinePart(528, "Burning Boxes")
                ));
                output.accept(GTMaterialItems.createPart("iron", "ingot", 1));
                output.accept(GTMaterialItems.createPart("copper", "ingot", 1));
                output.accept(GTMaterialItems.createPart("tin", "ingot", 1));
                output.accept(GTMaterialItems.createPart("steel", "ingot", 1));
                output.accept(Items.COAL);
            })
            .build()
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MATERIALS = TABS.register(
        "materials",
        () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 1)
            .title(Component.translatable("itemGroup.gregtech.materials"))
            .icon(() -> GTMaterialItems.createPart("steel", "ingot", 1))
            .displayItems((parameters, output) -> GTMaterialItems.creativeStacks().forEach(output::accept))
            .build()
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MATERIAL_BLOCKS = TABS.register(
        "material_blocks",
        () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 2)
            .title(Component.translatable("itemGroup.gregtech.material_blocks"))
            .icon(() -> GTMaterialBlocks.creativeStack("steel", "blockIngot"))
            .displayItems((parameters, output) -> GTMaterialBlocks.creativeStacks().forEach(output::accept))
            .build()
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MACHINES = TABS.register(
        "machine_catalog",
        () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 3)
            .title(Component.translatable("itemGroup.gregtech.machine_catalog"))
            .icon(() -> BasicMachineItem.create(
                GTItems.BASIC_MACHINE.get(),
                new MachinePart(1300, "Basic Machines")
            ))
            .displayItems((parameters, output) -> GTMachineRegistry.machines().stream()
                .filter(machine -> "Basic Machines".equals(machine.category()))
                .forEach(machine -> output.accept(BasicMachineItem.create(
                    GTItems.BASIC_MACHINE.get(),
                    new MachinePart(machine.line(), machine.category())
                ))))
            .build()
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MULTIBLOCKS_AND_HEAT = TABS.register(
        "multiblocks_and_heat",
        () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 4)
            .title(Component.translatable("itemGroup.gregtech.multiblocks_and_heat"))
            .icon(() -> GTBlocks.LARGE_CRUSHER.get().asItem().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(GTBlocks.LARGE_CRUSHER.get());
                output.accept(GTBlocks.CRUCIBLE.get());
                output.accept(GTBlocks.MACHINE_CASING.get());
                output.accept(GTBlocks.TUNGSTENSTEEL_WALL.get());
                output.accept(GTBlocks.CRUCIBLE_WALL.get());
                output.accept(GTBlocks.CRUSHER_WHEELS.get());
                output.accept(GTBlocks.MACHINE_PORT.get());
                output.accept(GTBlocks.TEMPERATURE_SENSOR.get());
                GTMachineRegistry.machines().stream()
                    .filter(machine -> "Burning Boxes".equals(machine.category()))
                    .filter(machine -> "Furnace".equals(machine.fuel_map()))
                    .forEach(machine -> output.accept(BurningBoxItem.create(
                        GTItems.BURNING_BOX.get(),
                        new MachinePart(machine.line(), machine.category())
                    )));
            })
            .build()
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TOOLS_AND_ENERGY = TABS.register(
        "tools_and_energy",
        () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 5)
            .title(Component.translatable("itemGroup.gregtech.tools_and_energy"))
            .icon(() -> GTItems.WRENCH.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(GTItems.GT6_DEMO_GUIDE.get());
                output.accept(GTItems.WRENCH.get());
                output.accept(GTItems.SCREWDRIVER.get());
                output.accept(GTItems.SOFT_HAMMER.get());
                output.accept(GTItems.MAGNIFYING_GLASS.get());
                output.accept(GTItems.MONKEY_WRENCH.get());
                output.accept(GTItems.ELECTROMETER.get());
                output.accept(GTItems.THERMOMETER.get());
                output.accept(GTBlocks.LV_GENERATOR.get());
                output.accept(GTBlocks.MANUAL_CRANK.get());
                output.accept(GTBlocks.ELECTRIC_MOTOR.get());
                output.accept(GTBlocks.ELECTRIC_ENGINE.get());
                output.accept(GTBlocks.ROTATION_ENGINE.get());
                output.accept(GTBlocks.AXLE.get());
                output.accept(GTBlocks.TIN_CABLE.get());
                output.accept(GTBlocks.COPPER_CABLE.get());
            })
            .build()
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SMALL_CRUCIBLES = TABS.register(
        "smelting_crucibles",
        () -> CreativeModeTab.builder(CreativeModeTab.Row.BOTTOM, 0)
            .title(Component.translatable("itemGroup.gregtech.smelting_crucibles"))
            .icon(() -> SmallCrucibleItem.create(
                GTItems.SMALL_CRUCIBLE.get(),
                new MachinePart(267, "Smelting Crucibles")
            ))
            .displayItems((parameters, output) -> {
                GTMachineRegistry.machines().stream()
                    .filter(machine -> "Smelting Crucibles".equals(machine.category()))
                    .forEach(machine -> output.accept(SmallCrucibleItem.create(
                        GTItems.SMALL_CRUCIBLE.get(),
                        new MachinePart(machine.line(), machine.category())
                    )));
                output.accept(GTItems.THERMOMETER.get());
                output.accept(GTBlocks.TEMPERATURE_SENSOR.get());
            })
            .build()
    );

    private GTCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
