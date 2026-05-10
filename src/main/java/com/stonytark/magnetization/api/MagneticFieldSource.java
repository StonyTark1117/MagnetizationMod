package com.stonytark.magnetization.api;

import org.jetbrains.annotations.Nullable;

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
}
