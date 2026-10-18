package gregtech.neo.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public final class GT6DemoGuideItem extends Item {
    private static final String[] GUIDE_LINES = {
        "Wrench: rotate multiblock controllers and axles.",
        "Screwdriver: toggle machines and cycle port modes.",
        "Soft hammer: force structure checks and reset controls.",
        "Magnifying glass / electrometer: read machine and energy details.",
        "Monkey wrench: toggle automatic input/output behavior.",
        "Burning Box: right-click fuel into it; it sends HU upward.",
        "Crucible: place it above a burning box; right-click material parts into the controller.",
        "Material items show GT6 heat, density, tool and original prefix data.",
        "Machines intentionally have no GUI. Use the world and tools."
    };

    public GT6DemoGuideItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            for (String line : GUIDE_LINES) {
                player.displayClientMessage(Component.literal("[GT6 Demo] " + line), false);
            }
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(
        ItemStack stack,
        TooltipContext context,
        List<Component> tooltip,
        TooltipFlag flag
    ) {
        tooltip.add(Component.translatable("item.gregtech.demo_guide.tooltip"));
        tooltip.add(Component.translatable("item.gregtech.demo_guide.use"));
    }
}
