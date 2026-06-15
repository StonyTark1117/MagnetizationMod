package com.stonytark.magnetization.content.fluid;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.physics.MagneticFields;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.List;

/**
 * Lorentz current for gallium. An electric current (here, a redstone signal)
 * passing through liquid gallium inside a magnetic field experiences the Lorentz
 * force, which sets the metal moving — the principle behind MHD pumps. So when a
 * gallium cell is both carrying a signal ({@link FluidRedstone#signal} &gt; 0) and
 * sitting in an emitter field, it pushes entities floating in it (boats, players,
 * mobs, items) like a water current: outward from the field origin for a NORTH
 * field, inward for SOUTH. This is an entity push only — the fluid blocks don't
 * move (unlike ferrofluid's creep).
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class GalliumLorentzHandler {

    /** Peak per-tick velocity nudge, at full signal strength. */
    private static final double SPEED = 0.09;
    /** Run every other tick — the push is cumulative so this stays smooth. */
    private static final int INTERVAL = 2;

    private GalliumLorentzHandler() {}

    @SubscribeEvent
    public static void onLevelTick(final LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.getGameTime() % INTERVAL != 0L) return;

        for (final BlockPos pos : GalliumRegistry.snapshot(level)) {
            final BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof GalliumBlock || state.getBlock() instanceof MixedGalliumBlock)) {
                GalliumRegistry.remove(level, pos);
                continue;
            }
            final int power = FluidRedstone.signal(state);
            if (power <= 0) continue; // no current → no Lorentz force

            final Vec3 center = Vec3.atCenterOf(pos);
            final MagneticField field = MagneticFields.nearestField(level, center);
            if (field == null) continue; // no field → no force

            // Horizontal radial direction from the field origin, signed by polarity.
            final Vec3 radial = center.subtract(field.origin());
            final Vec3 flat = new Vec3(radial.x, 0.0, radial.z);
            if (flat.lengthSqr() < 1.0e-4) continue; // directly above/below the source
            final Vec3 dir = flat.normalize().scale(field.polarity().sign());
            final double mag = SPEED * (power / 15.0);

            final AABB box = new AABB(pos);
            final List<Entity> entities = level.getEntities((Entity) null, box, e -> true);
            for (final Entity ent : entities) {
                ent.setDeltaMovement(ent.getDeltaMovement().add(dir.scale(mag)));
                ent.hurtMarked = true; // force a velocity sync to the client
            }
        }
    }
}
