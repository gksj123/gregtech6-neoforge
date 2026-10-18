package gregtech.neo.client;

import gregtech.neo.material.GTMaterial;
import gregtech.neo.material.GTMaterialItems;
import gregtech.neo.material.GTMaterialBlocks;
import gregtech.neo.material.GTMaterialRegistry;
import gregtech.neo.material.MaterialPart;
import gregtech.neo.registry.GTDataComponents;
import gregtech.neo.registry.GTBlocks;
import gregtech.neo.registry.GTItems;
import gregtech.neo.machine.GTMachineDefinition;
import gregtech.neo.machine.GTMachineRegistry;
import gregtech.neo.machine.MachinePart;
import gregtech.neo.casting.CastingPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

public final class GTMaterialItemColors {
    private GTMaterialItemColors() {
    }

    public static void install(IEventBus modEventBus) {
        modEventBus.addListener(GTMaterialItemColors::register);
        modEventBus.addListener(GTMaterialItemColors::registerBlocks);
    }

    private static void register(RegisterColorHandlersEvent.Item event) {
        ItemLike[] prefixItems = GTMaterialItems.PREFIX_ITEMS.values()
            .stream()
            .map(holder -> (ItemLike) holder.get())
            .toArray(ItemLike[]::new);
        ItemLike[] blockItems = GTMaterialBlocks.ITEMS.values()
            .stream()
            .map(holder -> (ItemLike) holder.get())
            .toArray(ItemLike[]::new);
        event.register(GTMaterialItemColors::getColor, prefixItems);
        event.register(GTMaterialItemColors::getColor, blockItems);
        event.register(
            GTMaterialItemColors::getSmallCrucibleItemColor,
            GTItems.SMALL_CRUCIBLE.get()
        );
        event.register(GTMaterialItemColors::getCastingItemColor, GTItems.MOLD.get());
        event.register(GTMaterialItemColors::getCastingItemColor, GTItems.FAUCET.get());
    }

    private static void registerBlocks(RegisterColorHandlersEvent.Block event) {
        event.register(
            GTMaterialItemColors::getBlockColor,
            GTMaterialBlocks.BLOCKS.values().stream().map(holder -> holder.get()).toArray(net.minecraft.world.level.block.Block[]::new)
        );
        event.register(
            GTMaterialItemColors::getSmallCrucibleBlockColor,
            GTBlocks.SMALL_CRUCIBLE.get()
        );
        event.register(GTMaterialItemColors::getMoldBlockColor, GTBlocks.MOLD.get());
        event.register(GTMaterialItemColors::getFaucetBlockColor, GTBlocks.FAUCET.get());
    }

    private static int getColor(ItemStack stack, int tintIndex) {
        if (tintIndex != 0) {
            return 0xFFFFFFFF;
        }
        MaterialPart part = stack.get(GTDataComponents.MATERIAL_PART.get());
        if (part == null) {
            return 0xFF808080;
        }
        GTMaterial material = GTMaterialRegistry.material(part.material());
        return 0xFF000000 | (material == null ? 0x808080 : material.provisionalColor());
    }

    private static int getBlockColor(
        BlockState state,
        BlockAndTintGetter level,
        BlockPos pos,
        int tintIndex
    ) {
        if (tintIndex != 0 || pos == null || !(level.getBlockEntity(pos) instanceof gregtech.neo.block.MaterialStorageBlockEntity storage)) {
            return 0xFFFFFFFF;
        }
        MaterialPart part = storage.part();
        GTMaterial material = part == null ? null : GTMaterialRegistry.material(part.material());
        return 0xFF000000 | (material == null ? 0x808080 : material.provisionalColor());
    }

    private static int getSmallCrucibleItemColor(ItemStack stack, int tintIndex) {
        if (tintIndex != 0) {
            return 0xFFFFFFFF;
        }
        MachinePart part = stack.get(GTDataComponents.MACHINE_PART.get());
        GTMachineDefinition definition = part == null
            ? null
            : GTMachineRegistry.find(part.category(), part.sourceLine());
        GTMaterial material = definition == null
            ? null
            : GTMaterialRegistry.material(definition.material_slug());
        return 0xFF000000 | (material == null ? 0x808080 : material.provisionalColor());
    }

    private static int getSmallCrucibleBlockColor(
        BlockState state,
        BlockAndTintGetter level,
        BlockPos pos,
        int tintIndex
    ) {
        if ((tintIndex != 0 && tintIndex != 1) || pos == null
            || !(level.getBlockEntity(pos) instanceof gregtech.neo.block.SmallCrucibleBlockEntity crucible)) {
            return 0xFFFFFFFF;
        }
        if (tintIndex == 1) {
            return crucible.contentColor();
        }
        GTMaterial material = GTMaterialRegistry.material(crucible.wallMaterial());
        return 0xFF000000 | (material == null ? 0x808080 : material.provisionalColor());
    }

    private static int getCastingItemColor(ItemStack stack, int tintIndex) {
        if (tintIndex != 0) {
            return 0xFFFFFFFF;
        }
        CastingPart part = stack.get(GTDataComponents.CASTING_PART.get());
        GTMaterial material = part == null ? null : GTMaterialRegistry.material(part.wallMaterial());
        return 0xFF000000 | (material == null ? 0x808080 : material.provisionalColor());
    }

    private static int getMoldBlockColor(
        BlockState state,
        BlockAndTintGetter level,
        BlockPos pos,
        int tintIndex
    ) {
        if (tintIndex != 0 || pos == null
            || !(level.getBlockEntity(pos) instanceof gregtech.neo.block.MoldBlockEntity mold)) {
            return 0xFFFFFFFF;
        }
        GTMaterial material = GTMaterialRegistry.material(mold.wallMaterial());
        return 0xFF000000 | (material == null ? 0x808080 : material.provisionalColor());
    }

    private static int getFaucetBlockColor(
        BlockState state,
        BlockAndTintGetter level,
        BlockPos pos,
        int tintIndex
    ) {
        if (tintIndex != 0 || pos == null
            || !(level.getBlockEntity(pos) instanceof gregtech.neo.block.FaucetBlockEntity faucet)) {
            return 0xFFFFFFFF;
        }
        GTMaterial material = GTMaterialRegistry.material(faucet.wallMaterial());
        return 0xFF000000 | (material == null ? 0x808080 : material.provisionalColor());
    }
}
