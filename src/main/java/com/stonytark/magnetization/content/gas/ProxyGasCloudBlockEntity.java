package com.stonytark.magnetization.content.gas;

import com.stonytark.magnetization.content.fluid.GasExcitation;
import com.stonytark.magnetization.menu.MachineHudData;
import com.stonytark.magnetization.registry.MagBlockEntities;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;

/** Persistent identity-bearing cell in a vented virtual-gas cloud. */
public final class ProxyGasCloudBlockEntity extends BlockEntity implements MachineHudData {
    private Fluid fluid = Fluids.EMPTY;
    private GasExcitationProfile.Buoyancy buoyancy = GasExcitationProfile.Buoyancy.NEUTRAL;
    private int dormantArgb = 0x30FFFFFF;
    private int excitedArgb = 0xFFFFFFFF;
    private Direction drift = Direction.NORTH;
    private BlockPos sourcePos;
    private int density;
    private int grace;

    public ProxyGasCloudBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.PROXY_GAS_CLOUD.get(), pos, state);
        sourcePos = pos.immutable();
    }

    public Fluid fluid() { return fluid; }
    public GasExcitationProfile.Buoyancy buoyancy() { return buoyancy; }
    public Direction driftDirection() { return drift; }
    public BlockPos sourcePos() { return sourcePos; }
    public int density() { return density; }
    public int dormantArgb() { return dormantArgb; }
    public int excitedArgb() { return excitedArgb; }
    public boolean isSource() { return density == 8 && sourcePos.equals(worldPosition); }
    public boolean isExcited() { return getBlockState().getValue(ProxyGasCloudBlock.EXCITED); }
    public int grace() { return grace; }
    public int tint() { return isExcited() ? excitedArgb : dormantArgb; }

    public void configureSource(final Fluid gas, final GasExcitationProfile profile, final Direction ventFacing) {
        fluid = gas;
        buoyancy = profile.buoyancy();
        dormantArgb = profile.dormantArgb();
        excitedArgb = profile.excitedArgb();
        drift = switch (buoyancy) {
            case RISE -> Direction.UP;
            case SINK -> Direction.DOWN;
            case NEUTRAL -> ventFacing;
        };
        sourcePos = worldPosition.immutable();
        density = 8;
        sync();
    }

    private void configureFlow(final ProxyGasCloudBlockEntity parent) {
        fluid = parent.fluid;
        buoyancy = parent.buoyancy;
        dormantArgb = parent.dormantArgb;
        excitedArgb = parent.excitedArgb;
        drift = parent.drift;
        sourcePos = parent.sourcePos;
        density = parent.density - 1;
        grace = parent.grace;
        sync();
    }

    public void setExcitation(final boolean excited, final int newGrace) {
        grace = Math.max(0, Math.min(3, newGrace));
        final BlockState state = getBlockState();
        if (state.getValue(ProxyGasCloudBlock.EXCITED) != excited && level != null) {
            level.setBlock(worldPosition, state.setValue(ProxyGasCloudBlock.EXCITED, excited), Block.UPDATE_CLIENTS);
        }
        sync();
    }

    public static void serverTick(final Level level, final BlockPos pos, final BlockState state,
                                  final ProxyGasCloudBlockEntity cloud) {
        if (!(level instanceof ServerLevel server)) return;
        if (cloud.fluid == Fluids.EMPTY || !cloud.hasLiveSource(server)) {
            server.removeBlock(pos, false);
            return;
        }
        cloud.spread(server);
        // One bounded flood-fill per cloud is enough: the source scan observes
        // redstone beside any flow cell and every supported exciter attachment.
        if (cloud.isSource()) GasExcitation.recompute(server, pos);
    }

    private boolean hasLiveSource(final ServerLevel level) {
        if (isSource()) return true;
        return level.hasChunkAt(sourcePos)
                && level.getBlockEntity(sourcePos) instanceof ProxyGasCloudBlockEntity source
                && source.isSource() && source.fluid == fluid;
    }

    private void spread(final ServerLevel level) {
        if (density <= 1) return;
        final BlockPos next = worldPosition.relative(drift);
        if (!level.hasChunkAt(next)) return;
        if (level.getBlockEntity(next) instanceof ProxyGasCloudBlockEntity existing) {
            if (existing.fluid == fluid && existing.density < density - 1) existing.configureFlow(this);
            return;
        }
        if (!level.getBlockState(next).isAir() && !level.getBlockState(next).canBeReplaced()) return;
        level.setBlock(next, MagBlocks.PROXY_GAS_CLOUD.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(next) instanceof ProxyGasCloudBlockEntity child) child.configureFlow(this);
    }

    public IFluidHandler fluidHandler() {
        return new IFluidHandler() {
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(final int tank) {
                return isSource() ? new FluidStack(fluid, GasVentBlockEntity.CAPACITY) : FluidStack.EMPTY;
            }
            @Override public int getTankCapacity(final int tank) { return GasVentBlockEntity.CAPACITY; }
            @Override public boolean isFluidValid(final int tank, final FluidStack stack) { return false; }
            @Override public int fill(final FluidStack resource, final FluidAction action) { return 0; }
            @Override public FluidStack drain(final FluidStack resource, final FluidAction action) {
                if (!isSource() || resource.isEmpty()
                        || !FluidStack.isSameFluidSameComponents(resource, new FluidStack(fluid, 1))
                        || resource.getAmount() < GasVentBlockEntity.CAPACITY) return FluidStack.EMPTY;
                return drain(GasVentBlockEntity.CAPACITY, action);
            }
            @Override public FluidStack drain(final int maxDrain, final FluidAction action) {
                if (!isSource() || maxDrain < GasVentBlockEntity.CAPACITY) return FluidStack.EMPTY;
                final FluidStack result = new FluidStack(fluid, GasVentBlockEntity.CAPACITY);
                if (action.execute() && level != null) level.removeBlock(worldPosition, false);
                return result;
            }
        };
    }

    @Override public List<Component> hudLines() {
        final Component gas = fluid == Fluids.EMPTY ? Component.translatable("tooltip.magnetization.gas_cloud.unknown")
                : new FluidStack(fluid, 1).getHoverName();
        final Component status = Component.translatable(isExcited()
                        ? "tooltip.magnetization.gas_cloud.excited" : "tooltip.magnetization.gas_cloud.dormant")
                .withStyle(isExcited() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY);
        return List.of(gas.copy().withStyle(ChatFormatting.AQUA), status);
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Fluid", BuiltInRegistries.FLUID.getKey(fluid).toString());
        tag.putString("Buoyancy", buoyancy.name());
        tag.putInt("DormantArgb", dormantArgb);
        tag.putInt("ExcitedArgb", excitedArgb);
        tag.putInt("Drift", drift.get3DDataValue());
        tag.putLong("Source", sourcePos.asLong());
        tag.putInt("Density", density);
        tag.putInt("Grace", grace);
    }

    @Override protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Fluid")) fluid = BuiltInRegistries.FLUID.get(ResourceLocation.parse(tag.getString("Fluid")));
        if (tag.contains("Buoyancy")) {
            try { buoyancy = GasExcitationProfile.Buoyancy.valueOf(tag.getString("Buoyancy")); }
            catch (final IllegalArgumentException ignored) { buoyancy = GasExcitationProfile.Buoyancy.NEUTRAL; }
        }
        dormantArgb = tag.getInt("DormantArgb");
        excitedArgb = tag.getInt("ExcitedArgb");
        drift = Direction.from3DDataValue(tag.getInt("Drift"));
        sourcePos = BlockPos.of(tag.getLong("Source"));
        density = tag.getInt("Density");
        grace = tag.getInt("Grace");
    }

    @Override public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
