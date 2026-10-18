package gregtech.neo.item;

import gregtech.neo.block.SmallCrucibleBlockEntity;
import gregtech.neo.machine.GTMachineDefinition;
import gregtech.neo.machine.GTMachineRegistry;
import gregtech.neo.machine.MachinePart;
import gregtech.neo.registry.GTDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
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

public final class SmallCrucibleItem extends BlockItem {
    public SmallCrucibleItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static ItemStack create(Item item, MachinePart part) {
        ItemStack stack = new ItemStack(item);
        stack.set(GTDataComponents.MACHINE_PART.get(), part);
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
        MachinePart part = stack.get(GTDataComponents.MACHINE_PART.get());
        if (part == null || !(level.getBlockEntity(pos) instanceof SmallCrucibleBlockEntity crucible)) {
            return false;
        }
        crucible.setPart(part);
        return true;
    }

    @Override
    public Component getName(ItemStack stack) {
        MachinePart part = stack.get(GTDataComponents.MACHINE_PART.get());
        GTMachineDefinition definition = part == null
            ? null
            : GTMachineRegistry.find(part.category(), part.sourceLine());
        return definition == null
            ? Component.translatable("block.gregtech.small_crucible.unassigned")
            : Component.literal(definition.display_name());
    }

    @Override
    public void appendHoverText(
        ItemStack stack,
        TooltipContext context,
        List<Component> tooltip,
        TooltipFlag flag
    ) {
        super.appendHoverText(stack, context, tooltip, flag);
        MachinePart part = stack.get(GTDataComponents.MACHINE_PART.get());
        GTMachineDefinition definition = part == null
            ? null
            : GTMachineRegistry.find(part.category(), part.sourceLine());
        if (definition == null) {
            return;
        }
        GT6Tooltip.header(tooltip, "SMELTING CRUCIBLE");
        GT6Tooltip.source(tooltip, definition.category(), definition.line());
        GT6Tooltip.line(tooltip, ChatFormatting.AQUA,
            "WALL: " + definition.material_name());
        GT6Tooltip.line(tooltip, ChatFormatting.GOLD,
            "ENERGY: HU IN // KU -> AIR // CU: COOL");
        GT6Tooltip.line(tooltip, ChatFormatting.RED,
            "CAP: 16U // WALL LIMIT: MP x1.25");
        GT6Tooltip.line(tooltip, ChatFormatting.GREEN,
            "CTRL: right-click insert // drop items into open top");
        GT6Tooltip.line(tooltip, ChatFormatting.GRAY,
            "TOOL: thermometer // shovel removes solid content");
        GT6Tooltip.nbt(tooltip,
            "gregtech:machine_part={line:" + definition.line()
                + ",category:\"Smelting Crucibles\"}, wall:\"" + definition.material_slug()
                + "\", capacity:\"16U\", heatScale:1.25");
    }
}
