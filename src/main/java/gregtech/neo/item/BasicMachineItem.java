package gregtech.neo.item;

import gregtech.neo.block.BasicMachineBlockEntity;
import gregtech.neo.machine.GTMachineDefinition;
import gregtech.neo.machine.GTMachineRegistry;
import gregtech.neo.machine.MachinePart;
import gregtech.neo.registry.GTDataComponents;
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

public final class BasicMachineItem extends BlockItem {
    public BasicMachineItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static ItemStack create(Item item, MachinePart part) {
        ItemStack stack = new ItemStack(item);
        stack.set(GTDataComponents.MACHINE_PART.get(), part);
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
        MachinePart part = stack.get(GTDataComponents.MACHINE_PART.get());
        if (part == null || !(level.getBlockEntity(pos) instanceof BasicMachineBlockEntity machine)) {
            return false;
        }
        machine.setPart(part);
        return true;
    }

    @Override
    public Component getName(ItemStack stack) {
        MachinePart part = stack.get(GTDataComponents.MACHINE_PART.get());
        GTMachineDefinition definition = part == null ? null : GTMachineRegistry.find(part.category(), part.sourceLine());
        return definition == null
            ? Component.translatable("block.gregtech.basic_machine.unassigned")
            : Component.literal(definition.display_name());
    }

    @Override
    public void appendHoverText(
        ItemStack stack,
        TooltipContext context,
        List<Component> tooltip,
        TooltipFlag flag
    ) {
        super.appendHoverText(stack, context, tooltip, flag);
        MachinePart part = stack.get(GTDataComponents.MACHINE_PART.get());
        GTMachineDefinition definition = part == null ? null : GTMachineRegistry.find(part.category(), part.sourceLine());
        if (definition != null) {
            tooltip.add(Component.translatable(
                "block.gregtech.basic_machine.source",
                definition.line(),
                definition.machine_class()
            ));
            tooltip.add(Component.translatable(
                "block.gregtech.basic_machine.category",
                definition.category()
            ));
            tooltip.add(Component.translatable(
                "block.gregtech.basic_machine.energy",
                definition.energy_accepted(),
                definition.input()
            ));
            if (!definition.recipe_map().isBlank()) {
                tooltip.add(Component.translatable(
                    "block.gregtech.basic_machine.recipe_map",
                    definition.recipe_map()
                ));
            }
            tooltip.add(Component.translatable("block.gregtech.basic_machine.usage"));
        }
    }
}
