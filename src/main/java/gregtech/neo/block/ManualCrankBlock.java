package gregtech.neo.block;

import com.mojang.serialization.MapCodec;
import gregtech.neo.core.EnergyKind;
import gregtech.neo.core.EnergyPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class ManualCrankBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<ManualCrankBlock> CODEC = simpleCodec(ManualCrankBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final EnergyPacket PACKET = new EnergyPacket(EnergyKind.ROTATION, 16, 1);

    public ManualCrankBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<ManualCrankBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
            MechanicalEnergyNetwork.Result result = MechanicalEnergyNetwork.distribute(
                level,
                pos,
                state.getValue(FACING),
                PACKET,
                false
            );
            if (result.acceptedAmperes() > 0) {
                player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                InWorldMessages.send(player, Component.translatable(
                    "message.gregtech.crank.turn",
                    PACKET.size(),
                    PACKET.amount()
                ));
            } else {
                InWorldMessages.send(player, InWorldMessages.status("crank.no_receiver"));
            }
        }
        return InteractionResult.SUCCESS;
    }
}
