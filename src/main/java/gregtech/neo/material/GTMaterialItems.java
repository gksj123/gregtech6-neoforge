package gregtech.neo.material;

import gregtech.neo.item.MaterialPartItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
        "ingotHot", "billet", "chunkGt", "plate", "plateTiny", "plateDouble", "plateDense",
        "stick", "stickLong",
        "gearGt", "gearGtSmall", "rotor", "screw", "bolt", "ring", "foil",
        "wireFine", "casingSmall", "lens", "plateGem", "plateGemTiny",
        "toolHeadRawSword", "toolHeadRawPickaxe", "toolHeadRawSpade",
        "toolHeadRawShovel", "toolHeadRawUniversalSpade", "toolHeadRawAxe",
        "toolHeadRawAxeDouble", "toolHeadRawSaw", "toolHeadHammer",
        "toolHeadFile", "toolHeadScrewdriver", "toolHeadRawChisel",
        "toolHeadRawArrow", "toolHeadRawHoe", "toolHeadRawSense",
        "toolHeadRawPlow", "toolHeadBuilderwand"
    );
    private static final Set<String> PRIMARY_DISPLAY_PREFIXES = Set.of(
        "dust", "ingot", "plate", "gem", "rawOreChunk", "crushed",
        "stick", "gearGt", "wireGt01"
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
        Map<String, ItemStack> stacks = new LinkedHashMap<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (Map.Entry<String, DeferredItem<MaterialPartItem>> entry : PREFIX_ITEMS.entrySet()) {
            if (PRIMARY_DISPLAY_PREFIXES.contains(entry.getKey())) {
                for (GTMaterial material : GTMaterialRegistry.materials()) {
                    addCreativeStack(stacks, seen, entry, material.slug(), entry.getKey());
                }
            }
            if (CORE_PREFIXES.contains(entry.getKey())) {
                for (String materialSlug : CORE_MATERIALS) {
                    if (GTMaterialRegistry.material(materialSlug) != null) {
                        addCreativeStack(stacks, seen, entry, materialSlug, entry.getKey());
                    }
                }
            }
        }
        return new ArrayList<>(stacks.values());
    }

    private static void addCreativeStack(
        Map<String, ItemStack> stacks,
        Set<String> seen,
        Map.Entry<String, DeferredItem<MaterialPartItem>> entry,
        String material,
        String prefix
    ) {
        String key = material + "|" + prefix;
        if (seen.add(key)) {
            stacks.put(key, MaterialPartItem.create(entry.getValue().get(), material, prefix, 1));
        }
    }

    public static boolean canCreate(String material, String prefix) {
        return PREFIX_ITEMS.containsKey(prefix)
            && GTMaterialRegistry.material(material) != null
            && GTMaterialRegistry.prefix(prefix) != null
            && GTMaterialRegistry.prefix(prefix).amount() > 0;
    }

    public static ItemStack createPart(String material, String prefix, int count) {
        DeferredItem<MaterialPartItem> holder = PREFIX_ITEMS.get(prefix);
        if (holder == null || GTMaterialRegistry.material(material) == null || count <= 0) {
            return ItemStack.EMPTY;
        }
        return MaterialPartItem.create(holder.get(), material, prefix, Math.min(64, count));
    }
}
