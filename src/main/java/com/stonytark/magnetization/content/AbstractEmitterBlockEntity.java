package com.stonytark.magnetization.content;

import com.stonytark.magnetization.api.FieldTooltipFormatter;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticFieldSource;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.energy.EnergyStorage;
import com.stonytark.magnetization.content.inverter.PolarityInverterBlock;
import com.stonytark.magnetization.physics.EmitterRegistry;
import com.stonytark.magnetization.physics.FieldApplicator;
import com.stonytark.magnetization.physics.InventorySink;
import com.stonytark.magnetization.physics.SableBridge;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
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
        IHaveGoggleInformation {

    private boolean powered = false;
    /** True for the current tick if energy was successfully consumed this tick
     *  to drive the field. Reset/recomputed each {@link #tickEmitter} call. */
    private boolean energyActiveThisTick = false;
    /** Snapshot of {@link #energyActiveThisTick} from the prior tick; used to
     *  fire the {@code energy_activated} advancement trigger on the rising
     *  edge only, not every tick the emitter is active. Not persisted — a
     *  player reload always re-fires the trigger the first time they're
     *  nearby an energy-driven emitter, which is fine. */
    private boolean wasEnergyActiveLastTick = false;
    /** Game-time of the last energy-state network sync. We resync once per
     *  second during active drain so the client HUD/goggle/Jade reading stays
     *  within ~1s of the true buffer level without flooding the network. */
    private long lastEnergySyncTick = Long.MIN_VALUE;
    /** Energy snapshot at the last sync — used to also fire a sync when the
     *  buffer changes meaningfully (e.g. a large FE pulse from a cable). */
    private int lastSyncedEnergy = 0;
    /** Internal FE buffer. Receives from external sources (capacity + transfer
     *  rate from config); drains while emitting if redstone signal is absent.
     *  Persists across saves via NBT. {@code maxExtract = 0} disables external
     *  draining — the buffer is one-way, fed by wires/cables and consumed
     *  internally by the emitter only via {@link InternalEnergyBuffer#drainInternal}. */
    private final InternalEnergyBuffer energyBuffer = new InternalEnergyBuffer(
            emitterEnergyCapacity(), emitterEnergyTransferRate());

    /** EnergyStorage subclass exposing the protected {@code energy} field so
     *  the emitter can drain it internally without making the capability
     *  externally extractable. */
    private static final class InternalEnergyBuffer extends EnergyStorage {
        InternalEnergyBuffer(final int capacity, final int maxReceive) {
            super(capacity, maxReceive, 0);
        }
        void drainInternal(final int amount) {
            this.energy = Math.max(0, this.energy - amount);
        }
        void setStored(final int value) {
            this.energy = Math.max(0, Math.min(this.capacity, value));
        }
    }

    @Nullable MagneticField cachedField = null;
    /** Snapshot of the host ship's magnetic state, synced to the client so HUDs
     *  can show "Ship: NORTH ×1.4" without re-running the server-side scan.
     *  {@code null} when this emitter is in the open world (not on a contraption). */
    private @Nullable com.stonytark.magnetization.api.ShipMagneticState cachedShipState = null;
    /** This emitter block's registry path, resolved once on first tick (invariant
     *  for the BE's lifetime) so the soft-disable check doesn't reverse-look-up the
     *  block every tick. */
    private @Nullable String cachedBlockPath = null;

    /** Per-emitter strength override. {@code null} = use the subclass's default tier. */
    private @Nullable MagneticStrength strengthOverride = null;
    /** Per-emitter range override (in blocks). {@code 0} = use the strength tier's default. */
    private int rangeOverride = 0;
    /** Per-emitter polarity override. {@code null} = use the subclass's default. */
    private @Nullable com.stonytark.magnetization.api.MagneticPolarity polarityOverride = null;
    /** Hematite Lens polarity lock. When non-null, the emitter's field is forced
     *  to this polarity at the END of {@link #tickEmitter} (after the Polarity
     *  Inverter step), making the lock take precedence over the inverter.
     *  Installed/removed by the Hematite Lens item. */
    private @Nullable com.stonytark.magnetization.api.MagneticPolarity lockedPolarity = null;

    protected AbstractEmitterBlockEntity(
            final BlockEntityType<?> type,
            final BlockPos pos,
            final BlockState state
    ) {
        super(type, pos, state);
    }

    /** Returns the <em>effective</em> powered state — true if redstone signal
     *  is active AND that source is allowed, OR if energy was successfully
     *  drained this tick AND that source is allowed. Subclasses' field-builder
     *  methods consult this to decide whether to emit. */
    public boolean isPowered() {
        return (allowRedstonePower() && powered) || (allowEnergyPower() && energyActiveThisTick);
    }

    /** Direct query: is a redstone signal currently driving this emitter
     *  (ignoring energy)? Used by tooltips/HUD that want to surface "powered
     *  by redstone" vs "powered by energy" separately. */
    public boolean isRedstonePowered() { return powered; }

    /** Direct query: did energy drive this tick? */
    public boolean isEnergyPowered() { return energyActiveThisTick; }

    /** Exposed to {@code RegisterCapabilitiesEvent} so external sources can
     *  push FE into this buffer. External {@code extractEnergy} calls return 0
     *  (the buffer's {@code maxExtract} is 0) — the emitter is the only thing
     *  that drains it. */
    public net.neoforged.neoforge.energy.IEnergyStorage getEnergyBuffer() { return energyBuffer; }

    /** Direct buffer setter for the {@code /magnetization debug energy} command —
     *  bypasses the {@code maxReceive} clamp so a single command can fill an
     *  empty buffer to capacity without needing a cabled-up FE source. Marks
     *  dirty + triggers a client sync so HUD/goggle/Jade update immediately. */
    public void setEnergyForDebug(final int amount) {
        energyBuffer.setStored(amount);
        setChanged();
        if (level instanceof ServerLevel server) markForClientSync(server);
    }

    private static boolean allowRedstonePower() {
        try { return MagConfig.ALLOW_REDSTONE_POWER.get(); } catch (Throwable t) { return true; }
    }

    private static boolean allowEnergyPower() {
        try { return MagConfig.ALLOW_ENERGY_POWER.get(); } catch (Throwable t) { return true; }
    }

    private static int emitterEnergyCapacity() {
        try { return MagConfig.EMITTER_ENERGY_CAPACITY.get(); } catch (Throwable t) { return 50_000; }
    }

    private static int emitterEnergyTransferRate() {
        try { return MagConfig.EMITTER_ENERGY_TRANSFER_RATE.get(); } catch (Throwable t) { return 200; }
    }

    private static int emitterEnergyDrainPerTick() {
        try { return MagConfig.EMITTER_ENERGY_DRAIN_PER_TICK.get(); } catch (Throwable t) { return 10; }
    }

    /** Effective strength tier for {@link #computeField}: override when set,
     *  otherwise the default. Defaults to {@link MagneticStrength#STRONG} for
     *  every menu-bearing emitter — the {@code base} the subclass passes is
     *  ignored when no override is in play. */
    public MagneticStrength effectiveStrength(final MagneticStrength base) {
        return strengthOverride != null ? strengthOverride : MagneticStrength.STRONG;
    }

    /** Effective range in blocks: override when set, otherwise
     *  {@link #defaultEffectiveRange}. Subclasses describe their default range;
     *  the base falls back to the strength tier's nominal value. */
    public double effectiveRange(final MagneticStrength tier) {
        return rangeOverride > 0 ? rangeOverride : defaultEffectiveRange(tier);
    }

    /** Subclass-supplied default range when no override has been dialed in.
     *  Conventionally returns half of the per-block admin ceiling from
     *  {@link com.stonytark.magnetization.config.MagConfig}, so emitters
     *  ship with a sensible midway range without forcing the player into
     *  the GUI. Override per-emitter to plug in the right config key. */
    protected double defaultEffectiveRange(final MagneticStrength tier) {
        return tier.range();
    }

    /** Effective polarity: override when set, otherwise base. */
    public com.stonytark.magnetization.api.MagneticPolarity effectivePolarity(
            final com.stonytark.magnetization.api.MagneticPolarity base) {
        return polarityOverride != null ? polarityOverride : base;
    }

    public @Nullable MagneticStrength getStrengthOverride() { return strengthOverride; }
    public int getRangeOverride() { return rangeOverride; }
    public @Nullable com.stonytark.magnetization.api.MagneticPolarity getPolarityOverride() { return polarityOverride; }

    public void setStrengthOverride(final @Nullable MagneticStrength s) {
        if (this.strengthOverride == s) return;
        this.strengthOverride = s;
        this.cachedField = null;
        setChanged();
        if (level instanceof ServerLevel server) markForClientSync(server);
    }

    public void setRangeOverride(final int blocks) {
        // Hard floor at 0 (= "use built-in default"). The upper bound matches the
        // GUI's RANGE_MAX so a maxed-out slider isn't quietly truncated here — the
        // per-emitter config ceiling does the actual game-balance clamp upstream
        // in EmitterMenu.bumpRange.
        final int clamped = Math.max(0, Math.min(com.stonytark.magnetization.menu.EmitterMenu.RANGE_MAX, blocks));
        if (this.rangeOverride == clamped) return;
        this.rangeOverride = clamped;
        this.cachedField = null;
        setChanged();
        if (level instanceof ServerLevel server) markForClientSync(server);
    }

    public void setPolarityOverride(final @Nullable com.stonytark.magnetization.api.MagneticPolarity p) {
        if (this.polarityOverride == p) return;
        this.polarityOverride = p;
        this.cachedField = null;
        setChanged();
        if (level instanceof ServerLevel server) markForClientSync(server);
    }

    public @Nullable com.stonytark.magnetization.api.MagneticPolarity getLockedPolarity() {
        return lockedPolarity;
    }

    /** Apply or clear the Hematite Lens polarity lock. While non-null, the
     *  emitter's field will reset to this polarity after each tick's
     *  Inverter pass, defeating the inverter and freezing polarity. */
    public void setLockedPolarity(final @Nullable com.stonytark.magnetization.api.MagneticPolarity p) {
        if (this.lockedPolarity == p) return;
        this.lockedPolarity = p;
        this.cachedField = null;
        setChanged();
        if (level instanceof ServerLevel server) markForClientSync(server);
    }

    /** Reset every per-emitter override at once. Useful for the wrench-tap
     *  shortcut so players can quickly restore an emitter to its built-in
     *  defaults without walking through the GUI. Subclasses can override to
     *  also drop subtype-specific state (anchor binding, etc.). */
    public void resetOverrides() {
        if (strengthOverride == null && rangeOverride == 0 && polarityOverride == null) return;
        strengthOverride = null;
        rangeOverride = 0;
        polarityOverride = null;
        cachedField = null;
        setChanged();
        if (level instanceof ServerLevel server) markForClientSync(server);
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

    protected void tickEmitter(
            final ServerLevel server, final BlockState state, final @Nullable ServerSubLevel host
    ) {
        final MagneticField previous = cachedField;
        // Soft-disable hook: if the operator has listed this block path in
        // config.content.disabledBlocks, treat the emitter as off regardless
        // of redstone state. Existing placements survive saves but stay inert.
        // The path is invariant for this BE's lifetime, so resolve the registry
        // key + string once rather than reverse-looking-up the block every tick.
        if (cachedBlockPath == null) {
            cachedBlockPath = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        }
        if (MagConfig.isBlockDisabled(cachedBlockPath)) {
            if (cachedField != null) {
                cachedField = null;
                markForClientSync(server);
            }
            return;
        }

        // Power-source resolution: redstone takes priority (it's free; no
        // reason to burn energy if it's already running). If redstone isn't
        // driving us, try to consume one tick's worth of energy. The drain is
        // a single extract — if the buffer can't cover it, energyActiveThisTick
        // stays false and the field falls off.
        energyActiveThisTick = false;
        if (!(allowRedstonePower() && powered) && allowEnergyPower()) {
            final int drain = emitterEnergyDrainPerTick();
            if (drain <= 0) {
                // Free-energy mode: any buffer content (or even an empty one)
                // counts as powered. Useful for creative/test setups.
                energyActiveThisTick = true;
            } else if (energyBuffer.getEnergyStored() >= drain) {
                energyBuffer.drainInternal(drain);
                energyActiveThisTick = true;
            }
        }
        // Rising-edge advancement fire: when an emitter first goes energy-active,
        // trigger the `energy_activated` criterion for every ServerPlayer within
        // 16 blocks. Heavy-handed by design — the player must be present to see
        // it, and rising edges are rare enough that scanning the player list per
        // transition is cheap.
        if (energyActiveThisTick && !wasEnergyActiveLastTick) {
            final double cx = getBlockPos().getX() + 0.5;
            final double cy = getBlockPos().getY() + 0.5;
            final double cz = getBlockPos().getZ() + 0.5;
            for (final net.minecraft.server.level.ServerPlayer sp : server.players()) {
                if (sp.distanceToSqr(cx, cy, cz) <= 16.0 * 16.0) {
                    com.stonytark.magnetization.registry.MagTriggers.ENERGY_ACTIVATED.get().trigger(sp);
                }
            }
        }
        wasEnergyActiveLastTick = energyActiveThisTick;

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

        // Adjacent Hematite blocks dampen the field strength tier (one step per
        // adjacent block, clamped to WEAK). Antiferromagnetic flavour — hematite
        // cancels out applied fields.
        final MagneticStrength dampened = com.stonytark.magnetization.content.hematite.HematiteBlock
                .dampenedStrength(server, getBlockPos(), local.strength());
        if (dampened != local.strength()) {
            local = new MagneticField(local.origin(), local.axis(), local.polarity(),
                    dampened, local.shape());
        }

        // Hematite Lens polarity lock: takes precedence over any Inverter flip
        // earlier in this tick. Set via the lens item's right-click; cleared
        // via shift+right-click.
        if (lockedPolarity != null && local.polarity() != lockedPolarity) {
            local = new MagneticField(local.origin(), local.axis(), lockedPolarity,
                    local.strength(), local.shape());
        }

        // When the emitter sits on a contraption (host != null), the blockpos-derived
        // origin/axis are sub-level-local; promote them to world space and exclude the
        // host from force application (no internal forces on the carrying ship).
        final MagneticField worldField = host == null
                ? local
                : SableBridge.promoteToWorldSpace(host.logicalPose(), local);

        cachedField = worldField;
        // Refresh the cached host-ship state so HUDs/goggles can show it. Cheap:
        // the registry is a TTL cache, so most ticks return immediately. Updates
        // to the snapshot are propagated to clients via the BE-NBT sync path.
        final com.stonytark.magnetization.api.ShipMagneticState previousShipState = cachedShipState;
        cachedShipState = host == null
                ? null
                : com.stonytark.magnetization.physics.ShipMagneticRegistry.get(server, host);
        // Resync to clients when the field meaningfully changes — going from null
        // ↔ non-null, or polarity / strength / shape change. Don't resync on every
        // tick (would flood the network) or on origin micro-changes (irrelevant
        // to the goggle/HUD readout).
        final long now = server.getGameTime();
        final int currentEnergy = energyBuffer.getEnergyStored();
        final boolean energyChangedEnough =
                Math.abs(currentEnergy - lastSyncedEnergy) >= Math.max(1, energyBuffer.getMaxEnergyStored() / 100);
        final boolean energyPeriodicDue = energyActiveThisTick && (now - lastEnergySyncTick) >= 20;
        if (previous == null
                || !sameForClientDisplay(previous, worldField)
                || !sameShipStateForClientDisplay(previousShipState, cachedShipState)
                || energyChangedEnough
                || energyPeriodicDue) {
            markForClientSync(server);
            lastEnergySyncTick = now;
            lastSyncedEnergy = currentEnergy;
        }
        FieldApplicator.apply(server, worldField, host, shipFilter());
        // Only ingest when the emitter sits in the open world — emitters mounted on a
        // contraption can't have a stable adjacent inventory anyway.
        if (host == null) {
            InventorySink.tryIngest(server, getBlockPos());
        }
    }

    /** Resync gate for ship state: only re-network when polarity or counts move
     *  enough to change the HUD readout. The susceptibility number is derived from
     *  counts, so comparing counts already covers it. */
    private static boolean sameShipStateForClientDisplay(
            final @Nullable com.stonytark.magnetization.api.ShipMagneticState a,
            final @Nullable com.stonytark.magnetization.api.ShipMagneticState b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.polarity() == b.polarity()
                && a.ferrousBlockCount() == b.ferrousBlockCount()
                && a.magnetBlockCount() == b.magnetBlockCount()
                && a.inverterBlockCount() == b.inverterBlockCount();
    }

    private static boolean sameForClientDisplay(final MagneticField a, final MagneticField b) {
        return a.polarity() == b.polarity()
                && a.strength() == b.strength()
                && a.shape() == b.shape()
                && a.customRange() == b.customRange();
    }

    protected void markForClientSync(final ServerLevel server) {
        setChanged();
        server.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 2 /* UPDATE_CLIENTS */);
    }

    /** Convenience for subclasses that store a horizontal facing in their blockstate. */
    protected static Direction facing(final BlockState state, final DirectionProperty prop) {
        return state.getValue(prop);
    }

    /** Subclass hook: does this emitter actually accept power input (redstone
     *  or FE/RF)? Passive emitters like pyrrhotite_block (heat-driven) and
     *  titanomagnetite_block (paleomagnetic record) don't — surfacing the
     *  power-source / energy-buffer tooltip line on them is a lie. Default
     *  true to cover the standard powered emitters. */
    protected boolean acceptsPower() { return true; }

    @Override
    public List<Component> extraTooltipLines(final boolean verbose) {
        final List<Component> lines = new java.util.ArrayList<>(4);
        if (acceptsPower()) {
            // Power-source line: highest signal-to-noise. Always show,
            // regardless of whether energy or redstone is driving so players
            // know at a glance.
            final int energy = energyBuffer.getEnergyStored();
            final int capacity = energyBuffer.getMaxEnergyStored();
            if (energy > 0 || isPowered()) {
                final String source = energyActiveThisTick ? "energy"
                        : (powered ? "redstone" : "idle");
                final ChatFormatting sourceColor = energyActiveThisTick ? ChatFormatting.GOLD
                        : (powered ? ChatFormatting.RED : ChatFormatting.DARK_GRAY);
                lines.add(Component.translatable("tooltip.magnetization.power_source",
                                Component.translatable("tooltip.magnetization.power_source." + source)
                                        .withStyle(sourceColor))
                        .withStyle(ChatFormatting.GRAY));
            }
            // Energy buffer line: only show when there's actually a buffer to
            // surface. Hides the line for fresh placements that never received
            // energy, so the tooltip stays clean when FE isn't in use on this
            // server.
            if (energy > 0 || verbose) {
                lines.add(Component.translatable("tooltip.magnetization.energy",
                                String.format("%,d / %,d", energy, capacity))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        if (cachedShipState != null) {
            lines.addAll(shipStateTooltipLines(cachedShipState, verbose));
        }
        return lines;
    }

    /** "On ship: NORTH ×1.4 (12 ferrous, 3 magnets, 1 inverter)" — verbose form
     *  for goggles/Jade/WTHIT/TOP; the compact form drops the count breakdown. */
    private static List<Component> shipStateTooltipLines(
            final com.stonytark.magnetization.api.ShipMagneticState state, final boolean verbose) {
        final ChatFormatting polColor = switch (state.polarity()) {
            case NORTH -> ChatFormatting.RED;
            case SOUTH -> ChatFormatting.AQUA;
            case NONE  -> ChatFormatting.GRAY;
        };
        final String summary = String.format("%s ×%.2f",
                state.polarity().getSerializedName().toUpperCase(java.util.Locale.ROOT),
                state.susceptibility());
        final Component head = Component.translatable("tooltip.magnetization.ship_state",
                        Component.literal(summary).withStyle(polColor))
                .withStyle(ChatFormatting.GRAY);
        if (!verbose) return List.of(head);
        final Component detail = Component.literal(String.format(
                "  %d ferrous, %d magnets, %d inverters",
                state.ferrousBlockCount(), state.magnetBlockCount(), state.inverterBlockCount()))
                .withStyle(ChatFormatting.DARK_GRAY);
        return List.of(head, detail);
    }

    @Override
    public boolean addToGoggleTooltip(final List<Component> tooltip, final boolean isPlayerSneaking) {
        // Create's GoggleOverlayRenderer draws the icon at (posX+10, posY-16)
        // which only horizontally overlaps the FIRST tooltip line. Lines 2+
        // are below the icon entirely and should render flush-left — indenting
        // them looks lopsided. So: 8-space prefix on the header line only,
        // content lines flush.
        tooltip.add(Component.literal("        ").append(
                Component.translatable("tooltip.magnetization.field_status")
                        .withStyle(ChatFormatting.GRAY)));
        tooltip.addAll(FieldTooltipFormatter.format(cachedField, true));
        tooltip.addAll(extraTooltipLines(true));
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
        if (strengthOverride != null) tag.putString("StrengthOverride", strengthOverride.name());
        if (rangeOverride > 0) tag.putInt("RangeOverride", rangeOverride);
        if (polarityOverride != null) tag.putString("PolarityOverride", polarityOverride.name());
        if (lockedPolarity != null) tag.putString("LockedPolarity", lockedPolarity.name());
        if (cachedShipState != null) tag.put("ShipState", cachedShipState.toNbt());
        if (energyBuffer.getEnergyStored() > 0) tag.putInt("Energy", energyBuffer.getEnergyStored());
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        powered = tag.getBoolean("Powered");
        cachedField = tag.contains("Field") ? MagneticField.fromNbt(tag.getCompound("Field")) : null;
        strengthOverride = tag.contains("StrengthOverride")
                ? MagneticStrength.valueOf(tag.getString("StrengthOverride")) : null;
        rangeOverride = tag.contains("RangeOverride") ? tag.getInt("RangeOverride") : 0;
        polarityOverride = tag.contains("PolarityOverride")
                ? com.stonytark.magnetization.api.MagneticPolarity.valueOf(tag.getString("PolarityOverride")) : null;
        lockedPolarity = tag.contains("LockedPolarity")
                ? com.stonytark.magnetization.api.MagneticPolarity.valueOf(tag.getString("LockedPolarity")) : null;
        cachedShipState = tag.contains("ShipState")
                ? com.stonytark.magnetization.api.ShipMagneticState.fromNbt(tag.getCompound("ShipState"))
                : null;
        if (tag.contains("Energy")) energyBuffer.setStored(tag.getInt("Energy"));
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

    /** Attach emitter state to crash reports. Vanilla's base already adds block
     *  + position; this adds the magnetism-specific fields that a maintainer
     *  reading the report would otherwise have to reproduce from a save. */
    @Override
    public void fillCrashReportCategory(final net.minecraft.CrashReportCategory category) {
        super.fillCrashReportCategory(category);
        category.setDetail("Magnetization Powered (redstone)", () -> Boolean.toString(powered));
        category.setDetail("Magnetization Energy Active", () -> Boolean.toString(energyActiveThisTick));
        category.setDetail("Magnetization Energy Buffer",
                () -> energyBuffer.getEnergyStored() + " / " + energyBuffer.getMaxEnergyStored());
        category.setDetail("Magnetization Strength Override",
                () -> strengthOverride == null ? "<default>" : strengthOverride.name());
        category.setDetail("Magnetization Range Override",
                () -> rangeOverride > 0 ? Integer.toString(rangeOverride) : "<default>");
        category.setDetail("Magnetization Polarity Override",
                () -> polarityOverride == null ? "<default>" : polarityOverride.name());
        category.setDetail("Magnetization Cached Field",
                () -> cachedField == null ? "<none>" : cachedField.toString());
        category.setDetail("Magnetization Cached Ship State",
                () -> cachedShipState == null ? "<not on ship>" : cachedShipState.toString());
    }
}
