package gregtech.neo.item;

import gregtech.neo.block.LargeBoilerWallBlockEntity;
import gregtech.neo.material.GTMaterial;
import gregtech.neo.material.GTMaterialRegistry;
import gregtech.neo.material.MaterialPart;
import gregtech.neo.registry.GTDataComponents;
import net.minecraft.ChatFormatting;
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
import java.util.Map;

public final class LargeBoilerWallItem extends BlockItem {
    private static final Map<String, Integer> SOURCE_LINES = Map.of(
        "stainlesssteel", 1159,
        "invar", 1160,
        "titanium", 1161,
        "tungstensteel", 1162,
        "adamantium", 1165
    );

    public LargeBoilerWallItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static ItemStack create(Item item, String material) {
        ItemStack stack = new ItemStack(item);
        stack.set(
            GTDataComponents.MATERIAL_PART.get(),
            new MaterialPart(material, "casingMachineDense")
        );
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
        if (part == null || !(level.getBlockEntity(pos) instanceof LargeBoilerWallBlockEntity wall)) {
            return false;
        }
        wall.setPart(part);
        return true;
    }

    @Override
    public Component getName(ItemStack stack) {
        MaterialPart part = stack.get(GTDataComponents.MATERIAL_PART.get());
        GTMaterial material = part == null ? null : GTMaterialRegistry.material(part.material());
        return Component.translatable(
            "block.gregtech.large_boiler_wall.name",
            material == null ? "Unknown" : material.prettyName()
        );
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
        if (part == null) {
            return;
        }
        GT6Tooltip.header(tooltip, "DENSE BOILER WALL");
        GT6Tooltip.source(tooltip, "Multiblock Machines", SOURCE_LINES.getOrDefault(part.material(), 1159));
        GT6Tooltip.line(tooltip, ChatFormatting.YELLOW,
            "MAT: " + part.material() + " // PREFIX: " + part.prefix());
        GT6Tooltip.line(tooltip, ChatFormatting.GRAY,
            "CTRL: build the hollow 3x3x3 shell with the controller material");
        GT6Tooltip.nbt(tooltip,
            "gregtech:material_part={material:\"" + part.material()
                + "\",prefix:\"casingMachineDense\"}");
    }
}
