package gregtech.neo.block;

import gregtech.neo.core.EnergyKind;
import gregtech.neo.core.EnergyPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * RU travels through axles. KU is a push/pull packet and is delivered only to
 * the directly facing block. Neither type can enter an electric cable.
 */
public final class MechanicalEnergyNetwork {
    private MechanicalEnergyNetwork() {
    }

    public record Result(long acceptedAmperes, long deliveredEnergy, int receivers) {
        public static final Result NONE = new Result(0, 0, 0);
    }

    public static Result distribute(
        Level level,
        BlockPos source,
        Direction outputSide,
        EnergyPacket packet,
        boolean simulate
    ) {
        if (packet.kind() == EnergyKind.KINETIC) {
            return deliverDirect(level, source.relative(outputSide), outputSide.getOpposite(), packet, simulate);
        }
        if (packet.kind() != EnergyKind.ROTATION) {
            return Result.NONE;
        }

        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visitedAxles = new HashSet<>();
        Set<BlockPos> receiverPositions = new HashSet<>();
        Set<ReceiverPath> receivers = new HashSet<>();
        queue.add(source.relative(outputSide));

        while (!queue.isEmpty()) {
            BlockPos pos = queue.remove();
            if (pos.equals(source) || !level.isLoaded(pos) || !visitedAxles.add(pos)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof AxleBlockEntity) {
                for (Direction direction : Direction.values()) {
                    queue.add(pos.relative(direction));
                }
            } else if (blockEntity instanceof EnergyReceiver receiver && receiverPositions.add(pos)) {
                if (receiver.receivePacket(packet.withAmount(1), Direction.UP, true) > 0) {
                    receivers.add(new ReceiverPath(pos, receiver));
                }
            }
        }

        long accepted = 0;
        long deliveredEnergy = 0;
        Set<BlockPos> used = new HashSet<>();
        for (long ampere = 0; ampere < packet.amount(); ampere++) {
            boolean acceptedAnywhere = false;
            for (ReceiverPath path : receivers) {
                long response = path.receiver().receivePacket(packet.withAmount(1), Direction.UP, simulate);
                if (response > 0) {
                    accepted++;
                    deliveredEnergy = EnergyPacket.saturatedAdd(deliveredEnergy, packet.size());
                    used.add(path.pos());
                    acceptedAnywhere = true;
                    break;
                }
            }
            if (!acceptedAnywhere) {
                break;
            }
        }
        return new Result(accepted, deliveredEnergy, used.size());
    }

    private static Result deliverDirect(
        Level level,
        BlockPos target,
        Direction side,
        EnergyPacket packet,
        boolean simulate
    ) {
        if (!level.isLoaded(target) || !(level.getBlockEntity(target) instanceof EnergyReceiver receiver)) {
            return Result.NONE;
        }
        long accepted = 0;
        long delivered = 0;
        for (long ampere = 0; ampere < packet.amount(); ampere++) {
            long response = receiver.receivePacket(packet.withAmount(1), side, simulate);
            if (response <= 0) {
                break;
            }
            accepted++;
            delivered += packet.size();
        }
        return new Result(accepted, delivered, accepted > 0 ? 1 : 0);
    }

    private record ReceiverPath(BlockPos pos, EnergyReceiver receiver) {
    }
}
