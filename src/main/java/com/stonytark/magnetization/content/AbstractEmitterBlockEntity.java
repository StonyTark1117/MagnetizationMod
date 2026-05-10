package com.stonytark.magnetization.content;

import com.stonytark.magnetization.api.FieldTooltipFormatter;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticFieldSource;
import com.stonytark.magnetization.content.inverter.PolarityInverterBlock;
import com.stonytark.magnetization.physics.EmitterRegistry;
import com.stonytark.magnetization.physics.FieldApplicator;
import com.stonytark.magnetization.physics.InventorySink;
import com.stonytark.magnetization.physics.SableBridge;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.api.equipment.goggles.IHaveHoveringInformation;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * Common base for emitter block entities. Subclasses describe their current field
 * each tick via {@link #computeField(BlockState)}; the base handles serializing
 * the powered/active flags, exposing them, and dispatching to {@link FieldApplicator}.
 */
public abstract class AbstractEmitterBlockEntity extends BlockEntity
        implements MagneticFieldSource, BlockEntitySubLevelActor,
        IHaveGoggleInformation, IHaveHoveringInformation {

    private boolean powered = false;
    @Nullable MagneticField cachedField = null;

    protected AbstractEmitterBlockEntity(
            final BlockEntityType<?> type,
            final BlockPos pos,
            final BlockState state
    ) {
        super(type, pos, state);
    }

    public boolean isPowered() {
        return powered;
    }

    public void setPowered(final boolean powered) {
        if (this.powered == powered) return;
        this.powered = powered;
        this.cachedField = null;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.playSound(null, getBlockPos(),
                    powered ? SoundEvents.LODESTONE_PLACE : SoundEvents.LODESTONE_BREAK,
                    SoundSource.BLOCKS, 0.4f, powered ? 1.6f : 1.2f);
        }
    }

    /**
     * Subclasses describe the current field; return {@code null} when off.
     * Called every tick — keep cheap.
     */
    protected abstract @Nullable MagneticField computeField(BlockState state);

    /**
     * Subclass hook to restrict which ships the field acts on. Return {@code null}
     * (default) to apply to every sub-level in range; return a predicate to filter.
     * Used by {@link com.stonytark.magnetization.content.anchor.MagneticAnchorBlockEntity} to
     * stick to its bound target.
     */
    protected @Nullable Predicate<ServerSubLevel> shipFilter() {
        return null;
    }

    @Override
    public final @Nullable MagneticField currentField() {
        return cachedField;
    }

    public static <T extends AbstractEmitterBlockEntity> void serverTick(
            final Level level, final BlockPos pos, final BlockState state, final T be
    ) {
        if (!(level instanceof ServerLevel server)) return;
        // Defensive: if this BE happens to be inside a contraption but is still
        // being driven by the vanilla ticker (Sable semantics may evolve), look up
        // the host so we promote correctly.
        final ServerSubLevel host = SableBridge.subLevelAt(server, pos);
        be.tickEmitter(server, state, host);
    }

    /**
     * Sable assembly hook: invoked when this BE is part of a contraption sub-level.
     * Vanilla's {@link net.minecraft.world.level.block.entity.BlockEntityTicker} is
     * not the right driver in that case; this method is.
     */
    @Override
    public void sable$tick(final ServerSubLevel subLevel) {
        if (!(level instanceof ServerLevel server)) return;
        tickEmitter(server, getBlockState(), subLevel);
    }

    void tickEmitter(
            final ServerLevel server, final BlockState state, final @Nullable ServerSubLevel host
    ) {
        final MagneticField previous = cachedField;
        MagneticField local = computeField(state);
        if (local == null) {
            cachedField = null;
            if (previous != null) markForClientSync(server);
            return;
        }

        // Adjacent Polarity Inverter blocks flip the field's polarity. Cheap 6-block
        // scan; only runs while the emitter is active.
        if (PolarityInverterBlock.shouldInvert(server, getBlockPos())) {
            local = new MagneticField(local.origin(), local.axis(), local.polarity().opposite(),
                    local.strength(), local.shape());
        }

        // When the emitter sits on a contraption (host != null), the blockpos-derived
        // origin/axis are sub-level-local; promote them to world space and exclude the
        // host from force application (no internal forces on the carrying ship).
        final MagneticField worldField = host == null
                ? local
                : SableBridge.promoteToWorldSpace(server, getBlockPos(), local);

        cachedField = worldField;
        // Resync to clients when the field meaningfully changes — going from null
        // ↔ non-null, or polarity / strength / shape change. Don't resync on every
        // tick (would flood the network) or on origin micro-changes (irrelevant
        // to the goggle/HUD readout).
        if (previous == null || !sameForClientDisplay(previous, worldField)) {
            markForClientSync(server);
        }
        FieldApplicator.apply(server, worldField, host, shipFilter());
        // Only ingest when the emitter sits in the open world — emitters mounted on a
        // contraption can't have a stable adjacent inventory anyway.
        if (host == null) {
            InventorySink.tryIngest(server, getBlockPos());
        }
    }

    private static boolean sameForClientDisplay(final MagneticField a, final MagneticField b) {
        return a.polarity() == b.polarity()
                && a.strength() == b.strength()
                && a.shape() == b.shape();
    }

    private void markForClientSync(final ServerLevel server) {
        setChanged();
        server.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 2 /* UPDATE_CLIENTS */);
    }

    /** Convenience for subclasses that store a horizontal facing in their blockstate. */
    protected static Direction facing(final BlockState state, final DirectionProperty prop) {
        return state.getValue(prop);
    }

    @Override
    public boolean addToTooltip(final List<Component> tooltip, final boolean isPlayerSneaking) {
        tooltip.addAll(FieldTooltipFormatter.format(cachedField, false));
        return true;
    }

    @Override
    public boolean addToGoggleTooltip(final List<Component> tooltip, final boolean isPlayerSneaking) {
        tooltip.add(Component.translatable("tooltip.magnetization.field_status")
                .withStyle(ChatFormatting.GRAY));
        tooltip.addAll(FieldTooltipFormatter.format(cachedField, true));
        return true;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) EmitterRegistry.register(level, getBlockPos());
    }

    @Override
    public void setRemoved() {
        if (level != null) EmitterRegistry.unregister(level, getBlockPos());
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Powered", powered);
        if (cachedField != null) tag.put("Field", cachedField.toNbt());
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        powered = tag.getBoolean("Powered");
        cachedField = tag.contains("Field") ? MagneticField.fromNbt(tag.getCompound("Field")) : null;
    }

    /** Pushes the BE's saved NBT to clients on chunk load — without this, client-side
     *  {@code cachedField} would always be null and goggle/HUD overlays would say
     *  "Inactive" on every emitter regardless of state. */
    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    /** Pushes BE NBT in response to {@link #markForClientSync}. */
    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(final Connection connection, final ClientboundBlockEntityDataPacket pkt,
                             final HolderLookup.Provider registries) {
        if (pkt.getTag() != null) loadCustomOnly(pkt.getTag(), registries);
    }
}
