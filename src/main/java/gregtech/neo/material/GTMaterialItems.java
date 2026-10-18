package gregtech.neo.material;

import gregtech.neo.item.MaterialPartItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GTMaterialItems {
    private static final Set<String> CORE_MATERIALS = Set.of(
        "iron", "copper", "tin", "gold", "silver", "lead", "nickel", "zinc",
        "bronze", "brass", "steel", "stainlesssteel", "aluminium", "titanium",
        "tungsten", "tungstensteel", "uranium", "plutonium", "rubber", "plastic"
    );
    private static final Set<String> CORE_PREFIXES = Set.of(
        "dust", "dustSmall", "dustTiny", "crushed", "gem", "nugget", "ingot",
        "ingotHot", "plate", "plateDouble", "plateDense", "stick", "stickLong",
        "gearGt", "gearGtSmall", "rotor", "screw", "bolt", "ring", "foil",
        "wireFine", "casingSmall", "lens", "plateGem", "plateGemTiny"
    );

    public static final Map<String, DeferredItem<MaterialPartItem>> PREFIX_ITEMS = new LinkedHashMap<>();

    private GTMaterialItems() {
    }

    public static void register(DeferredRegister.Items items) {
        GTMaterialRegistry.bootstrap();
        Set<String> usedPaths = new java.util.HashSet<>();
        for (GTMaterialPrefix prefix : GTMaterialRegistry.prefixes()) {
            String path = prefix.itemPath();
            if (!usedPaths.add(path)) {
                continue;
            }
            DeferredItem<MaterialPartItem> holder = items.register(
                path,
                () -> new MaterialPartItem(new Item.Properties())
            );
            PREFIX_ITEMS.put(prefix.id(), holder);
        }
    }

    public static List<ItemStack> creativeStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (Map.Entry<String, DeferredItem<MaterialPartItem>> entry : PREFIX_ITEMS.entrySet()) {
            if (!CORE_PREFIXES.contains(entry.getKey())) {
                continue;
            }
            for (String materialSlug : CORE_MATERIALS) {
                if (GTMaterialRegistry.material(materialSlug) == null) {
                    continue;
                }
                stacks.add(MaterialPartItem.create(entry.getValue().get(), materialSlug, entry.getKey(), 1));
            }
        }
        return stacks;
    }

    public static ItemStack createPart(String material, String prefix, int count) {
        DeferredItem<MaterialPartItem> holder = PREFIX_ITEMS.get(prefix);
        if (holder == null || GTMaterialRegistry.material(material) == null || count <= 0) {
            return ItemStack.EMPTY;
        }
        return MaterialPartItem.create(holder.get(), material, prefix, Math.min(64, count));
    }
}
