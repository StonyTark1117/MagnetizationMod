package com.stonytark.magnetization.content.gas;

import com.stonytark.magnetization.menu.MachineHudData;
import com.stonytark.magnetization.registry.MagBlockEntities;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.List;

/** One-bucket input buffer and source-cloud controller for the Gas Vent. */
public final class GasVentBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity
        implements MachineHudData {
    public static final int CAPACITY = 1000;
    private final FluidTank tank = new FluidTank(CAPACITY,
            stack -> GasExcitationProfiles.supports(stack.getFluid())) {
        @Override protected void onContentsChanged() {
            GasVentBlockEntity.this.setChanged();
            if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    };
    private final IFluidHandler insertOnly = new IFluidHandler() {
        @Override public int getTanks() { return tank.getTanks(); }
        @Override public FluidStack getFluidInTank(final int tankIndex) { return tank.getFluidInTank(tankIndex); }
        @Override public int getTankCapacity(final int tankIndex) { return tank.getTankCapacity(tankIndex); }
        @Override public boolean isFluidValid(final int tankIndex, final FluidStack stack) {
            return tank.isFluidValid(tankIndex, stack);
        }
        @Override public int fill(final FluidStack resource, final FluidAction action) {
            return tank.fill(resource, action);
        }
        @Override public FluidStack drain(final FluidStack resource, final FluidAction action) {
            return FluidStack.EMPTY;
        }
        @Override public FluidStack drain(final int maxDrain, final FluidAction action) {
            return FluidStack.EMPTY;
        }
    };

    public GasVentBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.GAS_VENT.get(), pos, state);
    }

    /** Automation-facing insertion endpoint; vent contents cannot be extracted. */
    public IFluidHandler fluidHandler() { return insertOnly; }
    public BlockPos outputPos() { return worldPosition.relative(outputDirection()); }
    public Direction outputDirection() {
        return getBlockState().hasProperty(BlockStateProperties.FACING)
                ? getBlockState().getValue(BlockStateProperties.FACING) : Direction.UP;
    }
    public BlockPos attachedExciterPos() { return worldPosition.relative(outputDirection().getOpposite()); }

    public static void serverTick(final Level level, final BlockPos pos, final BlockState state,
                                  final GasVentBlockEntity vent) {
        if (!(level instanceof ServerLevel server) || vent.tank.getFluidAmount() < CAPACITY) return;
        final FluidStack contents = vent.tank.getFluid();
        final GasExcitationProfile profile = GasExcitationProfiles.find(contents.getFluid()).orElse(null);
        if (profile == null) return;
        final BlockPos output = vent.outputPos();
        if (!server.hasChunkAt(output)) return;
        if (server.getBlockEntity(output) instanceof ProxyGasCloudBlockEntity cloud && cloud.isSource()) return;
        final BlockState target = server.getBlockState(output);
        if (!target.isAir() && !target.canBeReplaced()) return;
        server.setBlock(output, MagBlocks.PROXY_GAS_CLOUD.get().defaultBlockState(), Block.UPDATE_ALL);
        if (server.getBlockEntity(output) instanceof ProxyGasCloudBlockEntity cloud) {
            cloud.configureSource(contents.getFluid(), profile, vent.outputDirection());
            vent.tank.drain(CAPACITY, IFluidHandler.FluidAction.EXECUTE);
        }
    }

    @Override public List<Component> hudLines() {
        final FluidStack contents = tank.getFluid();
        final Component gas = contents.isEmpty()
                ? Component.translatable("tooltip.magnetization.gas_vent.empty")
                : Component.translatable("tooltip.magnetization.gas_vent.gas", contents.getHoverName());
        final Component amount = Component.translatable("tooltip.magnetization.gas_vent.amount",
                tank.getFluidAmount(), CAPACITY);
        final boolean cloudPresent = level != null
                && level.getBlockEntity(outputPos()) instanceof ProxyGasCloudBlockEntity;
        final boolean clear = level != null && !cloudPresent && (level.getBlockState(outputPos()).isAir()
                || level.getBlockState(outputPos()).canBeReplaced());
        final Component output = Component.translatable(cloudPresent
                        ? "tooltip.magnetization.gas_vent.output_cloud"
                        : clear ? "tooltip.magnetization.gas_vent.output_clear"
                        : "tooltip.magnetization.gas_vent.output_blocked")
                .withStyle(cloudPresent || clear ? ChatFormatting.GREEN : ChatFormatting.RED);
        final Component exciter = attachedExciterLine();
        return List.of(gas.copy().withStyle(ChatFormatting.AQUA), amount, output, exciter);
    }

    private Component attachedExciterLine() {
        if (level == null || !(level.getBlockEntity(attachedExciterPos()) instanceof GasExciterBlockEntity machine)) {
            return Component.translatable("tooltip.magnetization.gas_vent.exciter_missing")
                    .withStyle(ChatFormatting.GRAY);
        }
        if (level.hasNeighborSignal(attachedExciterPos())) {
            return Component.translatable("tooltip.magnetization.gas_vent.exciter_redstone")
                    .withStyle(ChatFormatting.RED);
        }
        return Component.translatable(machine.canExcite()
                        ? "tooltip.magnetization.gas_vent.exciter_ready"
                        : "tooltip.magnetization.gas_vent.exciter_unpowered")
                .withStyle(machine.canExcite() ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
    }

    @Override protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
    }

    @Override protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Tank")) tank.readFromNBT(registries, tag.getCompound("Tank"));
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
