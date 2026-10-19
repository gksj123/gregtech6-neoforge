package gregtech.neo.fluid;

import gregtech.neo.GT6Neo;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class GTFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, GT6Neo.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS =
        DeferredRegister.create(Registries.FLUID, GT6Neo.MOD_ID);

    // GT6 1.7.10: gregtech/loaders/a/Loader_Fluids.java:52 and :55.
    private static final FluidType STEAM_FLUID_TYPE = new FluidType(
        FluidType.Properties.create()
            .descriptionId("fluid.gregtech.steam")
            .density(-1000)
            .temperature(393)
            .viscosity(100)
    );
    private static final FluidType DISTILLED_WATER_FLUID_TYPE = new FluidType(
        FluidType.Properties.create()
            .descriptionId("fluid.gregtech.distilled_water")
            .density(1000)
            .temperature(293)
            .viscosity(1000)
    );

    public static final DeferredHolder<FluidType, FluidType> STEAM_TYPE =
        FLUID_TYPES.register("steam", () -> STEAM_FLUID_TYPE);

    public static final DeferredHolder<FluidType, FluidType> DISTILLED_WATER_TYPE =
        FLUID_TYPES.register("distilled_water", () -> DISTILLED_WATER_FLUID_TYPE);

    public static final DeferredHolder<Fluid, Fluid> STEAM =
        FLUIDS.register("steam", () -> new SimpleGTFluid(STEAM_FLUID_TYPE, Items.AIR));
    public static final DeferredHolder<Fluid, Fluid> DISTILLED_WATER =
        FLUIDS.register(
            "distilled_water",
            () -> new SimpleGTFluid(DISTILLED_WATER_FLUID_TYPE, Items.AIR)
        );

    private GTFluids() {
    }

    public static void register(net.neoforged.bus.api.IEventBus bus) {
        FLUID_TYPES.register(bus);
        FLUIDS.register(bus);
    }
}
