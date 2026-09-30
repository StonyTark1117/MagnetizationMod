package com.stonytark.magnetization.compat;

import net.minecraft.world.phys.Vec3;

/** Bounds only additional magnetic acceleration; existing native flight is never forcibly slowed. */
public final class AircraftImpulseLimiter {
    private AircraftImpulseLimiter() {}

    public static Vec3 addImpulse(final Vec3 velocity, final Vec3 impulse, final double limit) {
        final Vec3 candidate = velocity.add(impulse);
        final double maximumSquared = Math.max(limit * limit, velocity.lengthSqr());
        if (candidate.lengthSqr() <= maximumSquared) return candidate;
        final double a = impulse.lengthSqr();
        if (a < 1.0e-20) return velocity;
        // Intersect v + t*impulse with the allowed speed sphere, choosing the
        // forward exit. This keeps force direction and permits braking/reversal.
        final double b = 2 * velocity.dot(impulse);
        final double c = velocity.lengthSqr() - maximumSquared;
        final double root = Math.sqrt(Math.max(0, b * b - 4 * a * c));
        final double fraction = b >= 0 ? (b + root == 0 ? 0 : -2 * c / (b + root)) : (-b + root) / (2 * a);
        return velocity.add(impulse.scale(Math.clamp(fraction, 0, 1)));
    }
}
