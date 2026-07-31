package com.stonytark.magnetization.content.electrolyzer;

import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.menu.MachineGuiData;
import com.stonytark.magnetization.menu.MachineMenu;
import com.stonytark.magnetization.registry.MagBlockEntities;
import com.stonytark.magnetization.registry.MagFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * Electrolyzer — a modified cauldron that electrolyses water into hydrogen using
 * FE. The water level is rendered inside the basin ({@link ElectrolyzerRenderer}).
 * It is the entry point of the fusion-fuel isotope chain. Pipe water in / hydrogen
 * out via the {@code FluidHandler} capability, or use it cauldron-style with buckets.
 */
public class ElectrolyzerBlockEntity extends BlockEntity implements MachineGuiData {

    private final FluidTank waterTank = new FluidTank(MagConfig.electrolyzerWaterTank(), fs -> fs.getFluid() == Fluids.WATER);
    private final FluidTank hydrogenTank = new FluidTank(MagConfig.electrolyzerHydrogenTank(), fs -> fs.getFluid() == MagFluids.HYDROGEN.get());
    private final ReceiveBuffer energy = new ReceiveBuffer(MagConfig.electrolyzerFeCapacity(), MagConfig.electrolyzerFeReceive());
    private final com.stonytark.magnetization.content.MachineSyncGate syncGate = new com.stonytark.magnetization.content.MachineSyncGate();

    /** Bucket-input slot — water buckets auto-drained into the water tank. */
    private final SimpleContainer bucketSlot = new SimpleContainer(1) {
        @Override public boolean canPlaceItem(final int s, final ItemStack st) {
            return st.is(Items.WATER_BUCKET);
        }
        @Override public void setChanged() { super.setChanged(); ElectrolyzerBlockEntity.this.setChanged(); }
    };

    /** Combined fluid handler: fill = water (tank 0), drain = hydrogen (tank 1). */
    private final IFluidHandler fluidIO = new IFluidHandler() {
        @Override public int getTanks() { return 2; }
        @Override public FluidStack getFluidInTank(final int tank) {
            return tank == 0 ? waterTank.getFluid() : hydrogenTank.getFluid();
        }
        @Override public int getTankCapacity(final int tank) {
            return tank == 0 ? waterTank.getCapacity() : hydrogenTank.getCapacity();
        }
        @Override public boolean isFluidValid(final int tank, final FluidStack stack) {
            return tank == 0 ? stack.getFluid() == Fluids.WATER : stack.getFluid() == MagFluids.HYDROGEN.get();
        }
        @Override public int fill(final FluidStack resource, final FluidAction action) {
            return resource.getFluid() == Fluids.WATER ? waterTank.fill(resource, action) : 0;
        }
        @Override public FluidStack drain(final FluidStack resource, final FluidAction action) {
            return resource.getFluid() == MagFluids.HYDROGEN.get() ? hydrogenTank.drain(resource, action) : FluidStack.EMPTY;
        }
        @Override public FluidStack drain(final int maxDrain, final FluidAction action) {
            return hydrogenTank.drain(maxDrain, action);
        }
    };

