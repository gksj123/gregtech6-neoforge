package gregtech.neo.item;

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
        THERMOMETER
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
        tooltip.add(Component.translatable("item.gregtech.tool.usage." + kind.name().toLowerCase()));
    }
}
