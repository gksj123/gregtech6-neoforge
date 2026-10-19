package gregtech.neo.item;

import gregtech.neo.block.LargeBoilerBlockEntity;
import gregtech.neo.machine.GTMachineDefinition;
import gregtech.neo.machine.GTMachineRegistry;
import gregtech.neo.machine.MachinePart;
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

public final class LargeBoilerItem extends BlockItem {
    public LargeBoilerItem(Block block, Properties properties) {
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
        if (part == null || !(level.getBlockEntity(pos) instanceof LargeBoilerBlockEntity boiler)) {
            return false;
        }
        boiler.setPart(part);
        return true;
    }

    @Override
    public Component getName(ItemStack stack) {
        MachinePart part = stack.get(GTDataComponents.MACHINE_PART.get());
        GTMachineDefinition definition = part == null
            ? null
            : GTMachineRegistry.find(part.category(), part.sourceLine());
        return definition == null
            ? Component.translatable("block.gregtech.large_boiler.unassigned")
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
        GT6Tooltip.header(tooltip, "LARGE STEAM BOILER");
        GT6Tooltip.source(tooltip, definition.category(), definition.line());
        GT6Tooltip.line(tooltip, ChatFormatting.GOLD,
            "ENERGY: 1 L Water + 80 HU -> 160 L Steam");
        GT6Tooltip.line(tooltip, ChatFormatting.RED,
            "OUTPUT: " + definition.outputSu() + " L Steam/t");
        GT6Tooltip.line(tooltip, ChatFormatting.YELLOW,
            "BUILD: 3x3 heat transmitters below a 3x3x3 hollow wall shell");
        GT6Tooltip.line(tooltip, ChatFormatting.YELLOW,
            "MATCH: " + definition.material_slug()
                + " controller and matching dense walls");
        GT6Tooltip.line(tooltip, ChatFormatting.GRAY,
            "CTRL: controller on lower side facing out; water at lower wall, steam above");
        GT6Tooltip.line(tooltip, ChatFormatting.DARK_RED,
            "HAZARD: calcification, overheat and overpressure explosions");
        GT6Tooltip.nbt(tooltip,
            "gregtech:machine_part={line:" + definition.line()
                + ",category:\"Multiblock Machines\"}");
    }
}
