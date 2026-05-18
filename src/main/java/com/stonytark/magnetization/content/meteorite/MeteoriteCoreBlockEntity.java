package com.stonytark.magnetization.content.meteorite;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Decaying-field emitter. Tracks a {@code chargedAtTick} timestamp; effective
 * strength tier steps down over {@link #DECAY_TICKS} from {@code EXTREME} at
 * full charge, through {@code STRONG}, {@code WEAK}, then null (inert) when
 * fully decayed. Right-clicking the host block with a ferromagnetic item
 * calls {@link #refill} to reset the timer.
 *
 * <p>Newly-generated meteorites stamp {@code chargedAtTick} on their first
 * load — initial freshness window matches the player's chance to actually
 * stumble on the block. Once fully decayed and unattended, the block becomes
 * a permanent magnetic-blank decoration until the player feeds it.
 */
public final class MeteoriteCoreBlockEntity extends AbstractEmitterBlockEntity {

    /** Default ticks between full charge and full decay. 12000 = 10 in-game
     *  minutes, enough time to spot the field on a magnetic detector and
     *  travel to the source, but short enough that hoarding requires active
     *  feeding. Server owners can override via {@code MagConfig.METEORITE_DECAY_TICKS}. */
    public static final long DECAY_TICKS = 12000L;

    /** Resolve the live decay duration in ticks. Reads from config when
     *  available, falls back to {@link #DECAY_TICKS} when the spec hasn't
     *  been loaded yet (early-load / unit-test contexts). */
    public static long decayTicks() {
        try {
            return com.stonytark.magnetization.config.MagConfig.METEORITE_DECAY_TICKS.get();
        } catch (final Throwable t) {
            return DECAY_TICKS;
        }
    }

    /** Pure-function decay-tier mapping. Extracted so the boundary math
     *  ({@code <decay/3} → EXTREME, {@code <2*decay/3} → STRONG, otherwise
     *  WEAK or null) can be regression-tested without a live ServerLevel.
     *  Takes {@code decayTicks} as a parameter so tests can pin behaviour
     *  independently of the runtime config.
     *  @return active tier at {@code elapsed} ticks after full charge, or
     *  {@code null} if the meteorite has fully decayed. */
    public static @Nullable MagneticStrength tierForElapsed(final long elapsed, final long decayTicks) {
        if (elapsed >= decayTicks)                  return null;
        if (elapsed < decayTicks / 3L)              return MagneticStrength.EXTREME;
        if (elapsed < (2L * decayTicks) / 3L)       return MagneticStrength.STRONG;
        return MagneticStrength.WEAK;
    }

    /** Default-decay overload — convenience for tests + tooltip code that
     *  doesn't have a level-aware decayTicks() handy. */
    public static @Nullable MagneticStrength tierForElapsed(final long elapsed) {
        return tierForElapsed(elapsed, DECAY_TICKS);
    }

    /** Sentinel for "never charged yet" — set on first server tick if absent.
     *  Negative values guarantee no overlap with real game-time. */
    private static final long UNINITIALISED = Long.MIN_VALUE;

    private long chargedAtTick = UNINITIALISED;

    public MeteoriteCoreBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.METEORITE_CORE.get(), pos, state);
    }

    /** Reset the decay timer to full charge. Called when a player right-clicks
     *  the block with a ferromagnetic item, and on first server-tick for
     *  freshly worldgen'd blocks. Forces a client sync so WTHIT/HUD updates
     *  immediately — setChanged alone doesn't push the new chargedAtTick to
     *  clients in time for the next hover frame. */
    public void refill(final long now) {
        chargedAtTick = now;
        setChanged();
        if (getLevel() instanceof net.minecraft.server.level.ServerLevel server) {
            markForClientSync(server);
        }
    }

    public boolean isAtFullCharge() {
        if (getLevel() == null) return false;
        return chargedAtTick != UNINITIALISED
                && (getLevel().getGameTime() - chargedAtTick) < 20L;
    }

    @Override
    protected @Nullable MagneticField computeField(final BlockState state) {
        if (getLevel() == null) return null;
        final long now = getLevel().getGameTime();

        // First server tick after worldgen: start the clock at full charge.
        if (chargedAtTick == UNINITIALISED) {
            chargedAtTick = now;
            setChanged();
        }

        final long elapsed = now - chargedAtTick;
        final MagneticStrength tier = tierForElapsed(elapsed, decayTicks());
        if (tier == null) return null;

        return new MagneticField(
                Vec3.atCenterOf(getBlockPos()),
                new Vec3(0, 1, 0),
                MagneticPolarity.NORTH,
                tier,
                MagneticField.Shape.OMNIDIRECTIONAL
        );
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (chargedAtTick != UNINITIALISED) tag.putLong("ChargedAt", chargedAtTick);
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        chargedAtTick = tag.contains("ChargedAt") ? tag.getLong("ChargedAt") : UNINITIALISED;
    }

    @Override
    public List<Component> extraTooltipLines(final boolean verbose) {
        final List<Component> lines = super.extraTooltipLines(verbose);
        if (getLevel() == null || chargedAtTick == UNINITIALISED) return lines;
        final long elapsed = getLevel().getGameTime() - chargedAtTick;
        final long decay = decayTicks();
        if (elapsed >= decay) {
            lines.add(Component.translatable("tooltip.magnetization.meteorite_inert")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            final float remaining = 1f - (elapsed / (float) decay);
            final ChatFormatting colour = remaining > 0.66f ? ChatFormatting.AQUA
                    : (remaining > 0.33f ? ChatFormatting.YELLOW : ChatFormatting.RED);
            lines.add(Component.translatable("tooltip.magnetization.meteorite_charge",
                            String.format(Locale.ROOT, "%.0f", remaining * 100f))
                    .withStyle(colour));
        }
        return lines;
    }
}
