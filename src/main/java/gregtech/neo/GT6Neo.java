package gregtech.neo;

import com.mojang.logging.LogUtils;
import gregtech.neo.registry.GTBlockEntities;
import gregtech.neo.registry.GTBlocks;
import gregtech.neo.registry.GTCapabilities;
import gregtech.neo.registry.GTDataComponents;
import gregtech.neo.registry.GTItems;
import gregtech.neo.material.GTMaterialItems;
import gregtech.neo.material.GTMaterialBlocks;
import gregtech.neo.material.GTMaterialGameplayRegistry;
import gregtech.neo.material.GTMaterialRegistry;
import gregtech.neo.machine.GTMachineRegistry;
import gregtech.neo.machine.MachinePart;
import gregtech.neo.recipe.GTRecipeEngine;
import gregtech.neo.item.BasicMachineItem;
import gregtech.neo.item.BurningBoxItem;
import gregtech.neo.client.GTMaterialItemColors;
import gregtech.neo.runtime.RuntimeSelfTest;
import gregtech.neo.runtime.ClientRuntimeSelfTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(GT6Neo.MOD_ID)
public final class GT6Neo {
    public static final String MOD_ID = "gregtech";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = CREATIVE_TABS.register(
        "machines",
        () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("itemGroup.gregtech.machines"))
            .icon(() -> GTItems.SCREWDRIVER.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(GTBlocks.MACHINE_CASING.get());
                output.accept(GTBlocks.TUNGSTENSTEEL_WALL.get());
                output.accept(GTBlocks.CRUSHER_WHEELS.get());
                output.accept(GTBlocks.MACHINE_PORT.get());
                output.accept(GTBlocks.LARGE_CRUSHER.get());
                output.accept(GTBlocks.BASIC_CRUSHER.get());
                output.accept(GTItems.BASIC_MACHINE.get());
                output.accept(GTBlocks.CRUCIBLE.get());
                output.accept(GTBlocks.CRUCIBLE_WALL.get());
                output.accept(GTItems.BURNING_BOX.get());
                output.accept(GTBlocks.LV_GENERATOR.get());
                output.accept(GTBlocks.MANUAL_CRANK.get());
                output.accept(GTBlocks.ELECTRIC_MOTOR.get());
                output.accept(GTBlocks.ELECTRIC_ENGINE.get());
                output.accept(GTBlocks.ROTATION_ENGINE.get());
                output.accept(GTBlocks.AXLE.get());
                output.accept(GTBlocks.TIN_CABLE.get());
                output.accept(GTBlocks.COPPER_CABLE.get());
                output.accept(GTItems.WRENCH.get());
                output.accept(GTItems.SCREWDRIVER.get());
                output.accept(GTItems.SOFT_HAMMER.get());
                output.accept(GTItems.MAGNIFYING_GLASS.get());
                output.accept(GTItems.MONKEY_WRENCH.get());
                output.accept(GTItems.ELECTROMETER.get());
                GTMaterialItems.creativeStacks().forEach(output::accept);
                GTMaterialBlocks.creativeStacks().forEach(output::accept);
                GTMachineRegistry.machines().stream()
                    .filter(machine -> "Basic Machines".equals(machine.category()))
                    .forEach(machine -> output.accept(BasicMachineItem.create(
                        GTItems.BASIC_MACHINE.get(),
                        new MachinePart(machine.line(), machine.category())
                    )));
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

    public GT6Neo(IEventBus modEventBus, ModContainer modContainer) {
        GTMaterialRegistry.bootstrap();
        GTMaterialGameplayRegistry.bootstrap();
        GTMachineRegistry.bootstrap();
        GTRecipeEngine.bootstrap();
        GTDataComponents.DATA_COMPONENTS.register(modEventBus);
        GTBlocks.BLOCKS.register(modEventBus);
        GTItems.ITEMS.register(modEventBus);
        GTBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        GTCapabilities.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        RuntimeSelfTest.install();
        if (FMLEnvironment.dist.isClient()) {
            ClientRuntimeSelfTest.install();
            GTMaterialItemColors.install(modEventBus);
        }
    }
}
