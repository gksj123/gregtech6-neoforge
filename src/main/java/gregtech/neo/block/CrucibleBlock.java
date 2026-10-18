package gregtech.neo.block;

import com.mojang.serialization.MapCodec;
import gregtech.neo.item.GT6ToolItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class CrucibleBlock extends BaseEntityBlock {
    public static final MapCodec<CrucibleBlock> CODEC = simpleCodec(CrucibleBlock::new);
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, 8);

    public CrucibleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FILL, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILL);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getCollisionShape(
        BlockState state,
        BlockGetter level,
        BlockPos pos,
        CollisionContext context
    ) {
        int fill = state.getValue(FILL);
        if (fill <= 0) {
            return Shapes.block();
        }
        double surface = 18.0 + fill * 27.2 / 8.0;
        return Shapes.or(
            Shapes.block(),
            box(-16, surface - 2.0, -16, 32, surface, 32)
        );
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrucibleBlockEntity(pos, state);
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
        if (!(level.getBlockEntity(pos) instanceof CrucibleBlockEntity crucible)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.getItem() instanceof ShovelItem
            && hit.getDirection() == net.minecraft.core.Direction.UP) {
            ItemStack output = crucible.shovelOutSolidContent();
            if (!output.isEmpty()) {
                player.getInventory().placeItemBackInInventory(output);
            }
            if (crucible.temperature() > 320) {
                player.setRemainingFireTicks(40);
                player.hurt(level.damageSources().onFire(), 1.0F);
            }
            InWorldMessages.send(player, crucible.describe());
            return ItemInteractionResult.SUCCESS;
        }
        if (stack.getItem() instanceof GT6ToolItem tool) {
            switch (tool.kind()) {
                case SCREWDRIVER -> {
                    crucible.toggle();
                    InWorldMessages.send(player, crucible.describe());
                    return ItemInteractionResult.SUCCESS;
                }
                case SOFT_HAMMER -> {
                    crucible.forceStructureCheck();
                    InWorldMessages.send(player, crucible.describe());
                    return ItemInteractionResult.SUCCESS;
                }
                case THERMOMETER -> {
                    InWorldMessages.send(player, SmallCrucibleBlock.thermometerStatus(crucible));
                    return ItemInteractionResult.SUCCESS;
                }
                case MAGNIFYING_GLASS, ELECTROMETER -> {
                    InWorldMessages.send(player, crucible.describe());
                    return ItemInteractionResult.SUCCESS;
                }
                default -> {
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                }
            }
        }
        if (crucible.insertItem(stack)) {
            stack.shrink(1);
            player.setItemInHand(hand, stack.isEmpty() ? ItemStack.EMPTY : stack);
        }
        InWorldMessages.send(player, crucible.describe());
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
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CrucibleBlockEntity crucible) {
            InWorldMessages.send(player, crucible.describe());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        Level level,
        BlockState state,
        BlockEntityType<T> type
    ) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof CrucibleBlockEntity crucible) {
                crucible.tick();
            }
        };
    }
}
