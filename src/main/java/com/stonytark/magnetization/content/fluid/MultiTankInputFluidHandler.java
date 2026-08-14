package com.stonytark.magnetization.content.fluid;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;

/**
 * Insert-only aggregate over several independent input tanks. Tank indices are
 * exposed in delegate order and fills are routed to the first delegate that
 * accepts the supplied fluid. This lets one pipe connection feed, for example,
 * a fusion-fuel tank and a separate water-coolant tank without allowing either
 * consumable to be siphoned back out.
 */
public final class MultiTankInputFluidHandler implements IFluidHandler {
    private final List<IFluidHandler> delegates;

    public MultiTankInputFluidHandler(final IFluidHandler... delegates) {
        this.delegates = List.of(delegates);
    }

    @Override
    public int getTanks() {
        int count = 0;
        for (final IFluidHandler delegate : delegates) count += delegate.getTanks();
        return count;
    }

    @Override
    public FluidStack getFluidInTank(final int tank) {
        final TankRef ref = resolve(tank);
        return ref == null ? FluidStack.EMPTY : ref.handler().getFluidInTank(ref.localTank());
    }

    @Override
    public int getTankCapacity(final int tank) {
        final TankRef ref = resolve(tank);
        return ref == null ? 0 : ref.handler().getTankCapacity(ref.localTank());
    }

    @Override
    public boolean isFluidValid(final int tank, final FluidStack stack) {
        final TankRef ref = resolve(tank);
        return ref != null && ref.handler().isFluidValid(ref.localTank(), stack);
    }

    @Override
    public int fill(final FluidStack resource, final FluidAction action) {
        if (resource.isEmpty()) return 0;
        for (final IFluidHandler delegate : delegates) {
            for (int tank = 0; tank < delegate.getTanks(); tank++) {
                if (delegate.isFluidValid(tank, resource)) return delegate.fill(resource, action);
            }
        }
        return 0;
    }

    @Override
    public FluidStack drain(final FluidStack resource, final FluidAction action) {
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(final int maxDrain, final FluidAction action) {
        return FluidStack.EMPTY;
    }

    private TankRef resolve(final int globalTank) {
        if (globalTank < 0) return null;
        int offset = globalTank;
        for (final IFluidHandler delegate : delegates) {
            if (offset < delegate.getTanks()) return new TankRef(delegate, offset);
            offset -= delegate.getTanks();
        }
        return null;
    }

    private record TankRef(IFluidHandler handler, int localTank) {}
}
