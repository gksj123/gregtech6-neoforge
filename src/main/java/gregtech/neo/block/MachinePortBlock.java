package gregtech.neo.block;

import com.mojang.serialization.MapCodec;
import gregtech.neo.core.PortMode;
import gregtech.neo.item.GT6ToolItem;
import gregtech.neo.registry.GTBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
public final class MachinePortBlock extends BaseEntityBlock {
    public static final MapCodec<MachinePortBlock> CODEC = simpleCodec(MachinePortBlock::new);
    public static final EnumProperty<MachinePortMode> MODE = EnumProperty.create("mode", MachinePortMode.class);

    public MachinePortBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MODE, MachinePortMode.DISABLED));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MODE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MachinePortBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(
        ItemStack stack,
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        InteractionHand hand,
        BlockHitResult hit
    ) {
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        LargeCrusherBlockEntity controller = findController(level, pos);
        if (stack.getItem() instanceof GT6ToolItem tool) {
            switch (tool.kind()) {
                case SCREWDRIVER -> {
                    BlockState newState = state.cycle(MODE);
                    level.setBlock(pos, newState, Block.UPDATE_ALL);
                    InWorldMessages.send(player, Component.translatable(
                        "message.gregtech.port.mode",
                        newState.getValue(MODE)
                    ));
                    return ItemInteractionResult.SUCCESS;
                }
                case SOFT_HAMMER -> {
                    level.setBlock(pos, state.setValue(MODE, MachinePortMode.DISABLED), Block.UPDATE_ALL);
                    InWorldMessages.send(player, InWorldMessages.status("reset"));
                    return ItemInteractionResult.SUCCESS;
                }
                case MAGNIFYING_GLASS, ELECTROMETER -> {
                    describe(player, state, controller);
                    return ItemInteractionResult.SUCCESS;
                }
                default -> {
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                }
            }
        }

        if (state.getValue(MODE).mode().itemInput() && !stack.isEmpty()) {
            if (controller == null) {
                InWorldMessages.send(player, InWorldMessages.status("no_controller"));
                return ItemInteractionResult.SUCCESS;
            }
            ItemStack remainder = controller.insertItem(stack);
            if (remainder.getCount() != stack.getCount()) {
                player.setItemInHand(hand, remainder);
                InWorldMessages.send(player, InWorldMessages.status("item_inserted"));
            } else {
                InWorldMessages.send(player, InWorldMessages.status("input_full"));
            }
            return ItemInteractionResult.SUCCESS;
        }
        describe(player, state, controller);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        BlockHitResult hit
    ) {
        if (!level.isClientSide()) {
            LargeCrusherBlockEntity controller = findController(level, pos);
            PortMode mode = state.getValue(MODE).mode();
            if (mode.itemOutput() && controller != null) {
                ItemStack output = controller.extractOutput();
                if (!output.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(output);
                    InWorldMessages.send(player, InWorldMessages.status("item_extracted"));
                    return InteractionResult.SUCCESS;
                }
            }
            describe(player, state, controller);
        }
        return InteractionResult.SUCCESS;
    }

    private static void describe(Player player, BlockState state, LargeCrusherBlockEntity controller) {
        InWorldMessages.send(player, Component.translatable(
            "message.gregtech.port.status",
            state.getValue(MODE),
            controller != null,
            controller != null && controller.isFormed()
        ));
    }

    public static LargeCrusherBlockEntity findController(Level level, BlockPos portPos) {
        for (BlockPos candidate : BlockPos.betweenClosed(
            portPos.offset(-2, -2, -2),
            portPos.offset(2, 2, 2)
        )) {
            if (level.getBlockEntity(candidate) instanceof LargeCrusherBlockEntity controller
                && controller.isPart(portPos)) {
                return controller;
            }
        }
        return null;
    }

    public static PortMode mode(BlockState state) {
        return state.getValue(MODE).mode();
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        Level level,
        BlockState state,
        BlockEntityType<T> type
    ) {
        return null;
    }
}
