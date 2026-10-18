package gregtech.neo.block;

import gregtech.neo.core.EnergyPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

public final class EnergyNetwork {
    private EnergyNetwork() {
    }

    public record Result(long acceptedAmperes, long deliveredEnergy, int receivers, int burnedCables) {
    }

    private record Hop(BlockPos pos, EnergyPacket packet, Direction enteredFrom) {
    }

    private record ReceiverPath(BlockPos pos, Direction side, long deliveredSize) {
    }

    public static Result distribute(Level level, BlockPos source, EnergyPacket packet) {
        if (packet.kind() != gregtech.neo.core.EnergyKind.ELECTRICITY) {
            return new Result(0, 0, 0, 0);
        }
        Queue<Hop> queue = new ArrayDeque<>();
        Set<BlockPos> visitedWires = new HashSet<>();
        Set<BlockPos> visitedReceivers = new HashSet<>();
        Set<ReceiverPath> receiverPaths = new HashSet<>();
        int burnedCables = 0;

        for (Direction direction : Direction.values()) {
            queue.add(new Hop(source.relative(direction), packet, direction.getOpposite()));
        }

        while (!queue.isEmpty()) {
            Hop hop = queue.remove();
            if (hop.pos().equals(source) || !level.isLoaded(hop.pos())) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(hop.pos());
            if (blockEntity instanceof CableBlockEntity cable) {
                if (!visitedWires.add(hop.pos())) {
                    continue;
                }
                var transmission = cable.spec().transfer(hop.packet());
                cable.recordTransfer(hop.packet(), transmission);
                if (transmission.overloaded()) {
                    cable.burnOut(level);
                    burnedCables++;
                    continue;
                }
                if (transmission.output() == null) {
                    continue;
                }
                for (Direction direction : Direction.values()) {
                    if (direction != hop.enteredFrom()) {
                        queue.add(new Hop(hop.pos().relative(direction), transmission.output(), direction.getOpposite()));
                    }
                }
            } else if (blockEntity instanceof EnergyReceiver receiver && !visitedReceivers.contains(hop.pos())) {
                visitedReceivers.add(hop.pos());
                long probe = receiver.receivePacket(hop.packet().withAmount(1), hop.enteredFrom(), true);
                if (probe > 0) {
                    receiverPaths.add(new ReceiverPath(hop.pos(), hop.enteredFrom(), hop.packet().size()));
                }
            }
        }

        long acceptedAmperes = 0;
        long deliveredEnergy = 0;
        Set<BlockPos> actuallyUsed = new HashSet<>();
        for (long ampere = 0; ampere < packet.amount(); ampere++) {
            boolean acceptedAnywhere = false;
            for (ReceiverPath path : receiverPaths) {
                BlockEntity blockEntity = level.getBlockEntity(path.pos());
                if (!(blockEntity instanceof EnergyReceiver receiver)) {
                    continue;
                }
                EnergyPacket oneAmpere = new EnergyPacket(packet.kind(), path.deliveredSize(), 1);
                if (receiver.receivePacket(oneAmpere, path.side(), false) > 0) {
                    acceptedAmperes++;
                    deliveredEnergy = EnergyPacket.saturatedAdd(deliveredEnergy, oneAmpere.totalEnergy());
                    actuallyUsed.add(path.pos());
                    acceptedAnywhere = true;
                    break;
                }
            }
            if (!acceptedAnywhere) {
                break;
            }
        }

        return new Result(acceptedAmperes, deliveredEnergy, actuallyUsed.size(), burnedCables);
    }
}
