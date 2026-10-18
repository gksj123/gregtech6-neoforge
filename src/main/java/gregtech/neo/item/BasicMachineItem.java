package gregtech.neo.item;

import gregtech.neo.block.BasicMachineBlockEntity;
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

public final class BasicMachineItem extends BlockItem {
    public BasicMachineItem(Block block, Properties properties) {
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
        if (part == null || !(level.getBlockEntity(pos) instanceof BasicMachineBlockEntity machine)) {
            return false;
        }
        machine.setPart(part);
        return true;
    }

    @Override
    public Component getName(ItemStack stack) {
        MachinePart part = stack.get(GTDataComponents.MACHINE_PART.get());
        GTMachineDefinition definition = part == null ? null : GTMachineRegistry.find(part.category(), part.sourceLine());
        return definition == null
            ? Component.translatable("block.gregtech.basic_machine.unassigned")
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
        GTMachineDefinition definition = part == null ? null : GTMachineRegistry.find(part.category(), part.sourceLine());
        if (definition != null) {
            GT6Tooltip.header(tooltip, definition.category().toUpperCase());
            GT6Tooltip.source(tooltip, definition.category(), definition.line());
            GT6Tooltip.line(tooltip, ChatFormatting.AQUA,
                "CLASS: " + shortClassName(definition.machine_class()));
            GT6Tooltip.line(tooltip, ChatFormatting.GOLD,
                "ENERGY: " + energyFamily(definition.energy_accepted())
                    + " // IN " + definition.input()
                    + ".." + Math.max(definition.input(), definition.input_max()));
            if (!definition.recipe_map().isBlank()) {
                GT6Tooltip.line(tooltip, ChatFormatting.LIGHT_PURPLE,
                    "MAP: " + definition.recipe_map());
            }
            GT6Tooltip.line(tooltip, ChatFormatting.GREEN,
                "FLAGS: parallel=" + definition.parallel()
                    + " // duration=" + definition.parallel_duration()
                    + " // cheapOC=" + definition.cheap_overclocking());
            GT6Tooltip.line(tooltip, ChatFormatting.GRAY,
                "CTRL: no GUI // magnifier: status // screwdriver: on/off");
            GT6Tooltip.nbt(tooltip,
                "gregtech:machine_part={category:\"" + definition.category()
                    + "\",line:" + definition.line() + "}, material:\""
                    + definition.material_slug() + "\"");
        }
    }

    private static String energyFamily(String value) {
        return value == null || value.isBlank() ? "NONE" : value;
    }

    private static String shortClassName(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        int separator = value.lastIndexOf('.');
        return separator < 0 ? value : value.substring(separator + 1);
    }
}
