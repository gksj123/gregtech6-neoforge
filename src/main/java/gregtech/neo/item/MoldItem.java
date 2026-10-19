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

/**
 * GT6 1.7.10: gregtech/loaders/b/Loader_MultiTileEntities.java:345-388 and
 * gregtech/tileentity/tools/MultiTileEntityMold.java:121-134.
 *
 * <p>The source item is a blank mold. Its shape is carved in-world with a
 * chisel and stored in "gt.mold"; the table never contains pre-carved molds.
 * The modern port stores the wall material, source line and carved mask in a
 * data component, but keeps the same blank default and the same tooltip text
 * and colour order.</p>
 */
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
        if (part == null || part.wallMaterial() == null || part.wallMaterial().isBlank()) {
            return Component.literal("Mold (Unassigned)");
        }
        GTMaterial material = GTMaterialRegistry.material(part.wallMaterial());
        String wall = material == null ? part.wallMaterial() : material.prettyName();
        return Component.literal("Mold (" + wall + ")");
    }

    /**
     * GT6 1.7.10: MultiTileEntityMold.java:121-134. Keep the source order:
     * recipe/shape line, no-GUI hint, acid-proof hint, meltdown limit, contact
     * hazard, pincer hint, monkey-wrench hint, soft-hammer hint.
     */
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
        int mask = part.shapeMask() & MoldBlockEntity.MAX_MASK;
        String wallMaterial = part.wallMaterial() == null ? "" : part.wallMaterial();
        GTMaterialGameplayRegistry.MaterialGameplay wall =
            GTMaterialGameplayRegistry.material(wallMaterial);
        long limit = wall == null ? 1250L : (long) (wall.meltingKelvin() * 1.25D);

        if (mask == 0) {
            tooltip.add(Component.literal(
                "Use a Chisel in order to select the Shape of the Mold"
            ).withStyle(ChatFormatting.AQUA));
        } else {
            String prefixId = GTMoldRegistry.prefixForMask(mask);
            GTMaterialPrefix prefix = GTMaterialRegistry.prefix(prefixId);
            String name = prefix == null ? prefixId : prefix.prettyCategory();
            tooltip.add(Component.literal(
                "This Mold produces " + name + " "
                    + displayUnits(GTMoldRegistry.requiredUnits(mask)) + " Units"
            ).withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.literal(
            "No GUI, click to interact (Top)"
        ).withStyle(ChatFormatting.GOLD));
        if (part.acidProof()) {
            tooltip.add(Component.literal("Can handle Acids").withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.literal(
            "Melts down when stored Heat is too much! (" + limit + " K)"
        ).withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.literal(
            "Causes Damage when touched while active!"
        ).withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.literal(
            "Use Pincers or an empty hand to take out the Casting"
        ).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal(
            "Use Monkey Wrench to toggle Auto-Inputs"
        ).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal(
            "Use Soft Hammer to reset Auto-Inputs"
        ).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String displayUnits(long amount) {
        long whole = amount / GTMaterialGameplayRegistry.MATERIAL_UNIT;
        long thousandths = (amount % GTMaterialGameplayRegistry.MATERIAL_UNIT)
            * 1000 / GTMaterialGameplayRegistry.MATERIAL_UNIT;
        return whole + "." + String.format(java.util.Locale.ROOT, "%03d", thousandths);
    }
}