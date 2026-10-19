package gregtech.neo;

import com.mojang.logging.LogUtils;
import gregtech.neo.registry.GTBlockEntities;
import gregtech.neo.registry.GTBlocks;
import gregtech.neo.registry.GTCapabilities;
import gregtech.neo.registry.GTCreativeTabs;
import gregtech.neo.registry.GTDataComponents;
import gregtech.neo.registry.GTItems;
import gregtech.neo.material.GTMaterialGameplayRegistry;
import gregtech.neo.material.GTMaterialRegistry;
import gregtech.neo.machine.GTMachineRegistry;
import gregtech.neo.recipe.GTRecipeEngine;
import gregtech.neo.recipe.GTAlloyRegistry;
import gregtech.neo.casting.GTMoldRegistry;
import gregtech.neo.fluid.GTFluids;
import gregtech.neo.client.GTMaterialItemColors;
import gregtech.neo.client.GTCrucibleRenderer;
import gregtech.neo.client.GTCastingRenderer;
import gregtech.neo.runtime.RuntimeSelfTest;
import gregtech.neo.runtime.ClientRuntimeSelfTest;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(GT6Neo.MOD_ID)
public final class GT6Neo {
    public static final String MOD_ID = "gregtech";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GT6Neo(IEventBus modEventBus, ModContainer modContainer) {
        GTMaterialRegistry.bootstrap();
        GTMaterialGameplayRegistry.bootstrap();
        GTMachineRegistry.bootstrap();
        GTRecipeEngine.bootstrap();
        GTAlloyRegistry.bootstrap();
        GTMoldRegistry.bootstrap();
        GTFluids.register(modEventBus);
        GTDataComponents.DATA_COMPONENTS.register(modEventBus);
        GTBlocks.BLOCKS.register(modEventBus);
        GTItems.ITEMS.register(modEventBus);
        GTBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        GTCapabilities.register(modEventBus);
        GTCreativeTabs.register(modEventBus);
        RuntimeSelfTest.install();
        if (FMLEnvironment.dist.isClient()) {
            ClientRuntimeSelfTest.install();
            GTMaterialItemColors.install(modEventBus);
            GTCrucibleRenderer.install(modEventBus);
            GTCastingRenderer.install(modEventBus);
        }
    }
}
