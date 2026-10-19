package gregtech.neo.registry;

import gregtech.neo.GT6Neo;
import gregtech.neo.item.GT6ToolItem;
import gregtech.neo.material.GTMaterialItems;
import gregtech.neo.item.BasicMachineItem;
import gregtech.neo.item.BurningBoxItem;
import gregtech.neo.item.GT6DemoGuideItem;
import gregtech.neo.item.MoldItem;
import gregtech.neo.item.FaucetItem;
import gregtech.neo.item.CeramicMoldRawItem;
import gregtech.neo.item.SmallCrucibleItem;
import gregtech.neo.item.SteamBoilerItem;
import gregtech.neo.item.SteamEngineItem;
import gregtech.neo.item.SteamTurbineItem;
import gregtech.neo.casting.CastingPart;
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
    public static final DeferredItem<GT6ToolItem> THERMOMETER =
        tool("thermometer", GT6ToolItem.ToolKind.THERMOMETER);
    public static final DeferredItem<GT6ToolItem> CHISEL =
        tool("chisel", GT6ToolItem.ToolKind.CHISEL);
    // GT6 1.7.10: gregtech/tileentity/tools/MultiTileEntityMold.java:370-374.
    public static final DeferredItem<GT6ToolItem> PINCERS =
        tool("pincers", GT6ToolItem.ToolKind.PINCERS);
    public static final DeferredItem<GT6DemoGuideItem> GT6_DEMO_GUIDE =
        ITEMS.register("gt6_demo_guide", () -> new GT6DemoGuideItem(new Item.Properties()));
    public static final DeferredItem<BasicMachineItem> BASIC_MACHINE =
        ITEMS.register("basic_machine", () -> new BasicMachineItem(GTBlocks.BASIC_MACHINE.get(), new Item.Properties()));
    public static final DeferredItem<BurningBoxItem> BURNING_BOX =
        ITEMS.register("burning_box", () -> new BurningBoxItem(GTBlocks.BURNING_BOX.get(), new Item.Properties()));
    public static final DeferredItem<SmallCrucibleItem> SMALL_CRUCIBLE =
        ITEMS.register("small_crucible", () -> new SmallCrucibleItem(
            GTBlocks.SMALL_CRUCIBLE.get(),
            new Item.Properties()
        ));
    public static final DeferredItem<MoldItem> MOLD =
        ITEMS.register("mold", () -> new MoldItem(
            GTBlocks.MOLD.get(),
            new Item.Properties()
                .stacksTo(16)
                .component(
                    GTDataComponents.CASTING_PART.get(),
                    new CastingPart("stone", 1050, 0, false)
                )
        ));
    public static final DeferredItem<FaucetItem> FAUCET =
        ITEMS.register("faucet", () -> new FaucetItem(
            GTBlocks.FAUCET.get(),
            new Item.Properties()
                .stacksTo(16)
                .component(
                    GTDataComponents.CASTING_PART.get(),
                    new CastingPart("stone", 1700, 0, false)
                )
        ));
    public static final DeferredItem<CeramicMoldRawItem> CERAMIC_MOLD_RAW =
        ITEMS.register("ceramic_mold_raw", () -> new CeramicMoldRawItem(new Item.Properties()));

    public static final DeferredItem<SteamBoilerItem> STEAM_BOILER =
        ITEMS.register("steam_boiler", () -> new SteamBoilerItem(GTBlocks.STEAM_BOILER.get(), new Item.Properties()));

    public static final DeferredItem<SteamEngineItem> STEAM_ENGINE =
        ITEMS.register("steam_engine", () -> new SteamEngineItem(GTBlocks.STEAM_ENGINE.get(), new Item.Properties()));

    public static final DeferredItem<SteamTurbineItem> STEAM_TURBINE =
        ITEMS.register("steam_turbine", () -> new SteamTurbineItem(GTBlocks.STEAM_TURBINE.get(), new Item.Properties()));

    private static DeferredItem<GT6ToolItem> tool(String name, GT6ToolItem.ToolKind kind) {
        return ITEMS.register(name, () -> new GT6ToolItem(kind, new Item.Properties()));
    }

    static {
        GTMaterialItems.register(ITEMS);
    }
}
