package gregtech.neo.item;

import gregtech.neo.block.MoldBlockEntity;
import gregtech.neo.casting.CastingPart;
import gregtech.neo.casting.GTMoldRegistry;
import gregtech.neo.material.GTMaterial;
import gregtech.neo.material.GTMaterialGameplayRegistry;
import gregtech.neo.material.GTMaterialPrefix;
import gregtech.neo.material.GTMaterialRegistry;
import gregtech.neo.registry.GTDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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

public final class MoldItem extends BlockItem {
    public MoldItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static ItemStack create(Item item, CastingPart part) {
        ItemStack stack = new ItemStack(item);
        stack.set(GTDataComponents.CASTING_PART.get(), part);
        return stack;
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        if (stack.get(GTDataComponents.CASTING_PART.get()) == null) {
            stack.set(
                GTDataComponents.CASTING_PART.get(),
                new CastingPart("stone", 1050, 0, false)
            );
        }
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
        CastingPart part = stack.get(GTDataComponents.CASTING_PART.get());
        if (part == null || !(level.getBlockEntity(pos) instanceof MoldBlockEntity mold)) {
            return false;
        }
        mold.configure(part);
        return true;
    }

    @Override
    public Component getName(ItemStack stack) {
        CastingPart part = stack.get(GTDataComponents.CASTING_PART.get());
        if (part == null) {
            return Component.literal("Mold (Unassigned)");
        }
        GTMaterial material = GTMaterialRegistry.material(part.wallMaterial());
        String wall = material == null ? part.wallMaterial() : material.prettyName();
        if (part.shapeMask() == 0) {
            return Component.literal("Mold (" + wall + ")");
        }
        GTMoldRegistry.MaskLookup shape = GTMoldRegistry.lookup(part.shapeMask());
        return Component.literal("Mold (" + wall + ") [" + prettyForm(shape.prefix()) + "]");
    }

    @Override
    public void appendHoverText(
        ItemStack stack,
        TooltipContext context,
        List<Component> tooltip,
        TooltipFlag flag
    ) {
        super.appendHoverText(stack, context, tooltip, flag);
        CastingPart part = stack.get(GTDataComponents.CASTING_PART.get());
        if (part == null) {
            return;
        }
        GTMoldRegistry.MaskLookup shape = GTMoldRegistry.lookup(part.shapeMask());
        GTMaterial material = GTMaterialRegistry.material(part.wallMaterial());
        GTMaterialGameplayRegistry.MaterialGameplay gameplay =
            GTMaterialGameplayRegistry.material(part.wallMaterial());
        GTMaterialPrefix prefix = GTMaterialRegistry.prefix(shape.prefix());
        long required = GTMoldRegistry.requiredUnits(part.shapeMask());
        long limit = gameplay == null ? 1250 : (long) (gameplay.meltingKelvin() * 1.25D);

        GT6Tooltip.header(tooltip, "CASTING MOLD");
        GT6Tooltip.source(tooltip, "Molds", part.sourceLine());
        GT6Tooltip.line(tooltip, ChatFormatting.AQUA,
            "WALL: " + part.wallMaterial()
                + " // MP " + (gameplay == null ? "?" : format(gameplay.meltingKelvin())) + "K"
                + " // LIMIT " + limit + "K");
        GT6Tooltip.line(tooltip, ChatFormatting.LIGHT_PURPLE,
            "SHAPE: " + shape.sourceShape() + " // PREFIX " + shape.prefix()
                + " // MASK 0x" + Integer.toHexString(part.shapeMask()));
        GT6Tooltip.line(tooltip, ChatFormatting.YELLOW,
            "REQ: " + displayUnits(required) + "U"
                + " // OUTPUT: " + (prefix == null ? shape.prefix() : prefix.prettyCategory()));
        if (part.acidProof()) {
            GT6Tooltip.line(tooltip, ChatFormatting.GOLD, "ACID: PROOF");
        }
        GT6Tooltip.line(tooltip, ChatFormatting.GREEN,
            "CTRL: TOP empty hand -> pull/pick up // CU -> cool");
        GT6Tooltip.line(tooltip, ChatFormatting.GRAY,
            "TOOL: chisel carve // wrench rotate // thermometer read");
        GT6Tooltip.nbt(tooltip,
            "gregtech:casting_part={wall:\"" + part.wallMaterial()
                + "\",line:" + part.sourceLine()
                + ",shape:" + part.shapeMask()
                + ",acid:" + part.acidProof() + "}"
                + ", state:\"EMPTY\"");
    }

    private static String prettyForm(String prefix) {
        return switch (prefix) {
            case "chunkGt" -> "Chunk";
            case "casingSmall" -> "Small Casing";
            case "gearGt" -> "Gear";
            case "gearGtSmall" -> "Small Gear";
            case "stick" -> "Rod";
            case "stickLong" -> "Long Rod";
            case "plateTiny" -> "Tiny Plate";
            default -> {
                String value = prefix.replace("toolHeadRaw", "")
                    .replace("toolHead", "")
                    .replace("Gt", "");
                yield value.isBlank()
                    ? prefix
                    : Character.toUpperCase(value.charAt(0)) + value.substring(1);
            }
        };
    }

    private static String format(double value) {
        return Long.toString(Math.round(value));
    }

    private static String displayUnits(long amount) {
        long whole = amount / GTMaterialGameplayRegistry.MATERIAL_UNIT;
        long thousandths = (amount % GTMaterialGameplayRegistry.MATERIAL_UNIT)
            * 1000 / GTMaterialGameplayRegistry.MATERIAL_UNIT;
        return whole + "." + String.format(java.util.Locale.ROOT, "%03d", thousandths);
    }
}
