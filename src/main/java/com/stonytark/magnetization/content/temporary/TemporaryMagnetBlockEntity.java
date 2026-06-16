package com.stonytark.magnetization.content.temporary;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.registry.MagBlockEntities;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Cheap, transient companion to the Permanent Magnet. Always-on (no redstone
 * gate) but expires after {@link #lifetimeTicks()} and reverts to an iron block
 * — the resource it was made from, minus the redstone consumed at crafting.
 *
 * <p>Polarity is chosen randomly at placement and stored as a blockstate
 * property, so a player can re-place the same item to roll for the side they
 * want, but a single block can't be flipped after placement (intentional —
 * keeps Temporary Magnets from being a strictly-better Permanent at any
 * point). Field emission is identical to the Permanent: WEAK omnidirectional.
 */
public class TemporaryMagnetBlockEntity extends AbstractEmitterBlockEntity {

    /** Configurable lifetime (default 10 in-game minutes / 12 000 ticks). Long
     *  enough to lay down a short propulsion-track run, short enough that the
     *  player has to maintain it — distinguishing the block from the Permanent
     *  Magnet. */
    private static long lifetimeTicks() {
        return com.stonytark.magnetization.config.MagConfig.temporaryMagnetLifetime();
    }

    /** Tick the block was placed. {@link Long#MIN_VALUE} = not yet stamped
     *  (legacy save / freshly created BE); first tick fills it in. */
    private long placedTick = Long.MIN_VALUE;

    public TemporaryMagnetBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.TEMPORARY_MAGNET.get(), pos, state);
    }

    /** Passive magnet — always on, never consumes redstone/FE. Suppresses the
     *  power-source / energy-buffer tooltip lines so WTHIT / Jade / goggles don't
     *  wrongly imply it can take power. */
    @Override
    protected boolean acceptsPower() { return false; }

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
        if (server.getGameTime() - placedTick >= lifetimeTicks()) {
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
        if (placedTick == Long.MIN_VALUE) return lifetimeTicks();
        return Math.max(0L, lifetimeTicks() - (now - placedTick));
    }

    @Override
    public List<Component> extraTooltipLines(final boolean verbose) {
        // Use a level-relative clock when we can; before the first server tick
        // (and on the client where game time is the synced level time anyway),
        // fall back to level.getGameTime so the line shows full lifetime
        // immediately on placement instead of "0:00".
        final long now = level != null ? level.getGameTime() : 0L;
        final long remaining = remainingTicks(now);
        final int totalSeconds = (int) (remaining / 20L);
        final int minutes = totalSeconds / 60;
        final int seconds = totalSeconds % 60;
        final long life = lifetimeTicks();
        final float frac = life > 0 ? remaining / (float) life : 0f;
        final ChatFormatting color = frac > 0.5f
                ? ChatFormatting.GREEN
                : frac > 0.2f ? ChatFormatting.YELLOW : ChatFormatting.RED;
        return List.of(Component.translatable(
                "tooltip.magnetization.temporary_magnet.decay",
                String.format("%d:%02d", minutes, seconds)).withStyle(color));
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
