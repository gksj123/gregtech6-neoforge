package gregtech.neo.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class GT6ToolItem extends Item {
    public enum ToolKind {
        WRENCH,
        SCREWDRIVER,
        SOFT_HAMMER,
        MAGNIFYING_GLASS,
        MONKEY_WRENCH,
        ELECTROMETER,
        THERMOMETER,
        CHISEL,
        PINCERS
    }

    private final ToolKind kind;

    public GT6ToolItem(ToolKind kind, Properties properties) {
        super(properties.stacksTo(1));
        this.kind = kind;
    }

    public ToolKind kind() {
        return kind;
    }

    @Override
    public void appendHoverText(
        ItemStack stack,
        TooltipContext context,
        List<Component> tooltip,
        TooltipFlag flag
    ) {
        String usage = switch (kind) {
            case WRENCH -> "CTRL: rotate machine or multiblock";
            case SCREWDRIVER -> "CTRL: toggle machine / cycle port mode";
            case SOFT_HAMMER -> "CTRL: force structure check / reset";
            case MAGNIFYING_GLASS -> "CTRL: inspect machine and NBT state";
            case MONKEY_WRENCH -> "CTRL: toggle automation behavior";
            case ELECTROMETER -> "CTRL: inspect energy family, size and amperage";
            case THERMOMETER -> "CTRL: read temperature in Kelvin";
            case CHISEL -> "CTRL: carve one 5x5 cell into a casting mold";
            case PINCERS -> "CTRL: remove a casting from a mold without heat damage";
        };
        tooltip.add(Component.literal(usage).withStyle(ChatFormatting.GRAY));
        GT6Tooltip.nbt(tooltip, "tool:\"" + kind.name().toLowerCase() + "\"");
    }
}
