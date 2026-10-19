package gregtech.neo.block;

import com.mojang.serialization.MapCodec;
import gregtech.neo.casting.CastingPart;
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
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * GT6 1.7.10: gregtech/tileentity/tools/MultiTileEntityMold.java.
 *
 * <p>The source is a flat tray, not a deep box: the base is one pixel, the
 * outer walls four pixels and the 5x5 cell grid three pixels tall. The original
 * collision box is only the central 12x12x2 px footprint and the selection box
 * is a full 16x16x3 px plate. These dimensions are reproduced exactly.</p>
 */
public final class MoldBlock extends BaseEntityBlock {
    public static final MapCodec<MoldBlock> CODEC = simpleCodec(MoldBlock::new);
    private static final VoxelShape SELECTION = box(0, 0, 0, 16, 3, 16);
    private static final VoxelShape COLLISION = box(2, 0, 2, 14, 2, 14);

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
        boolean toolItem = stack.getItem() instanceof GT6ToolItem;
        if (level.isClientSide()) {
            if (toolItem) {
                GT6ToolItem.ToolKind kind = ((GT6ToolItem) stack.getItem()).kind();
                if ((kind == GT6ToolItem.ToolKind.CHISEL
                    || kind == GT6ToolItem.ToolKind.MONKEY_WRENCH)
                    && hit.getDirection() != Direction.UP) {
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                }
                return switch (kind) {
                    case CHISEL, WRENCH, SCREWDRIVER, MONKEY_WRENCH, SOFT_HAMMER,
                        THERMOMETER, MAGNIFYING_GLASS, ELECTROMETER, PINCERS ->
                        ItemInteractionResult.SUCCESS;
                    default -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                };
            }
            return hit.getDirection() == Direction.DOWN
                ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                : ItemInteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof MoldBlockEntity mold)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (toolItem) {
            GT6ToolItem.ToolKind kind = ((GT6ToolItem) stack.getItem()).kind();
            switch (kind) {
                case CHISEL -> {
                    if (hit.getDirection() != Direction.UP) {
                        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                    }
                    mold.carve(hit);
                    return ItemInteractionResult.SUCCESS;
                }
                case WRENCH, SCREWDRIVER -> {
                    mold.rotate();
                    return ItemInteractionResult.SUCCESS;
                }
                case MONKEY_WRENCH -> {
                    String message = mold.toggleAutoPull(hit.getDirection(), hit);
                    if (message == null) {
                        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                    }
                    InWorldMessages.send(
                        player,
                        net.minecraft.network.chat.Component.literal(message)
                    );
                    return ItemInteractionResult.SUCCESS;
                }
                case SOFT_HAMMER -> {
                    InWorldMessages.send(
                        player,
                        net.minecraft.network.chat.Component.literal(mold.resetAutomation())
                    );
                    return ItemInteractionResult.SUCCESS;
                }
                case THERMOMETER -> {
                    InWorldMessages.send(
                        player,
                        net.minecraft.network.chat.Component.literal(
                            "Temperature: " + mold.contentTemperature() + "K"
                        )
                    );
                    return ItemInteractionResult.SUCCESS;
                }
                case PINCERS -> {
                    mold.pickUp(player, false);
                    return ItemInteractionResult.SUCCESS;
                }
                case MAGNIFYING_GLASS, ELECTROMETER -> {
                    InWorldMessages.send(player, mold.describe());
                    return ItemInteractionResult.SUCCESS;
                }
                default -> {
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                }
            }
        }

        // GT6 1.7.10: MultiTileEntityMold.java:267-294. A right-click with any
        // non-tool item behaves like an empty-hand click: take the output first,
        // then try to pour from the side selected by the top-face hit position.
        if (hit.getDirection() == Direction.DOWN) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!mold.pickUp(player, true) && hit.getDirection() == Direction.UP) {
            mold.pourFromNeighborByHit(hit);
        }
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
        if (hit.getDirection() == Direction.DOWN) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof MoldBlockEntity mold) {
            if (!mold.pickUp(player, true) && hit.getDirection() == Direction.UP) {
                mold.pourFromNeighborByHit(hit);
            }
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
    protected void neighborChanged(
        BlockState state,
        Level level,
        BlockPos pos,
        Block neighborBlock,
        BlockPos neighborPos,
        boolean movedByPiston
    ) {
        if (!level.isClientSide()
            && level.getBlockEntity(pos) instanceof MoldBlockEntity mold) {
            mold.markBlockUpdated();
        }
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
    }

    /**
     * GT6 1.7.10: MultiTileEntityMold.java:416-419. The source discards the
     * molten content and drops the output slot plus the configured mold item.
     * The modern loot table is intentionally empty because the material and
     * carved mask live in the block entity, so the drop is built here.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()
            && level instanceof ServerLevel serverLevel
            && level.getBlockEntity(pos) instanceof MoldBlockEntity mold) {
            dropConfiguredMold(pos, serverLevel, mold, !player.isCreative());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, net.minecraft.world.level.Explosion explosion) {
        if (!level.isClientSide()
            && level instanceof ServerLevel serverLevel
            && level.getBlockEntity(pos) instanceof MoldBlockEntity mold) {
            dropConfiguredMold(pos, serverLevel, mold, true);
        }
        super.wasExploded(level, pos, explosion);
    }

    private static void dropConfiguredMold(
        BlockPos pos,
        ServerLevel serverLevel,
        MoldBlockEntity mold,
        boolean drop
    ) {
        ItemStack output = mold.takeOutputForTesting();
        if (!drop) {
            return;
        }
        if (!output.isEmpty()) {
            Block.popResource(serverLevel, pos, output);
        }
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
