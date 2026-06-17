package com.stonytark.magnetization.content.effect;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.physics.ShipMagneticRegistry;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Lenz-effect eddy-current braking. A magnetic Sable ship moving over conductive
 * non-ferrous blocks ({@code #magnetization:eddy_conductors} — copper, aluminium,
 * …) induces opposing eddy currents that drag it to a near-stop: it floats/slides
 * slowly instead of zipping through, exactly like a magnet falling through a
 * copper pipe. Linear-velocity drag only (no attraction); scaled by how many
 * conductor blocks the ship overlaps and the configured strength.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class LenzBrakingHandler {

    private static final int SAMPLE_CAP = 160;        // hard cap on blocks examined per ship
    private static final int BELOW_REACH = 3;         // blocks below the hull to scan for a pad it flies over

    private LenzBrakingHandler() {}

    @SubscribeEvent
    public static void onLevelTick(final LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        if ((server.getGameTime() % MagConfig.lenzBrakingTicks()) != 0L) return;
        final double strength = MagConfig.lenzBrakingStrength();
        if (strength <= 0.0d) return;
        final SubLevelContainer container = SubLevelContainer.getContainer(server);
        if (container == null) return;

        for (final SubLevel sub : container.getAllSubLevels()) {
            if (!(sub instanceof ServerSubLevel ship)) continue;
            if (ship.getMassTracker().isInvalid() || ship.getMassTracker().getMass() <= 0.0) continue;
            // Only magnetic ships are braked — a plain stone ship induces nothing.
            if (ShipMagneticRegistry.get(server, ship).susceptibility() <= 0.0) continue;

            final RigidBodyHandle handle = RigidBodyHandle.of(ship);
            if (handle == null || !handle.isValid()) continue;
            final Vector3dc vel = handle.getLinearVelocity();
            final double speed = Math.sqrt(vel.x() * vel.x() + vel.y() * vel.y() + vel.z() * vel.z());
            if (speed < MagConfig.lenzMinSpeed()) continue;

            final int conductors = countOverlappingConductors(server, ship.boundingBox());
            if (conductors <= 0) continue;

            final int conductorCap = MagConfig.lenzConductorCap();
            final double factor = (double) Math.min(conductors, conductorCap) / conductorCap;
            final double drag = Math.min(MagConfig.lenzMaxDrag(), MagConfig.lenzBaseDrag() * factor * strength);
            // Subtract a fraction of the current velocity — opposes motion in every axis,
            // so a descending ship floats down slowly and a sliding one coasts to rest.
            handle.addLinearAndAngularVelocity(
                    new Vector3d(-vel.x() * drag, -vel.y() * drag, -vel.z() * drag),
                    new Vector3d(0, 0, 0));
        }
    }

    /**
     * Count conductor blocks in (and just around) the ship's bounding box, capped.
     * Scans {@link #BELOW_REACH} blocks below the hull so a ship flying over a
     * ground conductor pad still induces eddy braking. Public for GameTest
     * verification of that below-hull reach ({@code lenzCountsConductorPadBelowHull}).
     */
    public static int countOverlappingConductors(final ServerLevel level, final BoundingBox3dc bb) {
        final int minX = (int) Math.floor(bb.minX()) - 1;
        final int minY = (int) Math.floor(bb.minY()) - BELOW_REACH;
        final int minZ = (int) Math.floor(bb.minZ()) - 1;
        final int maxX = (int) Math.ceil(bb.maxX()) + 1;
        final int maxY = (int) Math.ceil(bb.maxY()) + 1;
        final int maxZ = (int) Math.ceil(bb.maxZ()) + 1;
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int count = 0;
        int examined = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (++examined > SAMPLE_CAP) return count;
                    cursor.set(x, y, z);
                    if (!level.isLoaded(cursor)) continue;
                    if (level.getBlockState(cursor).is(MagTags.EDDY_CONDUCTORS)) count++;
                }
            }
        }
        return count;
    }
}
