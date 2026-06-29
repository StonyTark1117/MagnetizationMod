package com.stonytark.magnetization.content.fluid;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Insert-only view over a fluid handler: forwards reads and {@link #fill} to the
 * delegate but refuses every {@link #drain}. Machines that <em>consume</em> their
 * fluid (the thrusters: fuel goes in and is burnt internally) expose this as their
 * block capability so a Create pump/pipe can fuel them but cannot siphon the
 * unburnt fuel back out — matching the electrolyzer, whose hand-written handler
 * already makes its water input non-extractable. Internal consumption drains the
 * underlying tank directly, bypassing this wrapper.
 */
public final class InsertOnlyFluidHandler implements IFluidHandler {

    private final IFluidHandler delegate;

    public InsertOnlyFluidHandler(final IFluidHandler delegate) {
        this.delegate = delegate;
    }

    @Override public int getTanks() { return delegate.getTanks(); }
    @Override public FluidStack getFluidInTank(final int tank) { return delegate.getFluidInTank(tank); }
    @Override public int getTankCapacity(final int tank) { return delegate.getTankCapacity(tank); }
    @Override public boolean isFluidValid(final int tank, final FluidStack stack) { return delegate.isFluidValid(tank, stack); }
    @Override public int fill(final FluidStack resource, final FluidAction action) { return delegate.fill(resource, action); }

    @Override public FluidStack drain(final FluidStack resource, final FluidAction action) { return FluidStack.EMPTY; }
    @Override public FluidStack drain(final int maxDrain, final FluidAction action) { return FluidStack.EMPTY; }
}
