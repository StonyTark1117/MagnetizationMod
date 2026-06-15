package com.stonytark.magnetization.api;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * Snapshot of a Sable sub-level's intrinsic magnetic properties. Computed by
 * scanning the contraption's blocks and cached per ship; see
 * {@link com.stonytark.magnetization.physics.ShipMagneticRegistry}.
 *
 * <p>The two interesting outputs:
 * <ul>
 *   <li><b>polarity</b> — NORTH by default; flipped to SOUTH when an odd number
 *       of Polarity Inverter blocks ride the ship. The flip drives the
 *       attract/repel sign for every external field acting on the ship, the
 *       same way an entity's polarity does in the entity pass.</li>
 *   <li><b>susceptibility</b> — multiplicative gain on external force, derived
 *       from the count of on-board ferromagnetic blocks (iron, magnetite,
 *       etc.) plus a heavier per-magnet bonus. Magnets aboard contribute to
 *       susceptibility only — never to polarity — so a player can flip an
 *       Electromagnet's polarity for ship-to-ship gameplay without accidentally
 *       inverting their own ship and possibly tearing the contraption apart.</li>
 * </ul>
 *
 * <p>Counts are kept on the record so HUDs/goggles can explain how the number
 * was reached ("12 ferrous + 3 magnets → 1.85×") rather than just showing the
 * opaque multiplier.
 */
public record ShipMagneticState(
        MagneticPolarity polarity,
        double susceptibility,
        int ferrousBlockCount,
        int magnetBlockCount,
        int inverterBlockCount,
        int diamagneticBlockCount) {

    /** True if the ship carries diamagnetic blocks — it's then repelled by BOTH
     *  poles of any field (reacts to positive + negative the same way), instead
     *  of the normal like-repel/unlike-attract polarity response. */
    public boolean isDiamagnetic() {
        return diamagneticBlockCount > 0;
    }

    /** Fallback for ships that haven't been scanned yet (e.g. a freshly assembled
     *  contraption being touched by a field for the very first frame). NORTH with
     *  baseline susceptibility = the 1.0.0 default behaviour. */
    public static final ShipMagneticState DEFAULT =
            new ShipMagneticState(MagneticPolarity.NORTH, 1.0d, 0, 0, 0, 0);

    /** Serialize for BE→client sync. The HUD/goggle tooltips read this snapshot
     *  off the BE rather than trying to recompute the ship state on the client,
     *  where Sable's server-side state isn't available. */
    public CompoundTag toNbt() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("p", polarity.name());
        tag.putDouble("s", susceptibility);
        tag.putInt("f", ferrousBlockCount);
        tag.putInt("m", magnetBlockCount);
        tag.putInt("i", inverterBlockCount);
        tag.putInt("d", diamagneticBlockCount);
        return tag;
    }

    public static @Nullable ShipMagneticState fromNbt(final @Nullable CompoundTag tag) {
        if (tag == null) return null;
        try {
            return new ShipMagneticState(
                    MagneticPolarity.valueOf(tag.getString("p")),
                    tag.getDouble("s"),
                    tag.getInt("f"),
                    tag.getInt("m"),
                    tag.getInt("i"),
                    tag.getInt("d"));
        } catch (final Throwable t) {
            return null;
        }
    }
}
