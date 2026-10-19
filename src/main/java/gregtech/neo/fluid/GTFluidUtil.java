package gregtech.neo.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;

public final class GTFluidUtil {
    private GTFluidUtil() {
    }

    public static boolean isWater(FluidStack stack) {
        return !stack.isEmpty()
            && (stack.is(net.minecraft.world.level.material.Fluids.WATER)
                || stack.is(GTFluids.DISTILLED_WATER.get()));
    }

    public static boolean isDistilledWater(FluidStack stack) {
        return !stack.isEmpty() && stack.is(GTFluids.DISTILLED_WATER.get());
    }

    public static boolean isSteam(FluidStack stack) {
        return !stack.isEmpty() && stack.is(GTFluids.STEAM.get());
    }

    public static FluidStack steam(int amount) {
        return amount <= 0
            ? FluidStack.EMPTY
            : new FluidStack(GTFluids.STEAM.get(), amount);
    }

    public static FluidStack distilledWater(int amount) {
        return amount <= 0
            ? FluidStack.EMPTY
            : new FluidStack(GTFluids.DISTILLED_WATER.get(), amount);
    }

    public static int fillAdjacent(
        Level level,
        BlockPos pos,
        Direction side,
        FluidStack stack,
        boolean simulate
    ) {
        if (stack.isEmpty()) {
            return 0;
        }
        IFluidHandler handler = level.getCapability(
            Capabilities.FluidHandler.BLOCK,
            pos.relative(side),
            side.getOpposite()
        );
        if (handler == null) {
            return 0;
        }
        return handler.fill(
            stack.copy(),
            simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE
        );
    }

    public static int fillFirst(
        Level level,
        BlockPos pos,
        FluidStack stack,
        List<Direction> sides,
        boolean simulate
    ) {
        for (Direction side : sides) {
            int filled = fillAdjacent(level, pos, side, stack, simulate);
            if (filled > 0) {
                return filled;
            }
        }
        return 0;
    }

    public static int fillAll(
        Level level,
        BlockPos pos,
        FluidStack stack,
        List<Direction> sides,
        boolean simulate
    ) {
        int remaining = stack.getAmount();
        for (Direction side : sides) {
            if (remaining <= 0) {
                break;
            }
            int filled = fillAdjacent(
                level,
                pos,
                side,
                stack.copyWithAmount(remaining),
                simulate
            );
            remaining -= Math.max(0, filled);
        }
        return stack.getAmount() - remaining;
    }
}
