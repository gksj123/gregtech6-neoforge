package gregtech.neo.block;

import com.mojang.serialization.MapCodec;
import gregtech.neo.casting.CastingPart;
import gregtech.neo.casting.GTMoldRegistry;
import gregtech.neo.item.GT6ToolItem;
import gregtech.neo.item.MoldItem;
import gregtech.neo.registry.GTItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
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

public final class MoldBlock extends BaseEntityBlock {
    public static final MapCodec<MoldBlock> CODEC = simpleCodec(MoldBlock::new);
    private static final VoxelShape SELECTION = Shapes.or(
        box(0, 0, 0, 16, 13, 16),
        box(1, 1, 1, 15, 14, 15)
    );
    private static final VoxelShape COLLISION = box(2, 0, 2, 14, 14, 14);

    public MoldBlock(Properties properties) {
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
        BlockGetter level,
        BlockPos pos,
        CollisionContext context
    ) {
        return SELECTION;
    }

    @Override
    protected VoxelShape getCollisionShape(
        BlockState state,
        BlockGetter level,
        BlockPos pos,
        CollisionContext context
    ) {
        return COLLISION;
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SELECTION;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MoldBlockEntity(pos, state);
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
        if (!(level.getBlockEntity(pos) instanceof MoldBlockEntity mold)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.getItem() instanceof GT6ToolItem tool) {
            switch (tool.kind()) {
                case CHISEL -> {
                    if (hit.getDirection() == Direction.UP) {
                        mold.carve(hit);
                    }
                    InWorldMessages.send(player, mold.describe());
                    return ItemInteractionResult.SUCCESS;
                }
                case WRENCH, SCREWDRIVER -> {
                    mold.rotate();
                    InWorldMessages.send(player, mold.describe());
                    return ItemInteractionResult.SUCCESS;
                }
                case MONKEY_WRENCH -> {
                    mold.toggleAutoPull();
                    InWorldMessages.send(player, mold.describe());
                    return ItemInteractionResult.SUCCESS;
                }
                case SOFT_HAMMER -> {
                    mold.resetAutomation();
                    InWorldMessages.send(player, mold.describe());
                    return ItemInteractionResult.SUCCESS;
                }
                case THERMOMETER, MAGNIFYING_GLASS, ELECTROMETER -> {
                    InWorldMessages.send(player, mold.describe());
                    return ItemInteractionResult.SUCCESS;
                }
                default -> {
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                }
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        BlockHitResult hit
    ) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MoldBlockEntity mold) {
            if (hit.getDirection() == Direction.UP) {
                mold.takeOrPour(player);
            }
            InWorldMessages.send(player, mold.describe());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void entityInside(
        BlockState state,
        Level level,
        BlockPos pos,
        Entity entity
    ) {
        if (!level.isClientSide() && entity instanceof LivingEntity living
            && level.getBlockEntity(pos) instanceof MoldBlockEntity mold) {
            mold.damageTouchingEntity(living);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MoldBlockEntity mold) {
            ItemStack output = mold.takeOutputForTesting();
            if (!output.isEmpty() && !player.isCreative()) {
                Block.popResource(level, pos, output);
            }
            if (!player.isCreative() && level instanceof ServerLevel serverLevel) {
                Block.popResource(
                    serverLevel,
                    pos,
                    MoldItem.create(
                        GTItems.MOLD.get(),
                        new CastingPart(
                            mold.wallMaterial(),
                            mold.sourceLine(),
                            mold.shapeMask(),
                            mold.acidProof()
                        )
                    )
                );
            }
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
            if (blockEntity instanceof MoldBlockEntity mold) {
                mold.tick();
            }
        };
    }
}
