package gregtech.neo.material;

import gregtech.neo.block.MaterialStorageBlock;
import gregtech.neo.item.MaterialBlockItem;
import gregtech.neo.registry.GTItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GTMaterialBlocks {
    private static final List<String> BLOCK_PREFIXES = List.of(
        "blockRaw",
        "blockGem",
        "blockDust",
        "blockIngot",
        "blockPlate",
        "blockPlateGem",
        "blockSolid",
        "casingMachine",
        "casingMachineDouble",
        "casingMachineQuadruple",
        "casingMachineDense"
    );

    public static final Map<String, DeferredBlock<MaterialStorageBlock>> BLOCKS = new LinkedHashMap<>();
    public static final Map<String, DeferredItem<MaterialBlockItem>> ITEMS = new LinkedHashMap<>();

    private GTMaterialBlocks() {
    }

    public static void register(DeferredRegister.Blocks blocks) {
        GTMaterialRegistry.bootstrap();
        for (String prefix : BLOCK_PREFIXES) {
            if (GTMaterialRegistry.prefix(prefix) == null) {
                continue;
            }
            String path = "meta_block_" + GTMaterial.slug(prefix);
            DeferredBlock<MaterialStorageBlock> block = blocks.register(
                path,
                () -> new MaterialStorageBlock(
                    BlockBehaviour.Properties.of()
                        .mapColor(MapColor.METAL)
                        .sound(SoundType.METAL)
                        .strength(3.0F, 8.0F),
                    prefix
                )
            );
            BLOCKS.put(prefix, block);
            ITEMS.put(
                prefix,
                GTItems.ITEMS.register(path, () -> new MaterialBlockItem(block.get(), new Item.Properties()))
            );
        }
    }

    public static List<ItemStack> creativeStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (Map.Entry<String, DeferredItem<MaterialBlockItem>> entry : ITEMS.entrySet()) {
            for (GTMaterial material : GTMaterialRegistry.materials()) {
                stacks.add(MaterialBlockItem.create(
                    entry.getValue().get(),
                    material.slug(),
                    entry.getKey(),
                    1
                ));
            }
        }
        return stacks;
    }

    public static ItemStack creativeStack(String materialSlug, String prefixId) {
        DeferredItem<MaterialBlockItem> item = ITEMS.get(prefixId);
        if (item == null || GTMaterialRegistry.material(materialSlug) == null) {
            return ItemStack.EMPTY;
        }
        return MaterialBlockItem.create(item.get(), materialSlug, prefixId, 1);
    }
}
