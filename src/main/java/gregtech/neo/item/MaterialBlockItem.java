package gregtech.neo.item;

import gregtech.neo.block.MaterialStorageBlockEntity;
import gregtech.neo.material.GTMaterial;
import gregtech.neo.material.GTMaterialPrefix;
import gregtech.neo.material.GTMaterialRegistry;
import gregtech.neo.material.MaterialPart;
import gregtech.neo.registry.GTDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public final class MaterialBlockItem extends BlockItem {
    public MaterialBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static ItemStack create(Item item, String materialSlug, String prefixId, int count) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(GTDataComponents.MATERIAL_PART.get(), new MaterialPart(materialSlug, prefixId));
        return stack;
    }

    @Override
    protected boolean updateCustomBlockEntityTag(
        BlockPos pos,
        Level level,
        Player player,
        ItemStack stack,
        BlockState state
    ) {
        MaterialPart part = stack.get(GTDataComponents.MATERIAL_PART.get());
        if (part == null || !(level.getBlockEntity(pos) instanceof MaterialStorageBlockEntity storage)) {
            return false;
        }
        storage.setPart(part);
        return true;
    }

    @Override
    public Component getName(ItemStack stack) {
        MaterialPart part = stack.get(GTDataComponents.MATERIAL_PART.get());
        if (part == null) {
            return Component.translatable("block.gregtech.material_storage.unassigned");
        }
        GTMaterial material = GTMaterialRegistry.material(part.material());
        GTMaterialPrefix prefix = GTMaterialRegistry.prefix(part.prefix());
        String materialName = material == null ? part.material() : material.prettyName();
        String formName = prefix == null ? part.prefix() : prefix.prettyCategory();
        return Component.translatable("block.gregtech.material_storage.name", materialName, formName);
    }

    @Override
    public void appendHoverText(
        ItemStack stack,
        TooltipContext context,
        List<Component> tooltip,
        TooltipFlag flag
    ) {
        super.appendHoverText(stack, context, tooltip, flag);
        MaterialPart part = stack.get(GTDataComponents.MATERIAL_PART.get());
        if (part != null) {
            tooltip.add(Component.translatable("block.gregtech.material_storage.source", part.prefix(), part.material()));
        }
    }
}
