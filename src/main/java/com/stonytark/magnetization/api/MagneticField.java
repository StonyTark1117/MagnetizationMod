package com.stonytark.magnetization.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A snapshot of a magnetic field source's state at one tick.
 *
 * @param origin       the world-space center of the field emitter
 * @param axis         unit vector defining the emitter's facing — used by directional
 *                     fields (tractor beam, repulsor cone). For omnidirectional fields
 *                     (anchor, electromagnet) this is conventionally {@code (0, 1, 0)}
 *                     and the {@code shape} ignores it.
 * @param polarity     sign of the field; controls attract vs. repel
 * @param strength     force magnitude tier
 * @param shape        how the force decays with position relative to {@code origin}
 * @param customRange  per-field range override in blocks; {@code 0} = use the strength
 *                     tier's default range. Used by emitters whose GUI lets the player
 *                     dial the radius separately from the strength tier.
 */
public record MagneticField(
        Vec3 origin,
        Vec3 axis,
        MagneticPolarity polarity,
        MagneticStrength strength,
        Shape shape,
        double customRange
) {

    public MagneticField(final Vec3 origin, final Vec3 axis, final MagneticPolarity polarity,
                         final MagneticStrength strength, final Shape shape) {
        this(origin, axis, polarity, strength, shape, 0.0d);
    }

    public enum Shape {
        /** Force points radially toward (or away from) {@code origin}; magnitude falls off as 1/r^2. */
        OMNIDIRECTIONAL,
        /** Force is parallel to {@code axis}; magnitude falls off linearly with distance along the axis. */
        DIRECTIONAL,
        /** Force points along {@code axis}, but only for points inside a cone with half-angle 45° around the axis. */
        CONICAL
    }

    public double range() {
        return customRange > 0 ? customRange : strength.range();
    }

    /** Serialize for BE→client network sync. */
    public CompoundTag toNbt() {
        final CompoundTag tag = new CompoundTag();
        tag.putDouble("ox", origin.x);
        tag.putDouble("oy", origin.y);
        tag.putDouble("oz", origin.z);
        tag.putDouble("ax", axis.x);
        tag.putDouble("ay", axis.y);
        tag.putDouble("az", axis.z);
        tag.putString("p", polarity.name());
        tag.putString("s", strength.name());
        tag.putString("sh", shape.name());
        if (customRange > 0) tag.putDouble("cr", customRange);
        return tag;
    }

    /** Inverse of {@link #toNbt()}. Returns {@code null} if {@code tag} is null or malformed. */
    public static @Nullable MagneticField fromNbt(final @Nullable CompoundTag tag) {
        if (tag == null) return null;
        try {
            return new MagneticField(
                    new Vec3(tag.getDouble("ox"), tag.getDouble("oy"), tag.getDouble("oz")),
                    new Vec3(tag.getDouble("ax"), tag.getDouble("ay"), tag.getDouble("az")),
                    MagneticPolarity.valueOf(tag.getString("p")),
                    MagneticStrength.valueOf(tag.getString("s")),
                    Shape.valueOf(tag.getString("sh")),
                    tag.contains("cr") ? tag.getDouble("cr") : 0.0d);
        } catch (final Throwable t) {
            return null;
        }
    }
}
