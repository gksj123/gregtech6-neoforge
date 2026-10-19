package gregtech.neo.fluid;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public final class FluidHandlerView implements IFluidHandler {
    private final IFluidHandler delegate;
    private final boolean fillable;
    private final boolean drainable;

    public FluidHandlerView(IFluidHandler delegate, boolean fillable, boolean drainable) {
        this.delegate = delegate;
        this.fillable = fillable;
        this.drainable = drainable;
    }

    @Override
    public int getTanks() {
        return delegate.getTanks();
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return delegate.getFluidInTank(tank);
    }

    @Override
    public int getTankCapacity(int tank) {
        return delegate.getTankCapacity(tank);
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return delegate.isFluidValid(tank, stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return fillable ? delegate.fill(resource, action) : 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return drainable ? delegate.drain(resource, action) : FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return drainable ? delegate.drain(maxDrain, action) : FluidStack.EMPTY;
    }
}
