package com.stonytark.magnetization.api;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Implemented by block entities that emit a magnetic field.
 *
 * <p>The physics ticker walks every loaded block entity that implements this
 * interface and, when {@link #currentField()} is non-null, applies forces it
 * exerts on nearby Sable sub-levels (Create: Aeronautics ships) and on
 * {@link IMagnetizable} entities.
 */
public interface MagneticFieldSource {

    /**
     * @return the current field state, or {@code null} if the emitter is off
     *         (e.g. unpowered, redstone-disabled, no kinetic input).
     */
    @Nullable MagneticField currentField();

    /**
     * Whether this source should show the standard field-status line ("WEAK
     * NORTH" / "Inactive"). Blocks that aren't field emitters (e.g. the
     * Structural Inducer, a puller) return {@code false} so they don't
     * misleadingly read "Inactive" — they surface their own status instead.
     */
    default boolean showsFieldStatus() {
        return true;
    }

    /**
     * Optional extra HUD lines emitted by an emitter. Appended after the standard
     * field block by goggles, in-world hover, Jade, WTHIT, and TOP. Used for
     * source-specific status that doesn't fit the {@link MagneticField} model —
     * e.g. the Temporary Magnet's remaining lifetime.
     *
     * @param verbose {@code true} for the goggle/Jade/WTHIT/TOP surfaces;
     *                {@code false} for the compact in-world hover line.
     */
    default List<Component> extraTooltipLines(boolean verbose) {
        return Collections.emptyList();
    }
}
