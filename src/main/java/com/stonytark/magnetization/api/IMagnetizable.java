package com.stonytark.magnetization.api;

/**
 * Marker interface for blocks/items/entities that experience magnetic fields.
 *
 * <p>This is the public hook for other mods that want their content to participate
 * in Magnetization's force resolution. Implement this on a {@code Block}, {@code Item},
 * or {@code Entity} subclass; the addon scans for it during physics ticks.
 *
 * <p>For full integration with Create: Aeronautics ships, no implementation is
 * required — the mod looks up Sable {@code SubLevel}s directly and applies forces
 * via the physics engine. {@link IMagnetizable} is intended for free-floating
 * vanilla-style entities (item drops, mobs, projectiles) that should be tugged by
 * nearby fields.
 */
public interface IMagnetizable {

    /**
     * @return how strongly this object responds to magnetic fields. Higher
     *         susceptibility means a larger fraction of the field force acts on
     *         the object. Range: [0.0, 1.0] is the conventional band; values
     *         beyond 1.0 are permitted for exotic content.
     */
    default double magneticSusceptibility() {
        return 1.0d;
    }

    /**
     * @return the polarity of this object. {@link MagneticPolarity#NORTH} is
     *         attracted to {@link MagneticPolarity#SOUTH} field sources and
     *         repelled by {@link MagneticPolarity#NORTH} ones (and vice-versa).
     */
    default MagneticPolarity magneticPolarity() {
        return MagneticPolarity.NORTH;
    }
}
