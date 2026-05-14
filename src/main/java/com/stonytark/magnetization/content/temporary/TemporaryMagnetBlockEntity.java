package com.stonytark.magnetization.content.temporary;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.registry.MagBlockEntities;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Cheap, transient companion to the Permanent Magnet. Always-on (no redstone
 * gate) but expires after {@link #LIFETIME_TICKS} and reverts to an iron block
 * — the resource it was made from, minus the redstone consumed at crafting.
 *
 * <p>Polarity is chosen randomly at placement and stored as a blockstate
 * property, so a player can re-place the same item to roll for the side they
 * want, but a single block can't be flipped after placement (intentional —
 * keeps Temporary Magnets from being a strictly-better Permanent at any
 * point). Field emission is identical to the Permanent: WEAK omnidirectional.
 */
public class TemporaryMagnetBlockEntity extends AbstractEmitterBlockEntity {

    /** 10 in-game minutes (12 000 ticks). Long enough to lay down a short
     *  propulsion-track run, short enough that the player has to maintain
     *  it — distinguishing the block from the Permanent Magnet. */
    public static final long LIFETIME_TICKS = 12_000L;

    /** Tick the block was placed. {@link Long#MIN_VALUE} = not yet stamped
     *  (legacy save / freshly created BE); first tick fills it in. */
    private long placedTick = Long.MIN_VALUE;

    public TemporaryMagnetBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.TEMPORARY_MAGNET.get(), pos, state);
    }

    @Override
    protected @Nullable MagneticField computeField(final BlockState state) {
        // No redstone gate — Temporary Magnet is "always on" while alive.
        return new MagneticField(
                Vec3.atCenterOf(getBlockPos()),
                new Vec3(0, 1, 0),
                state.getValue(TemporaryMagnetBlock.POLARITY),
                MagneticStrength.WEAK,
                MagneticField.Shape.OMNIDIRECTIONAL);
    }

    @Override
    protected void tickEmitter(final ServerLevel server, final BlockState state,
                               final @Nullable ServerSubLevel host) {
        if (placedTick == Long.MIN_VALUE) {
            placedTick = server.getGameTime();
            setChanged();
        }
        // Expiration check before pumping the field — keeps the block from
        // applying a force on the same tick it reverts to iron.
        if (server.getGameTime() - placedTick >= LIFETIME_TICKS) {
            revertToIron(server);
            return;
        }
        super.tickEmitter(server, state, host);
    }

    /** Replace this Temporary Magnet with a vanilla iron_block. Drops a small
     *  particle puff via the sound at the position. The redstone consumed at
     *  craft time is intentionally not returned — that's the cost of using
     *  the temporary variant. */
    private void revertToIron(final ServerLevel server) {
        final BlockPos pos = getBlockPos();
        server.setBlock(pos, Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        server.playSound(null, pos,
                net.minecraft.sounds.SoundEvents.LODESTONE_BREAK,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 0.8f);
    }

    /** Remaining lifetime in ticks (0 = expiring this tick). Used by the
     *  item tooltip / Jade/Goggles overlay if exposed later. */
    public long remainingTicks(final long now) {
        if (placedTick == Long.MIN_VALUE) return LIFETIME_TICKS;
        return Math.max(0L, LIFETIME_TICKS - (now - placedTick));
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (placedTick != Long.MIN_VALUE) tag.putLong("PlacedTick", placedTick);
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        placedTick = tag.contains("PlacedTick") ? tag.getLong("PlacedTick") : Long.MIN_VALUE;
    }
}
