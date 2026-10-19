package gregtech.neo.block;

import com.mojang.serialization.MapCodec;
import gregtech.neo.item.GT6ToolItem;
import gregtech.neo.item.LargeBoilerItem;
import gregtech.neo.machine.MachinePart;
import gregtech.neo.registry.GTBlockEntities;
import gregtech.neo.registry.GTItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * GT6 1.7.10: gregtech/tileentity/multiblocks/MultiTileEntityLargeBoiler.java:74-148.
 */
public final class LargeBoilerBlock extends BaseEntityBlock {
    public static final MapCodec<LargeBoilerBlock> CODEC = simpleCodec(LargeBoilerBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public LargeBoilerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(FORMED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(FORMED, false);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LargeBoilerBlockEntity(pos, state);
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
        if (!(level.getBlockEntity(pos) instanceof LargeBoilerBlockEntity boiler)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (stack.is(Items.WATER_BUCKET)) {
            if (boiler.addWater(1_000)) {
                if (!player.isCreative()) {
                    player.setItemInHand(hand, new ItemStack(Items.BUCKET));
                }
                InWorldMessages.send(player, InWorldMessages.status("water_inserted"));
            }
            InWorldMessages.send(player, boiler.describe());
            return ItemInteractionResult.SUCCESS;
        }
        if (stack.getItem() instanceof GT6ToolItem tool) {
            switch (tool.kind()) {
                case WRENCH -> {
                    Direction rotated = state.getValue(FACING).getClockWise();
                    level.setBlock(
                        pos,
                        state.setValue(FACING, rotated).setValue(FORMED, false),
                        Block.UPDATE_ALL
                    );
                    if (level.getBlockEntity(pos) instanceof LargeBoilerBlockEntity rotatedBoiler) {
                        rotatedBoiler.forceStructureCheck();
                    }
                    InWorldMessages.send(player, InWorldMessages.status("rotated"));
                    return ItemInteractionResult.SUCCESS;
                }
                case CHISEL -> {
                    boiler.descale(player);
                    InWorldMessages.send(player, boiler.describe());
                    return ItemInteractionResult.SUCCESS;
                }
                case SOFT_HAMMER -> {
                    boiler.forceStructureCheck();
                    InWorldMessages.send(player, boiler.isFormed()
                        ? InWorldMessages.status("structure_formed")
                        : InWorldMessages.status("structure_missing"));
                    return ItemInteractionResult.SUCCESS;
                }
                case THERMOMETER, MAGNIFYING_GLASS, ELECTROMETER -> {
                    InWorldMessages.send(player, boiler.describe());
                    return ItemInteractionResult.SUCCESS;
                }
                default -> {
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                }
            }
        }
        InWorldMessages.send(player, boiler.describe());
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
        if (!level.isClientSide()
            && level.getBlockEntity(pos) instanceof LargeBoilerBlockEntity boiler) {
            InWorldMessages.send(player, boiler.describe());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void entityInside(
        BlockState state,
        Level level,
        BlockPos pos,
        net.minecraft.world.entity.Entity entity
    ) {
        if (!level.isClientSide()
            && entity instanceof net.minecraft.world.entity.LivingEntity living
            && level.getBlockEntity(pos) instanceof LargeBoilerBlockEntity boiler) {
            boiler.damageTouchingEntity(living);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()
            && level.getBlockEntity(pos) instanceof LargeBoilerBlockEntity boiler
            && boiler.definition() != null) {
            boiler.onBreak(player);
            if (!player.isCreative() && level instanceof ServerLevel serverLevel) {
                Block.popResource(serverLevel, pos, LargeBoilerItem.create(
                    GTItems.LARGE_BOILER.get(),
                    new MachinePart(boiler.definition().line(), boiler.definition().category())
                ));
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
        return (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof LargeBoilerBlockEntity boiler) {
                boiler.tick();
            }
        };
    }
}
