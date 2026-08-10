package com.stonytark.magnetization.content.gas;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.registry.MagBlockEntities;
import com.stonytark.magnetization.registry.MagFluids;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.Arrays;

/** Simultaneously condenses five atmospheric noble-gas fractions. */
public final class AirSeparatorBlockEntity extends KineticBlockEntity {
    public static final int HELIUM = 0, NEON = 1, ARGON = 2, KRYPTON = 3, XENON = 4, COUNT = 5;
    private final FluidTank[] tanks = new FluidTank[COUNT];
    private final long[] progressMilli = new long[COUNT];
    private final Direction[] outputs = new Direction[COUNT];
    private Direction lastFacing;
    private long isotopeWorkMilli;
    private final SimpleContainer upgrade = new SimpleContainer(1) {
        @Override public boolean canPlaceItem(final int slot, final ItemStack stack) {
            return stack.is(MagItems.ISOTOPE_SEPARATION_MODULE.get());
        }
        @Override public int getMaxStackSize() { return 1; }
        @Override public void setChanged() { super.setChanged(); AirSeparatorBlockEntity.this.setChanged(); }
    };
    private final SimpleContainer crystalOutput = new SimpleContainer(1) {
        @Override public boolean canPlaceItem(final int slot, final ItemStack stack) { return false; }
        @Override public void setChanged() { super.setChanged(); AirSeparatorBlockEntity.this.setChanged(); }
    };

    public AirSeparatorBlockEntity(final net.minecraft.world.level.block.entity.BlockEntityType<?> type,
                                   final BlockPos pos, final BlockState state) {
        super(type, pos, state);
        final Fluid[] fluids = gases();
        for (int i = 0; i < COUNT; i++) {
            final int index = i;
            tanks[i] = new FluidTank(MagConfig.airSeparatorTankCapacity(), fs -> fs.getFluid() == fluids[index]) {
                @Override protected void onContentsChanged() { AirSeparatorBlockEntity.this.setChanged(); }
            };
        }
        resetDefaultOutputs(state);
    }

    public AirSeparatorBlockEntity(final BlockPos pos, final BlockState state) {
        this(MagBlockEntities.AIR_SEPARATOR.get(), pos, state);
    }

    private static Fluid[] gases() {
        return new Fluid[]{MagFluids.HELIUM.get(), MagFluids.NEON.get(), MagFluids.ARGON.get(),
                MagFluids.KRYPTON.get(), MagFluids.XENON.get()};
    }

    public static String gasTranslationKey(final int gas) {
        return "fluid_type.magnetization." + switch (gas) {
            case HELIUM -> "helium"; case NEON -> "neon"; case ARGON -> "argon";
            case KRYPTON -> "krypton"; case XENON -> "xenon"; default -> "argon";
        };
    }

    public FluidTank tank(final int index) { return tanks[index]; }
    public SimpleContainer upgradeContainer() { return upgrade; }
    public SimpleContainer crystalOutputContainer() { return crystalOutput; }
    public net.neoforged.neoforge.items.IItemHandler itemHandler() { return automationItems; }

