package gregtech.neo.client;

import gregtech.neo.material.GTMaterial;
import gregtech.neo.material.GTMaterialItems;
import gregtech.neo.material.GTMaterialBlocks;
import gregtech.neo.material.GTMaterialRegistry;
import gregtech.neo.material.MaterialPart;
import gregtech.neo.registry.GTDataComponents;
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
    }

    private static void registerBlocks(RegisterColorHandlersEvent.Block event) {
        event.register(
            GTMaterialItemColors::getBlockColor,
            GTMaterialBlocks.BLOCKS.values().stream().map(holder -> holder.get()).toArray(net.minecraft.world.level.block.Block[]::new)
        );
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
}
