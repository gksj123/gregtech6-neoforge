package gregtech.neo.block;

import com.mojang.serialization.MapCodec;
import gregtech.neo.item.BurningBoxItem;
import gregtech.neo.item.GT6ToolItem;
import gregtech.neo.registry.GTBlockEntities;
import gregtech.neo.registry.GTItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BurningBoxBlock extends BaseEntityBlock {
    public static final MapCodec<BurningBoxBlock> CODEC = simpleCodec(BurningBoxBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape COLLISION_SHAPE = box(0, 0, 0, 16, 14, 16);

    public BurningBoxBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(LIT, false);
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
        return COLLISION_SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BurningBoxBlockEntity(pos, state);
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
        if (!(level.getBlockEntity(pos) instanceof BurningBoxBlockEntity box)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.is(Items.WATER_BUCKET)) {
            box.extinguish();
            if (!player.isCreative()) {
                player.setItemInHand(hand, new ItemStack(Items.BUCKET));
            }
            InWorldMessages.send(player, box.describe());
            return ItemInteractionResult.SUCCESS;
        }
        if (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE)) {
            if (hit.getDirection() == state.getValue(FACING) && box.ignite()) {
                if (stack.is(Items.FIRE_CHARGE) && !player.isCreative()) {
                    stack.shrink(1);
                }
                player.setRemainingFireTicks(20);
            }
            InWorldMessages.send(player, box.describe());
            return ItemInteractionResult.SUCCESS;
        }
        if (stack.getItem() instanceof GT6ToolItem tool) {
            switch (tool.kind()) {
                case SCREWDRIVER -> {
                    box.toggleEnabled();
                    InWorldMessages.send(player, box.describe());
                    return ItemInteractionResult.SUCCESS;
                }
                case SOFT_HAMMER, MAGNIFYING_GLASS, ELECTROMETER -> {
                    InWorldMessages.send(player, box.describe());
                    return ItemInteractionResult.SUCCESS;
                }
                default -> {
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                }
            }
        }
        int before = stack.getCount();
        ItemStack remainder = hit.getDirection() == state.getValue(FACING)
            ? box.insertFuel(stack)
            : stack;
        if (remainder.getCount() != before) {
            player.setItemInHand(hand, remainder);
        }
        InWorldMessages.send(player, box.describe());
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
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BurningBoxBlockEntity box) {
            ItemStack output = box.extractOutput();
            if (!output.isEmpty()) {
                player.getInventory().placeItemBackInInventory(output);
            } else {
                InWorldMessages.send(player, box.describe());
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.56
            + (facing.getAxis() == Direction.Axis.Z ? random.nextDouble() * 0.5 - 0.25 : 0);
        double y = pos.getY() + 0.2 + random.nextDouble() * 0.35;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.56
            + (facing.getAxis() == Direction.Axis.X ? random.nextDouble() * 0.5 - 0.25 : 0);
        level.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0.01, 0);
        if (random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.FLAME, x, y, z, 0, 0.01, 0);
        }
        if (random.nextInt(40) == 0) {
            level.addParticle(ParticleTypes.LAVA, x, y, z, 0, 0, 0);
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (state.getValue(LIT) && !level.isClientSide()) {
            entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), 60));
            entity.hurt(level.damageSources().onFire(), 1.0F);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.isCreative()
            && level.getBlockEntity(pos) instanceof BurningBoxBlockEntity box
            && box.definition() != null && level instanceof ServerLevel serverLevel) {
            Block.popResource(serverLevel, pos, BurningBoxItem.create(
                GTItems.BURNING_BOX.get(),
                new gregtech.neo.machine.MachinePart(
                    box.definition().line(),
                    box.definition().category()
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
            if (blockEntity instanceof BurningBoxBlockEntity box) {
                box.tick();
            }
        };
    }
}
