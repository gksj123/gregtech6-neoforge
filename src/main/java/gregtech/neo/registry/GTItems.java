package gregtech.neo.registry;

import gregtech.neo.GT6Neo;
import gregtech.neo.item.GT6ToolItem;
import gregtech.neo.material.GTMaterialItems;
import gregtech.neo.item.BasicMachineItem;
import gregtech.neo.item.BurningBoxItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GTItems {
    private GTItems() {
    }

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GT6Neo.MOD_ID);

    public static final DeferredItem<GT6ToolItem> WRENCH = tool("wrench", GT6ToolItem.ToolKind.WRENCH);
    public static final DeferredItem<GT6ToolItem> SCREWDRIVER = tool("screwdriver", GT6ToolItem.ToolKind.SCREWDRIVER);
    public static final DeferredItem<GT6ToolItem> SOFT_HAMMER = tool("soft_hammer", GT6ToolItem.ToolKind.SOFT_HAMMER);
    public static final DeferredItem<GT6ToolItem> MAGNIFYING_GLASS =
        tool("magnifying_glass", GT6ToolItem.ToolKind.MAGNIFYING_GLASS);
    public static final DeferredItem<GT6ToolItem> MONKEY_WRENCH =
        tool("monkey_wrench", GT6ToolItem.ToolKind.MONKEY_WRENCH);
    public static final DeferredItem<GT6ToolItem> ELECTROMETER =
        tool("electrometer", GT6ToolItem.ToolKind.ELECTROMETER);
    public static final DeferredItem<BasicMachineItem> BASIC_MACHINE =
        ITEMS.register("basic_machine", () -> new BasicMachineItem(GTBlocks.BASIC_MACHINE.get(), new Item.Properties()));
    public static final DeferredItem<BurningBoxItem> BURNING_BOX =
        ITEMS.register("burning_box", () -> new BurningBoxItem(GTBlocks.BURNING_BOX.get(), new Item.Properties()));

    private static DeferredItem<GT6ToolItem> tool(String name, GT6ToolItem.ToolKind kind) {
        return ITEMS.register(name, () -> new GT6ToolItem(kind, new Item.Properties()));
    }

    static {
        GTMaterialItems.register(ITEMS);
    }
}
