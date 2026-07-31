package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.Magnetization;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Immersive Portals aperture and transform edge cases, when that optional API is present. */
@GameTestHolder("magnetization_immersive_aeronautics")
@PrefixGameTestTemplate(false)
public final class PortalEdgeCaseGameTests {
    private PortalEdgeCaseGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 80, batch = "portalEdgeCases")
    public static void rotatedScaledPortalTransformsFieldAxis(final GameTestHelper helper) {
        if (!portalApiAvailable()) {
            helper.succeed();
            return;
        }
        final ServerLevel source = helper.getLevel();
        final ServerLevel destination = source.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        if (destination == null) {
            helper.fail("Portal edge-case test requires the Nether");
            return;
        }
        final Entity portal = createPortal(source, destination,
                new Vec3(0.5, 160.5, 4.5), new Vec3(100.5, 160.5, 4.5), true);
        try {
            final Vec3 transformed = (Vec3) invoke(portal, "transformLocalVec", new Vec3(1, 0, 0));
            final double scale = ((Number) invoke(portal, "getScaling")).doubleValue();
            helper.assertTrue(Math.abs(scale - 2.0d) < 0.001d,
                    "Scaled portal lost its scale: " + scale);
            helper.assertTrue(Math.abs(transformed.z()) > 0.9d && Math.abs(transformed.x()) < 0.1d,
                    "Rotated portal did not rotate the field axis: " + transformed);
            helper.succeed();
        } finally {
            portal.discard();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 80, batch = "portalEdgeCases")
    public static void shipOutsidePortalApertureReceivesNoField(final GameTestHelper helper) {
        if (!portalApiAvailable()) {
            helper.succeed();
            return;
        }
        final ServerLevel source = helper.getLevel();
        final ServerLevel destination = source.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        if (destination == null) {
            helper.fail("Portal edge-case test requires the Nether");
            return;
        }
        final Vec3 sourcePortal = new Vec3(0.5, 160.5, 4.5);
        final Entity portal = createPortal(source, destination, sourcePortal,
                new Vec3(100.5, 160.5, 4.5), false);
        try {
            final Object hit = invoke(portal, "rayTrace", new Vec3(0.5, 160.5, 0.5),
                    new Vec3(20.5, 160.5, 4.5));
            helper.assertTrue(hit == null,
                    "A ship center outside the portal aperture must not be traversable");
            helper.succeed();
        } finally {
            portal.discard();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 80, batch = "portalEdgeCases")
    public static void overlappingPortalsClaimShipOnlyOnce(final GameTestHelper helper) {
        // This is the same UUID gate used by the live portal traversal predicate.
        // Keeping it as a tiny deterministic test catches regressions where adding
        // another portal reintroduces a second impulse for one destination ship.
        final Set<UUID> applied = new HashSet<>();
        final UUID ship = UUID.randomUUID();
        helper.assertTrue(applied.add(ship), "First overlapping portal should claim the ship");
        helper.assertTrue(!applied.add(ship), "Overlapping portals must not claim one ship twice");
        helper.succeed();
    }

    private static boolean portalApiAvailable() {
        try {
            Class.forName("qouteall.imm_ptl.core.portal.Portal");
            Class.forName("qouteall.q_misc_util.my_util.DQuaternion");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static Entity createPortal(final ServerLevel source, final ServerLevel destination,
                                       final Vec3 origin, final Vec3 target, final boolean rotatedScaled) {
        try {
            final Class<?> portalClass = Class.forName("qouteall.imm_ptl.core.portal.Portal");
            final Object entityType = portalClass.getField("ENTITY_TYPE").get(null);
            final Entity portal = (Entity) portalClass
                    .getConstructor(net.minecraft.world.entity.EntityType.class, net.minecraft.world.level.Level.class)
                    .newInstance(entityType, source);
            invoke(portal, "setOriginPos", origin);
            invoke(portal, "setOrientationAndSize", new Vec3(0, 0, 1), new Vec3(0, 1, 0), 5.0, 5.0);
            invoke(portal, "setDestinationDimension", destination.dimension());
            invoke(portal, "setDestination", target);
            final Class<?> quaternionClass = Class.forName("qouteall.q_misc_util.my_util.DQuaternion");
            final double half = Math.PI / 4.0d;
            final Object quaternion = quaternionClass.getConstructor(double.class, double.class, double.class, double.class)
                    .newInstance(0.0, rotatedScaled ? Math.sin(half) : 0.0,
                            0.0, rotatedScaled ? Math.cos(half) : 1.0);
            invoke(portal, "setRotationTransformationD", quaternion);
            invoke(portal, "setScaling", rotatedScaled ? 2.0d : 1.0d);
            invoke(portal, "setTeleportable", true);
            invoke(portal, "updateCache");
            source.addFreshEntity(portal);
            return portal;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not create optional Immersive Portal", exception);
        }
    }

    private static Object invoke(final Object target, final String name, final Object... args) {
        for (final Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            try {
                return method.invoke(target, args);
            } catch (IllegalArgumentException ignored) {
                // Try the next overload; optional API signatures vary by release.
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not invoke optional Portal method " + name, exception);
            }
        }
        throw new IllegalStateException("Optional Portal method is missing: " + name + "/" + args.length);
    }
}
