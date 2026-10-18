package gregtech.neo.block;

import com.mojang.serialization.MapCodec;
import gregtech.neo.item.GT6ToolItem;
import gregtech.neo.item.SmallCrucibleItem;
import gregtech.neo.machine.MachinePart;
import gregtech.neo.registry.GTBlockEntities;
import gregtech.neo.registry.GTItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class SmallCrucibleBlock extends BaseEntityBlock {
    public static final MapCodec<SmallCrucibleBlock> CODEC = simpleCodec(SmallCrucibleBlock::new);
    private static final VoxelShape OUTLINE_SHAPE = Shapes.or(
        box(0, 0, 0, 2, 16, 16),
        box(14, 0, 0, 16, 16, 16),
        box(2, 0, 0, 14, 16, 2),
        box(2, 0, 14, 14, 16, 16),
        box(2, 0, 2, 14, 2, 14)
    );
    private static final VoxelShape COLLISION_SHAPE = Shapes.or(
        box(1, 1, 1, 2, 15, 15),
        box(14, 1, 1, 15, 15, 15),
        box(2, 1, 1, 14, 15, 2),
        box(2, 1, 14, 14, 15, 15),
        box(2, 1, 2, 14, 2, 14)
    );

    public SmallCrucibleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(
        BlockState state,
        net.minecraft.world.level.BlockGetter level,
        BlockPos pos,
        CollisionContext context
    ) {
        return OUTLINE_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(
        BlockState state,
        net.minecraft.world.level.BlockGetter level,
        BlockPos pos,
        CollisionContext context
    ) {
        return COLLISION_SHAPE;
    }

    @Override
    protected VoxelShape getInteractionShape(
        BlockState state,
        net.minecraft.world.level.BlockGetter level,
        BlockPos pos
    ) {
        return OUTLINE_SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SmallCrucibleBlockEntity(pos, state);
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
        if (!(level.getBlockEntity(pos) instanceof SmallCrucibleBlockEntity crucible)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.getItem() instanceof GT6ToolItem tool) {
            switch (tool.kind()) {
                case SCREWDRIVER -> {
                    crucible.toggle();
                    InWorldMessages.send(player, crucible.describe());
                    return ItemInteractionResult.SUCCESS;
                }
                case THERMOMETER, MAGNIFYING_GLASS, ELECTROMETER, SOFT_HAMMER -> {
                    InWorldMessages.send(player, thermometerStatus(crucible));
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
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SmallCrucibleBlockEntity crucible) {
            InWorldMessages.send(player, crucible.describe());
        }
        return InteractionResult.SUCCESS;
    }

    public static Component thermometerStatus(TemperatureProvider provider) {
        return Component.translatable(
            "message.gregtech.thermometer.reading",
            provider.temperatureSourceName(),
            provider.temperatureKelvin(),
            provider.maximumTemperatureKelvin()
        );
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.isCreative()
            && level.getBlockEntity(pos) instanceof SmallCrucibleBlockEntity crucible
            && crucible.definition() != null && level instanceof ServerLevel serverLevel) {
            Block.popResource(serverLevel, pos, SmallCrucibleItem.create(
                GTItems.SMALL_CRUCIBLE.get(),
                new MachinePart(
                    crucible.definition().line(),
                    crucible.definition().category()
                )
            ));
        }
        return super.playerWillDestroy(level, pos, state, player);
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
            if (blockEntity instanceof SmallCrucibleBlockEntity crucible) {
                crucible.tick();
            }
        };
    }
}
