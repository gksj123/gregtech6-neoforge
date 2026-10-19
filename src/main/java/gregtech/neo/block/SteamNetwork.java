package gregtech.neo.block;

import gregtech.neo.fluid.GTFluidUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.Set;

/**
 * GT6 1.7.10 steam is a fluid. The platform keeps its observable movement in
 * fluid capabilities and uses the dedicated Steam Pipe as the current GT fluid
 * pipe adapter.
 */
public final class SteamNetwork {
    private SteamNetwork() {
    }

    public static int distribute(
        Level level,
        BlockPos source,
        Direction outputSide,
        FluidStack stack,
        boolean simulate
    ) {
        if (!GTFluidUtil.isSteam(stack)) {
            return 0;
        }
        IFluidHandler handler = level.getCapability(
            Capabilities.FluidHandler.BLOCK,
            source.relative(outputSide),
            outputSide.getOpposite()
        );
        if (handler == null) {
            return 0;
        }
        return handler.fill(
            stack.copy(),
            simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE
        );
    }

    public static int pushFromPipe(
        Level level,
        BlockPos source,
        FluidStack stack,
        boolean simulate
    ) {
        if (!GTFluidUtil.isSteam(stack) || stack.isEmpty()) {
            return 0;
        }
        Queue<PathNode> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        Set<ReceiverPath> receivers = new LinkedHashSet<>();
        for (Direction direction : Direction.values()) {
            queue.add(new PathNode(source.relative(direction), direction.getOpposite()));
        }

        while (!queue.isEmpty()) {
            PathNode node = queue.remove();
            if (node.pos().equals(source)
                || !visited.add(node.pos())
                || !level.isLoaded(node.pos())) {
                continue;
            }
            if (level.getBlockEntity(node.pos()) instanceof SteamPipeBlockEntity) {
                for (Direction direction : Direction.values()) {
                    queue.add(new PathNode(
                        node.pos().relative(direction),
                        direction.getOpposite()
                    ));
                }
                continue;
            }
            IFluidHandler handler = level.getCapability(
                Capabilities.FluidHandler.BLOCK,
                node.pos(),
                node.enteredFrom()
            );
            if (handler != null) {
                receivers.add(new ReceiverPath(node.pos(), node.enteredFrom(), handler));
            }
        }

        int remaining = stack.getAmount();
        for (ReceiverPath receiver : receivers) {
            if (remaining <= 0) {
                break;
            }
            int accepted = receiver.handler().fill(
                stack.copyWithAmount(remaining),
                simulate
                    ? IFluidHandler.FluidAction.SIMULATE
                    : IFluidHandler.FluidAction.EXECUTE
            );
            remaining -= Math.max(0, accepted);
        }
        return stack.getAmount() - remaining;
    }

    private record PathNode(BlockPos pos, Direction enteredFrom) {
    }

    private record ReceiverPath(BlockPos pos, Direction side, IFluidHandler handler) {
    }
}
