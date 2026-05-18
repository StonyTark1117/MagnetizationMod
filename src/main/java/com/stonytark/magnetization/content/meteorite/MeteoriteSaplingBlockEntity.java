package com.stonytark.magnetization.content.meteorite;

import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Tracks growth progress for a planted meteorite sapling. Stamps the host
 * tick on first server tick; every {@link #TICK_INTERVAL} ticks compares
 * elapsed-since-planted against {@link #GROW_TICKS}; when reached, replaces
 * the sapling block with a fresh {@code meteorite_core}.
 *
 * <p>Stops the host BE from being part of the EmitterRegistry overhead — it
 * extends {@link BlockEntity} directly, not {@code AbstractEmitterBlockEntity},
 * because the sapling has no field of its own and shouldn't pay the emitter
 * tick + registry cost while incubating.
 */
public final class MeteoriteSaplingBlockEntity extends BlockEntity {

    /** Default ticks to fully grow. 36000 = 30 in-game minutes; long enough
     *  that a player has to commit, short enough that growing one is faster
     *  than finding a new natural meteorite. Server owners can override via
     *  {@code MagConfig.METEORITE_SAPLING_GROW_TICKS}. */
    public static final long GROW_TICKS = 36000L;

    /** Live grow duration in ticks. Reads from config when loaded; falls back
     *  to {@link #GROW_TICKS} when the spec isn't ready yet. */
    public static long growTicks() {
        try {
            return com.stonytark.magnetization.config.MagConfig.METEORITE_SAPLING_GROW_TICKS.get();
        } catch (final Throwable t) {
            return GROW_TICKS;
        }
    }

    /** Cadence of the growth check. We don't need precise per-tick resolution
     *  for a 30-minute timer — 200 ticks = 10s is plenty. */
    private static final int TICK_INTERVAL = 200;

    private static final long UNINITIALISED = Long.MIN_VALUE;
    private long plantedAtTick = UNINITIALISED;

    public MeteoriteSaplingBlockEntity(final BlockPos pos, final BlockState state) {
        super(com.stonytark.magnetization.registry.MagBlockEntities.METEORITE_SAPLING.get(), pos, state);
    }

    public static void serverTick(final Level level, final BlockPos pos,
                                   final BlockState state, final MeteoriteSaplingBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        final long now = server.getGameTime();
        if (be.plantedAtTick == UNINITIALISED) {
            be.plantedAtTick = now;
            be.setChanged();
            return;
        }
        if ((now % TICK_INTERVAL) != 0L) return;
        if ((now - be.plantedAtTick) < growTicks()) return;

        // Time's up — promote to a fully-charged meteorite core. The core's
        // own onLoad will register with EmitterRegistry and start its
        // decay clock from this server tick.
        server.setBlock(pos, MagBlocks.METEORITE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (plantedAtTick != UNINITIALISED) tag.putLong("PlantedAt", plantedAtTick);
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        plantedAtTick = tag.contains("PlantedAt") ? tag.getLong("PlantedAt") : UNINITIALISED;
    }

    /** @return 0..1 fraction of growth complete, for UI/tooltips. */
    public float growthProgress(final long now) {
        if (plantedAtTick == UNINITIALISED) return 0f;
        return progressForElapsed(now - plantedAtTick);
    }

    /** Pure-math helper: 0..1 growth fraction given elapsed ticks since planting.
     *  Clamped to [0, 1] so callers don't have to worry about negative elapsed
     *  (clock-rewind during world time changes) or post-mature values. Public so
     *  the boundary behaviour can be regression-tested. */
    public static float progressForElapsed(final long elapsed) {
        if (elapsed <= 0L) return 0f;
        if (elapsed >= GROW_TICKS) return 1f;
        return elapsed / (float) GROW_TICKS;
    }

    public @Nullable BlockEntityType<?> typeForExternal() { return getType(); }
}
