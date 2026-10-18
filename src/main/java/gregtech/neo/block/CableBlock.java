package gregtech.neo.block;

import com.mojang.serialization.MapCodec;
import gregtech.neo.core.WireSpec;
import gregtech.neo.item.GT6ToolItem;
import gregtech.neo.registry.GTBlockEntities;
import gregtech.neo.registry.GTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public final class CableBlock extends BaseEntityBlock {
    public static final MapCodec<CableBlock> CODEC = simpleCodec(CableBlock::new);

    private final WireSpec fallbackSpec;

    private CableBlock(Properties properties) {
        this(properties, new WireSpec("tin", 32, 2, 1));
    }

    private CableBlock(Properties properties, WireSpec fallbackSpec) {
        super(properties);
        this.fallbackSpec = fallbackSpec;
    }

    public static CableBlock tin(Properties properties) {
        return new CableBlock(properties, new WireSpec("tin", 32, 2, 1));
    }

    public static CableBlock copper(Properties properties) {
        return new CableBlock(properties, new WireSpec("copper", 64, 4, 1));
    }

    public WireSpec fallbackSpec() {
        return fallbackSpec;
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.is(GTBlocks.TIN_CABLE.get())) {
            return new CableBlockEntity(pos, state);
        }
        return new CableBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(
        ItemStack stack,
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        net.minecraft.world.InteractionHand hand,
        net.minecraft.world.phys.BlockHitResult hit
    ) {
        if (!(stack.getItem() instanceof GT6ToolItem tool)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof CableBlockEntity cable) {
            switch (tool.kind()) {
                case MAGNIFYING_GLASS, ELECTROMETER -> {
                    InWorldMessages.send(player, Component.translatable(
                        "message.gregtech.cable.status",
                        cable.spec().id(),
                        cable.spec().maximumVoltage(),
                        cable.spec().maximumAmperage(),
                        cable.spec().voltageLossPerBlock(),
                        cable.lastVoltage(),
                        cable.lastAmperage(),
                        cable.overloadCount()
                    ));
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
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        Level level,
        BlockState state,
        BlockEntityType<T> type
    ) {
        return null;
    }
}
