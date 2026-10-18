package gregtech.neo.item;

import net.minecraft.world.item.Item;

public final class GT6ToolItem extends Item {
    public enum ToolKind {
        WRENCH,
        SCREWDRIVER,
        SOFT_HAMMER,
        MAGNIFYING_GLASS,
        MONKEY_WRENCH,
        ELECTROMETER
    }

    private final ToolKind kind;

    public GT6ToolItem(ToolKind kind, Properties properties) {
        super(properties.stacksTo(1));
        this.kind = kind;
    }

    public ToolKind kind() {
        return kind;
    }
}
