package gregtech.neo.registry;

import gregtech.neo.block.SteamBoilerBlockEntity;
import gregtech.neo.block.SteamEngineBlockEntity;
import gregtech.neo.block.SteamPipeBlockEntity;
import gregtech.neo.block.SteamTurbineBlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class GTCapabilities {
    private GTCapabilities() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(GTCapabilities::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            GTBlockEntities.BASIC_MACHINE.get(),
            (machine, side) -> machine.itemHandler()
        );
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            GTBlockEntities.BASIC_CRUSHER.get(),
            (crusher, side) -> crusher.itemHandler()
        );
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            GTBlockEntities.LARGE_CRUSHER.get(),
            (crusher, side) -> crusher.itemHandler()
        );
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            GTBlockEntities.MACHINE_PORT.get(),
            (port, side) -> port.itemHandler()
        );
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            GTBlockEntities.BURNING_BOX.get(),
            (box, side) -> box.itemHandler()
        );
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            GTBlockEntities.MOLD.get(),
            (mold, side) -> mold.outputItemHandler()
        );
        event.registerBlockEntity(
            Capabilities.FluidHandler.BLOCK,
            GTBlockEntities.STEAM_BOILER.get(),
            SteamBoilerBlockEntity::fluidHandler
        );
        event.registerBlockEntity(
            Capabilities.FluidHandler.BLOCK,
            GTBlockEntities.STEAM_ENGINE.get(),
            SteamEngineBlockEntity::fluidHandler
        );
        event.registerBlockEntity(
            Capabilities.FluidHandler.BLOCK,
            GTBlockEntities.STEAM_TURBINE.get(),
            SteamTurbineBlockEntity::fluidHandler
        );
        event.registerBlockEntity(
            Capabilities.FluidHandler.BLOCK,
            GTBlockEntities.STEAM_PIPE.get(),
            SteamPipeBlockEntity::fluidHandler
        );
    }
}
