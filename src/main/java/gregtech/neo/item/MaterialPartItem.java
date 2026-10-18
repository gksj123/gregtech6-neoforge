package gregtech.neo.item;

import gregtech.neo.material.GTMaterial;
import gregtech.neo.material.GTMaterialGameplayRegistry;
import gregtech.neo.material.GTMaterialPrefix;
import gregtech.neo.material.GTMaterialRegistry;
import gregtech.neo.material.MaterialPart;
import gregtech.neo.registry.GTDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class MaterialPartItem extends Item {
    public MaterialPartItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(Item item, String materialSlug, String prefixId, int count) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(GTDataComponents.MATERIAL_PART.get(), new MaterialPart(materialSlug, prefixId));
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        MaterialPart part = stack.get(GTDataComponents.MATERIAL_PART.get());
        if (part == null) {
            return Component.translatable("item.gregtech.unassigned_material_part");
        }
        GTMaterial material = GTMaterialRegistry.material(part.material());
        GTMaterialPrefix prefix = GTMaterialRegistry.prefix(part.prefix());
        String materialName = material == null ? part.material() : material.prettyName();
        String formName = prefix == null ? part.prefix() : prefix.prettyCategory();
        return Component.translatable("item.gregtech.material_part.name", materialName, formName);
    }

    @Override
    public void appendHoverText(
        ItemStack stack,
        TooltipContext context,
        List<Component> tooltip,
        TooltipFlag flag
    ) {
        super.appendHoverText(stack, context, tooltip, flag);
        MaterialPart part = stack.get(GTDataComponents.MATERIAL_PART.get());
        if (part == null) {
            return;
        }
        GTMaterial material = GTMaterialRegistry.material(part.material());
        GTMaterialPrefix prefix = GTMaterialRegistry.prefix(part.prefix());
        if (material != null) {
            tooltip.add(Component.translatable(
                "item.gregtech.material_part.material",
                material.name(),
                material.symbol(),
                material.id()
            ));
        }
        if (prefix != null) {
            tooltip.add(Component.translatable(
                "item.gregtech.material_part.prefix",
                prefix.id(),
                prefix.category()
            ));
            tooltip.add(Component.translatable(
                "item.gregtech.material_part.category",
                prefix.category()
            ));
        }
        GTMaterialGameplayRegistry.MaterialGameplay gameplay =
            GTMaterialGameplayRegistry.material(part.material());
        if (gameplay != null) {
            tooltip.add(Component.translatable(
                "item.gregtech.material_part.thermal",
                gameplay.meltingKelvin(),
                gameplay.boilingKelvin(),
                gameplay.densityGramsPerCubicCentimeter()
            ));
            if (gameplay.toolTypes() > 0) {
                tooltip.add(Component.translatable(
                    "item.gregtech.material_part.tools",
                    gameplay.toolTypes(),
                    gameplay.toolSpeed(),
                    gameplay.toolDurability(),
                    gameplay.toolQuality()
                ));
            }
        }
        tooltip.add(Component.translatable("item.gregtech.material_part.usage"));
    }
}
