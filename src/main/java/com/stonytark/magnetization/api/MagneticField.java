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
 * @param forceOverride per-field force override in Newtons; {@code 0} = use the strength
 *                     tier's nominal force. Set by emitters running with an "Analog
 *                     Redstone" config toggle on, where the redstone level (1-15) drives
 *                     force on a continuous ramp instead of the four-step tier ladder.
 *                     {@code strength} stays the <em>configured</em> tier either way, so
 *                     it keeps driving {@link #range()} and the GUI/HUD readouts.
 */
public record MagneticField(
        Vec3 origin,
        Vec3 axis,
        MagneticPolarity polarity,
        MagneticStrength strength,
        Shape shape,
        double customRange,
        double forceOverride
) {

    public MagneticField(final Vec3 origin, final Vec3 axis, final MagneticPolarity polarity,
                         final MagneticStrength strength, final Shape shape) {
        this(origin, axis, polarity, strength, shape, 0.0d, 0.0d);
    }

    public MagneticField(final Vec3 origin, final Vec3 axis, final MagneticPolarity polarity,
                         final MagneticStrength strength, final Shape shape,
                         final double customRange) {
        this(origin, axis, polarity, strength, shape, customRange, 0.0d);
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

    /** Force magnitude in Newtons: the analog override when one is set, otherwise the
     *  strength tier's nominal force. Mirrors {@link #range()}'s override-or-tier shape.
     *
     *  <p>Note that {@code strength} still gates whether the field is <em>live</em> at all —
     *  {@code FieldApplicator} early-outs on a {@link MagneticStrength#NONE} tier regardless
     *  of this value, so a hematite block that damps an emitter all the way to NONE silences
     *  it even when an analog override is set. */
    public double force() {
        return forceOverride > 0 ? forceOverride : strength.force();
    }

    /** True when an analog redstone level is driving this field rather than its tier. */
    public boolean hasForceOverride() {
        return forceOverride > 0;
    }

    /** Copy carrying a different strength tier, preserving both overrides verbatim. */
    public MagneticField withStrength(final MagneticStrength newStrength) {
        return new MagneticField(origin, axis, polarity, newStrength, shape, customRange, forceOverride);
    }

    /**
     * Copy at a new tier for a modifier that <em>steps</em> the ladder — hematite
     * dampening, Halbach boosting — scaling any analog force override by the same ratio
     * the tier moved.
     *
     * <p>Without this, those modifiers would silently become range-only for an
     * analog-driven emitter: they step {@code strength}, but {@link #force()} reads the
     * override instead. Scaling proportionally keeps a hematite block damping and a
     * Halbach array concentrating whichever way the emitter is driven. A step down to
     * {@link MagneticStrength#NONE} zeroes the override, so hematite can still silence
     * an emitter outright.
     */
    public MagneticField withSteppedStrength(final MagneticStrength newStrength) {
        if (forceOverride <= 0) return withStrength(newStrength);
        final double from = strength.force();
        final double scaled = from > 0 ? forceOverride * (newStrength.force() / from) : 0.0d;
        return new MagneticField(origin, axis, polarity, newStrength, shape, customRange, scaled);
    }

    /** Copy carrying a different polarity, preserving both overrides. */
    public MagneticField withPolarity(final MagneticPolarity newPolarity) {
        return new MagneticField(origin, axis, newPolarity, strength, shape, customRange, forceOverride);
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
        if (forceOverride > 0) tag.putDouble("fo", forceOverride);
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
                    tag.contains("cr") ? tag.getDouble("cr") : 0.0d,
                    tag.contains("fo") ? tag.getDouble("fo") : 0.0d);
        } catch (final Throwable t) {
            return null;
        }
    }
}