    private final net.neoforged.neoforge.items.IItemHandler automationItems = new net.neoforged.neoforge.items.IItemHandler() {
        @Override public int getSlots() { return 2; }
        @Override public ItemStack getStackInSlot(final int slot) {
            return slot == 0 ? upgrade.getItem(0) : slot == 1 ? crystalOutput.getItem(0) : ItemStack.EMPTY;
        }
        @Override public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate) {
            if (slot != 0 || !stack.is(MagItems.ISOTOPE_SEPARATION_MODULE.get()) || !upgrade.getItem(0).isEmpty()) return stack;
            if (!simulate) upgrade.setItem(0, stack.copyWithCount(1));
            final ItemStack remainder = stack.copy();
            remainder.shrink(1);
            return remainder;
        }
        @Override public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
            if (slot != 1 || amount <= 0 || crystalOutput.getItem(0).isEmpty()) return ItemStack.EMPTY;
            final ItemStack result = crystalOutput.getItem(0).copyWithCount(Math.min(amount, crystalOutput.getItem(0).getCount()));
            if (!simulate) crystalOutput.removeItem(0, result.getCount());
            return result;
        }
        @Override public int getSlotLimit(final int slot) { return slot == 0 ? 1 : 64; }
        @Override public boolean isItemValid(final int slot, final ItemStack stack) {
            return slot == 0 && stack.is(MagItems.ISOTOPE_SEPARATION_MODULE.get());
        }
    };

    public boolean installUpgrade() {
        if (!upgrade.getItem(0).isEmpty()) return false;
        upgrade.setItem(0, new ItemStack(MagItems.ISOTOPE_SEPARATION_MODULE.get()));
        notifyUpdate();
        return true;
    }

    public ItemStack takeCrystal() {
        final ItemStack result = crystalOutput.removeItemNoUpdate(0);
        if (!result.isEmpty()) notifyUpdate();
        return result;
    }

    public ItemStack drainBucket(final Direction side) {
        final int gas = gasForFace(side);
        if (gas < 0 || tanks[gas].getFluidAmount() < 1000) return ItemStack.EMPTY;
        tanks[gas].drain(1000, IFluidHandler.FluidAction.EXECUTE);
        final ItemStack result = new ItemStack(switch (gas) {
            case HELIUM -> MagItems.HELIUM_BUCKET.get();
            case NEON -> MagItems.NEON_BUCKET.get();
            case ARGON -> MagItems.ARGON_BUCKET.get();
            case KRYPTON -> MagItems.KRYPTON_BUCKET.get();
            case XENON -> MagItems.XENON_BUCKET.get();
            default -> Items.BUCKET;
        });
        notifyUpdate();
        return result;
    }

    /** Drain-only, face-isolated capability. */
    public IFluidHandler fluidHandler(final Direction side) {
        final int gas = gasForFace(side);
        if (gas < 0) return null;
        return new IFluidHandler() {
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(final int tank) { return tanks[gas].getFluid(); }
            @Override public int getTankCapacity(final int tank) { return tanks[gas].getCapacity(); }
            @Override public boolean isFluidValid(final int tank, final FluidStack stack) { return false; }
            @Override public int fill(final FluidStack resource, final FluidAction action) { return 0; }
            @Override public FluidStack drain(final FluidStack resource, final FluidAction action) {
                final FluidStack drained = tanks[gas].drain(resource, action);
                if (action.execute() && !drained.isEmpty()) notifyUpdate();
                return drained;
            }
            @Override public FluidStack drain(final int maxDrain, final FluidAction action) {
                final FluidStack drained = tanks[gas].drain(maxDrain, action);
                if (action.execute() && !drained.isEmpty()) notifyUpdate();
                return drained;
            }
        };
    }

    public int cycleFace(final Direction face, final int delta) {
        syncFacing();
        final Direction shaft = getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
        if (face == shaft) return -1;
        final int current = gasForFace(face);
        if (current < 0) return -1;
        final int target = Math.floorMod(current + delta, COUNT);
        final Direction targetFace = outputs[target];
        outputs[target] = face;
        outputs[current] = targetFace;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return target;
    }

    public int gasForFace(final Direction face) {
        syncFacing();
        for (int i = 0; i < COUNT; i++) if (outputs[i] == face) return i;
        return -1;
    }

    @Override public float calculateStressApplied() {
        final float stress = MagConfig.airSeparatorStress()
                + (hasUpgrade() ? MagConfig.airSeparatorUpgradeStress() : 0f);
        lastStressApplied = stress;
        return stress;
    }

    @Override public void tick() {
        super.tick();
        syncFacing();
        if (level == null || level.isClientSide || !MagConfig.airSeparatorAllowedIn(level)) return;
        final float rpm = Math.abs(getSpeed());
        final int minRpm = MagConfig.airSeparatorMinRpm();
        if (rpm < minRpm) return;
        final float effective = Math.min(rpm, MagConfig.airSeparatorMaxRpm());
        final Fluid[] fluids = gases();
        boolean changed = false;
        for (int i = 0; i < COUNT; i++) {
            tanks[i].setCapacity(MagConfig.airSeparatorTankCapacity());
            if (tanks[i].getFluidAmount() >= tanks[i].getCapacity()) continue;
            progressMilli[i] += Math.round(MagConfig.airSeparatorRateMilli(i) * effective / minRpm);
            final int available = (int) Math.min(Integer.MAX_VALUE, progressMilli[i] / 1000L);
            if (available <= 0) continue;
            final int accepted = tanks[i].fill(new FluidStack(fluids[i], available), IFluidHandler.FluidAction.EXECUTE);
            progressMilli[i] -= accepted * 1000L;
            changed |= accepted > 0;
        }
        if (hasUpgrade() && canOutputCrystal()) {
            isotopeWorkMilli += Math.round(effective * 1000.0f / minRpm);
            final long required = (long) MagConfig.airSeparatorHelium3Work() * 1000L;
            if (isotopeWorkMilli >= required) {
                isotopeWorkMilli -= required;
                final ItemStack existing = crystalOutput.getItem(0);
                if (existing.isEmpty()) crystalOutput.setItem(0, new ItemStack(MagItems.HELIUM_3_CRYSTAL.get()));
                else existing.grow(1);
                changed = true;
            }
        }
        if (changed) {
            setChanged();
            if (level.getGameTime() % 10L == 0L) sendData();
        }
    }

    private boolean hasUpgrade() { return upgrade.getItem(0).is(MagItems.ISOTOPE_SEPARATION_MODULE.get()); }
    private boolean canOutputCrystal() {
        final ItemStack out = crystalOutput.getItem(0);
        return out.isEmpty() || out.is(MagItems.HELIUM_3_CRYSTAL.get()) && out.getCount() < out.getMaxStackSize();
    }

    private void resetDefaultOutputs(final BlockState state) {
        final Direction front = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
        outputs[HELIUM] = Direction.UP;
        outputs[NEON] = front.getCounterClockWise();
        outputs[ARGON] = Direction.DOWN;
        outputs[KRYPTON] = front.getClockWise();
        outputs[XENON] = front;
        lastFacing = front;
    }

    /** Keep persisted port assignments attached to the same physical faces when
     * a wrench, structure transform, or contraption placement rotates the block. */
    private void syncFacing() {
        final Direction current = getBlockState().hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
        if (lastFacing == null) {
            lastFacing = current;
            return;
        }
        int turns = 0;
        Direction cursor = lastFacing;
        while (cursor != current && turns < 4) {
            cursor = cursor.getClockWise();
            turns++;
        }
        if (turns >= 4) {
            resetDefaultOutputs(getBlockState());
            return;
        }
        for (int turn = 0; turn < turns; turn++) {
            for (int i = 0; i < COUNT; i++) if (outputs[i] != null && outputs[i].getAxis().isHorizontal()) {
                outputs[i] = outputs[i].getClockWise();
            }
        }
        if (turns > 0) setChanged();
        lastFacing = current;
    }

    @Override protected void write(final CompoundTag tag, final HolderLookup.Provider registries,
                                   final boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        for (int i = 0; i < COUNT; i++) {
            tag.put("Tank" + i, tanks[i].writeToNBT(registries, new CompoundTag()));
            tag.putLong("Progress" + i, progressMilli[i]);
            tag.putInt("Output" + i, outputs[i].get3DDataValue());
        }
        tag.putLong("IsotopeWork", isotopeWorkMilli);
        tag.putInt("LastFacing", (lastFacing == null ? Direction.NORTH : lastFacing).get3DDataValue());
        tag.put("Upgrade", upgrade.createTag(registries));
        tag.put("CrystalOutput", crystalOutput.createTag(registries));
    }

    @Override protected void read(final CompoundTag tag, final HolderLookup.Provider registries,
                                  final boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        for (int i = 0; i < COUNT; i++) {
            if (tag.contains("Tank" + i)) tanks[i].readFromNBT(registries, tag.getCompound("Tank" + i));
            progressMilli[i] = tag.getLong("Progress" + i);
            if (tag.contains("Output" + i)) outputs[i] = Direction.from3DDataValue(tag.getInt("Output" + i));
        }
        isotopeWorkMilli = tag.getLong("IsotopeWork");
        lastFacing = tag.contains("LastFacing") ? Direction.from3DDataValue(tag.getInt("LastFacing"))
                : getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        upgrade.fromTag(tag.getList("Upgrade", net.minecraft.nbt.Tag.TAG_COMPOUND), registries);
        crystalOutput.fromTag(tag.getList("CrystalOutput", net.minecraft.nbt.Tag.TAG_COMPOUND), registries);
        if (Arrays.stream(outputs).anyMatch(java.util.Objects::isNull)) resetDefaultOutputs(getBlockState());
    }
}
