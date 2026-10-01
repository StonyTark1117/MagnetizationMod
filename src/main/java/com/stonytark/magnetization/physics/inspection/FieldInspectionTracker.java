package com.stonytark.magnetization.physics.inspection;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagneticField;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3d;
import java.util.*;

/** Records actual successful physics injections, only for ships currently inspected with goggles. */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class FieldInspectionTracker {
    public record Contribution(Vec3 origin, Vec3 requested, Vec3 applied, Vec3 torque, boolean capped) {}
    public record Snapshot(long tick, Vec3 force, Vec3 torque, List<Contribution> sources, int omitted, int limited) {}
    private static final Map<ServerLevel, Map<UUID, Trace>> LEVELS = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final int MAX_SOURCES = 128;
    private static final class Trace {
        long expires, tick = Long.MIN_VALUE;
        final List<MutableContribution> sources = new ArrayList<>();
        Vec3 force = Vec3.ZERO, torque = Vec3.ZERO;
        int omitted, limited;
        Snapshot complete = empty(-1);
        void advance(long now) {
            if (tick == now) return;
            complete = tick == now - 1 ? snapshot() : empty(now - 1);
            sources.clear(); force = torque = Vec3.ZERO; omitted = limited = 0; tick = now;
        }
        Snapshot snapshot() {
            return new Snapshot(tick, force, torque, sources.stream().map(MutableContribution::snapshot).toList(), omitted, limited);
        }
    }
    public static final class MutableContribution {
        private final Trace trace;
        private final Vec3 origin, center;
        private Vec3 requested = Vec3.ZERO, applied = Vec3.ZERO, torque = Vec3.ZERO;
        private final boolean capped;
        private MutableContribution(Trace trace, Vec3 origin, Vec3 center, boolean capped) {
            this.trace = trace; this.origin = origin; this.center = center; this.capped = capped;
        }
        public void applied(Vec3 point, Vec3 force) {
            Vec3 turn = point.subtract(center).cross(force);
            applied = applied.add(force); torque = torque.add(turn);
            trace.force = trace.force.add(force); trace.torque = trace.torque.add(turn);
        }
        private Contribution snapshot() { return new Contribution(origin, requested, applied, torque, capped); }
    }
    private FieldInspectionTracker() {}
    public static void watch(ServerLevel level, UUID ship) {
        Trace trace = LEVELS.computeIfAbsent(level, k -> new HashMap<>()).computeIfAbsent(ship, k -> new Trace());
        trace.expires = level.getGameTime() + 20;
    }
    public static MutableContribution begin(ServerLevel level, ServerSubLevel ship, MagneticField field,
                                            List<Vec3> requestedForces, double scale) {
        Map<UUID, Trace> traces = LEVELS.get(level);
        Trace trace = traces == null ? null : traces.get(ship.getUniqueId());
        if (trace == null || trace.expires < level.getGameTime()) return null;
        trace.advance(level.getGameTime());
        Vector3d center = new Vector3d(ship.getMassTracker().getCenterOfMass());
        ship.logicalPose().transformPosition(center);
        MutableContribution contribution = new MutableContribution(trace, field.origin(), new Vec3(center.x, center.y, center.z), scale < 1 - 1e-9);
        for (Vec3 force : requestedForces) contribution.requested = contribution.requested.add(force);
        if (contribution.capped) trace.limited++;
        if (trace.sources.size() < MAX_SOURCES) trace.sources.add(contribution); else trace.omitted++;
        return contribution;
    }
    public static Snapshot latest(ServerLevel level, UUID ship) {
        var traces = LEVELS.get(level);
        var trace = traces == null ? null : traces.get(ship);
        if (trace == null) return empty(level.getGameTime() - 1);
        trace.advance(level.getGameTime());
        return trace.complete;
    }
    private static Snapshot empty(long tick) { return new Snapshot(tick, Vec3.ZERO, Vec3.ZERO, List.of(), 0, 0); }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % 20 == 0) {
            var traces = LEVELS.get(level);
            if (traces != null) traces.values().removeIf(trace -> trace.expires < level.getGameTime());
        }
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) { LEVELS.remove(event.getLevel()); }
}