    public ElectrolyzerBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.ELECTROLYZER.get(), pos, state);
    }

    public IEnergyStorage energyBuffer() { return energy; }
    public IFluidHandler fluidHandler() { return fluidIO; }
    public Container bucketContainer() { return bucketSlot; }

    /** 0..1 water fill fraction for the in-basin renderer. */
    public float waterFillFraction() { return waterTank.getFluidAmount() / (float) Math.max(1, waterTank.getCapacity()); }
    public int waterAmount() { return waterTank.getFluidAmount(); }
    public int hydrogenAmount() { return hydrogenTank.getFluidAmount(); }

    // ── MachineGuiData ──
    @Override public Container guiInput() { return bucketSlot; }
    @Override public MachineMenu.Kind guiKind() { return MachineMenu.Kind.ELECTROLYZER; }
    @Override public int guiEnergyStored() { return energy.getEnergyStored(); }
    @Override public int guiEnergyMax() { return MagConfig.electrolyzerFeCapacity(); }
    @Override public int guiStat1() { return waterTank.getFluidAmount(); }
    @Override public int guiStat2() { return hydrogenTank.getFluidAmount(); }
    @Override public int guiStat4() { return com.stonytark.magnetization.config.MagConfig.electrolyzerHydrogenTank(); }  // bar denominator (server config)
    @Override public com.stonytark.magnetization.menu.MachineDisplayData.Status guiDisplayStatus() {
        return level != null && getBlockState().hasProperty(BlockStateProperties.LIT)
                && getBlockState().getValue(BlockStateProperties.LIT)
                ? com.stonytark.magnetization.menu.MachineDisplayData.Status.ACTIVE
                : com.stonytark.magnetization.menu.MachineDisplayData.Status.IDLE;
    }

    /** Pour one water bucket (1000 mB) into the water tank. */
    public boolean fillWaterFromBucket() {
        final FluidStack water = new FluidStack(Fluids.WATER, 1000);
        if (waterTank.fill(water, IFluidHandler.FluidAction.SIMULATE) < 1000) return false;
        waterTank.fill(water, IFluidHandler.FluidAction.EXECUTE);
        setChanged();
        return true;
    }

    /** Draw one hydrogen bucket (1000 mB) out — for cauldron-style empty-bucket use. */
    public boolean drainHydrogenToBucket() {
        if (hydrogenTank.getFluidAmount() < 1000) return false;
        hydrogenTank.drain(1000, IFluidHandler.FluidAction.EXECUTE);
        setChanged();
        return true;
    }

    public static void serverTick(final Level level, final BlockPos pos, final BlockState state,
                                  final ElectrolyzerBlockEntity be) {
        if (com.stonytark.magnetization.config.MagConfig.isBlockDisabled(state)) return;
        if (!(level instanceof ServerLevel server)) return;
        be.run(server);
    }

    private void run(final ServerLevel server) {
        waterTank.setCapacity(MagConfig.electrolyzerWaterTank());
        hydrogenTank.setCapacity(MagConfig.electrolyzerHydrogenTank());
        energy.resize(MagConfig.electrolyzerFeCapacity(), MagConfig.electrolyzerFeReceive());
        // Auto-drain a water bucket from the slot into the water tank.
        final ItemStack in = bucketSlot.getItem(0);
        if (in.is(Items.WATER_BUCKET) && fillWaterFromBucket()) {
            bucketSlot.setItem(0, new ItemStack(Items.BUCKET));
        }
        final int waterPerTick = MagConfig.electrolyzerWaterPerTick();
        final int fePerTick = MagConfig.electrolyzerFePerTick();
        final int hydrogenPerTick = MagConfig.electrolyzerHydrogenPerTick();
        final boolean running = waterTank.getFluidAmount() >= waterPerTick
                && energy.getEnergyStored() >= fePerTick
                && hydrogenTank.getFluidAmount() + hydrogenPerTick <= hydrogenTank.getCapacity();
        if (running) {
            waterTank.drain(waterPerTick, IFluidHandler.FluidAction.EXECUTE);
            energy.drainInternal(fePerTick);
            hydrogenTank.fill(new FluidStack(MagFluids.HYDROGEN.get(), hydrogenPerTick), IFluidHandler.FluidAction.EXECUTE);
            setChanged();
        }
        final BlockState state = getBlockState();
        if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT) != running) {
            server.setBlock(getBlockPos(), state.setValue(BlockStateProperties.LIT, running), Block.UPDATE_CLIENTS);
        }
        if (server.getGameTime() % 10L == 0L && syncGate.changed(this, server.registryAccess())) {
            server.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.put("Water", waterTank.writeToNBT(registries, new CompoundTag()));
        tag.put("Hydrogen", hydrogenTank.writeToNBT(registries, new CompoundTag()));
        tag.put("Bucket", bucketSlot.createTag(registries));
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setStored(tag.getInt("Energy"));
        waterTank.readFromNBT(registries, tag.getCompound("Water"));
        hydrogenTank.readFromNBT(registries, tag.getCompound("Hydrogen"));
        bucketSlot.fromTag(tag.getList("Bucket", net.minecraft.nbt.Tag.TAG_COMPOUND), registries);
    }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    private static final class ReceiveBuffer extends EnergyStorage {
        ReceiveBuffer(final int capacity, final int maxReceive) { super(capacity, maxReceive, 0); }
        void drainInternal(final int amount) { this.energy = Math.max(0, this.energy - amount); }
        void setStored(final int value) { this.energy = Math.max(0, Math.min(capacity, value)); }
        void resize(final int capacity, final int maxReceive) {
            this.capacity = Math.max(0, capacity);
            this.maxReceive = Math.max(0, maxReceive);
            this.energy = Math.min(this.energy, this.capacity);
        }
    }
}
