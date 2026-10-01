package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.compat.SlugterraProjectileCompat;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.physics.FieldApplicator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Focused checks for both projectile movement protocols in the installed port. */
@GameTestHolder("magnetization_slugterra_performance")
@PrefixGameTestTemplate(false)
public final class SlugterraPerformanceGameTests {
    private SlugterraPerformanceGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void cachedTypeKeepsFlightStateLiveAndOrdinaryItemForces(GameTestHelper h) {
        final var level = h.getLevel();
        final Vec3 origin = Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 110, 1)));
        final boolean enabled = MagConfig.SLUGTERRA_DEFLECTION_ENABLED.get();
        try {
            MagConfig.SLUGTERRA_DEFLECTION_ENABLED.set(true);
            for (String path : new String[]{"armashelt_velocimorph", "rammstone_velocimorph"}) {
                final ResourceLocation id = ResourceLocation.fromNamespaceAndPath("slugterra", path);
                h.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(id), "Missing tested protocol: " + id);
                final var shot = BuiltInRegistries.ENTITY_TYPE.get(id).create(level);
                h.assertTrue(shot != null, "Could not create " + id);
                try {
                    shot.setPos(origin);
                    shot.setNoGravity(true);
                    shot.setDeltaMovement(new Vec3(1, 0, 0));
                    shot.getPersistentData().putDouble("v_x", 1);
                    shot.getPersistentData().putDouble("v_y", 0);
                    shot.getPersistentData().putDouble("v_z", 0);
                    level.addFreshEntity(shot);
                    h.assertTrue(SlugterraProjectileCompat.isInFlight(shot), "Protocol is not in flight: " + id);
                    final var field = field(origin.add(0, shot.getBbHeight() * 0.5, -2));
                    FieldApplicator.applyEntitiesOnly(level, field);
                    final Vec3 bent = shot.getDeltaMovement();
                    h.assertTrue(Math.abs(bent.z) > 1e-5 && Math.abs(bent.length() - 1) < 1e-9,
                            "Projectile must turn without gaining speed: " + id);
                    // Same callback/tick: the cached target remains present but is no longer in flight.
                    shot.getPersistentData().putBoolean("Transformed", true);
                    h.assertTrue(!SlugterraProjectileCompat.isInFlight(shot), "Stopped protocol remains in flight");
                    FieldApplicator.applyEntitiesOnly(level, field);
                    h.assertTrue(shot.getDeltaMovement().equals(bent), "Cached type bypassed live flight state: " + id);
                } finally {
                    shot.discard();
                }
            }
            final var item = new ItemEntity(level, origin.x, origin.y, origin.z, new ItemStack(Items.IRON_INGOT));
            try {
                item.setNoGravity(true);
                item.setDeltaMovement(Vec3.ZERO);
                level.addFreshEntity(item);
                h.assertTrue(!SlugterraProjectileCompat.supportsType(item), "Ordinary item classified as projectile");
                FieldApplicator.applyEntitiesOnly(level, field(origin.add(0, 0, -2)));
                h.assertTrue(item.getDeltaMovement().lengthSqr() > 1e-8, "Ordinary item lost magnetic force");
            } finally {
                item.discard();
            }
            h.succeed();
        } finally {
            MagConfig.SLUGTERRA_DEFLECTION_ENABLED.set(enabled);
        }
    }

    private static MagneticField field(Vec3 origin) {
        return new MagneticField(origin, new Vec3(0, 0, 1), MagneticPolarity.NORTH,
                MagneticStrength.EXTREME, MagneticField.Shape.OMNIDIRECTIONAL, 4, 100000);
    }
}
