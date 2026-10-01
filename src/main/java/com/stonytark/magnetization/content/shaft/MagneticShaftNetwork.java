package com.stonytark.magnetization.content.shaft;

import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.mixin.MagneticShaftRotationAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.*;
import static com.stonytark.magnetization.content.shaft.MagneticShaftBlockEntity.Status.*;

/**
 * Wireless edges join real Create networks. No generated RPM or capacity is invented.
 * Discovery deliberately removes wireless edges so a received drive can never retransmit.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class MagneticShaftNetwork {
    private static final Map<Level, State> LEVELS = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Boolean> PHYSICAL_ONLY = ThreadLocal.withInitial(() -> false);
    private static final int MAX_COMPONENT = 16384;
    private static final class State {
        long lastUpdate = Long.MIN_VALUE;
        final Set<BlockPos> shafts = new HashSet<>();
        final Map<BlockPos, BlockPos> links = new HashMap<>(); // receiver -> source
    }
    private static final class Component {
        final Map<KineticBlockEntity, Double> members = new LinkedHashMap<>();
        final List<MagneticShaftBlockEntity> shafts = new ArrayList<>();
        boolean driven, invalid;
    }
    private record Candidate(MagneticShaftBlockEntity receiver, MagneticShaftBlockEntity source,
                             double referenceRpm, double distance) {}
    private MagneticShaftNetwork() {}
    public static void register(MagneticShaftBlockEntity shaft) {
        if (shaft.getLevel() != null) LEVELS.computeIfAbsent(shaft.getLevel(), k -> new State()).shafts.add(shaft.getBlockPos());
    }
    public static List<MagneticShaftBlockEntity> loadedShafts(Level level) {
        State state = LEVELS.get(level);
        if (state == null) return List.of();
        return state.shafts.stream().map(pos -> loaded(level, pos)).filter(MagneticShaftBlockEntity.class::isInstance)
                .map(MagneticShaftBlockEntity.class::cast).toList();
    }
    public static void unregister(MagneticShaftBlockEntity shaft) {
        State state = LEVELS.get(shaft.getLevel());
        if (state != null) state.shafts.remove(shaft.getBlockPos());
        // Keep links until the end-of-tick pass: Create's removal traversal still needs them.
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) { LEVELS.remove(event.getLevel()); }
    public static void addNeighbours(MagneticShaftBlockEntity shaft, List<BlockPos> neighbours) {
        if (PHYSICAL_ONLY.get()) return;
        State state = LEVELS.get(shaft.getLevel());
        if (state == null) return;
        state.links.forEach((receiver, source) -> {
            BlockPos other = receiver.equals(shaft.getBlockPos()) ? source : source.equals(shaft.getBlockPos()) ? receiver : null;
            if (other != null && shaft.getLevel().hasChunkAt(other) && !neighbours.contains(other)) neighbours.add(other);
        });
    }
    /** Null means ordinary Create connection. A wireless edge is directional to prevent backfeed. */
    public static Float connectionRatio(KineticBlockEntity from, KineticBlockEntity to) {
        if (!(from instanceof MagneticShaftBlockEntity a) || !(to instanceof MagneticShaftBlockEntity b)) return null;
        State state = LEVELS.get(from.getLevel());
        if (state == null) return null;
        boolean forward = from.getBlockPos().equals(state.links.get(to.getBlockPos()));
        boolean reverse = to.getBlockPos().equals(state.links.get(from.getBlockPos()));
        if (!forward && !reverse) return null;
        if (PHYSICAL_ONLY.get()) return 0f;
        if (!forward || a.worldCenter().distanceToSqr(b.worldCenter()) > (double) a.transmissionRange() * a.transmissionRange()) return 0f;
        // A newly attached local motor wins immediately, even before the next manager pass.
        if (discover(b).driven) return 0f;
        // Disconnect/reconnect in the manager before a reversal reaches Create's destructive conflict path.
        if (b.getTheoreticalSpeed() != 0 && Math.abs(b.getTheoreticalSpeed() - a.getTheoreticalSpeed()) > 0.001f) return 0f;
        return 1f;
    }
    private static KineticBlockEntity loaded(Level level, BlockPos pos) {
        // Level#getBlockEntity calls getChunk(..., true), refreshing an UNKNOWN ticket
        // even after a hasChunkAt guard. Poll only an existing full chunk so discovery
        // cannot keep a remote source loaded by looking at it every tick.
        var chunk = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4,
                net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
        return chunk instanceof net.minecraft.world.level.chunk.LevelChunk full
                && full.getBlockEntity(pos) instanceof KineticBlockEntity kinetic && !kinetic.isRemoved() ? kinetic : null;
    }
    private static Component discover(KineticBlockEntity start) {
        boolean old = PHYSICAL_ONLY.get();
        PHYSICAL_ONLY.set(true);
        try {
            Component component = new Component();
            var queue = new ArrayDeque<KineticBlockEntity>();
            component.members.put(start, 1d); queue.add(start);
            while (!queue.isEmpty()) {
                KineticBlockEntity current = queue.remove();
                if (current.isSource()) component.driven = true;
                if (current instanceof MagneticShaftBlockEntity shaft) component.shafts.add(shaft);
                for (BlockPos pos : MagneticShaftRotationAccess.magnetization$locations(current)) {
                    KineticBlockEntity next = loaded(current.getLevel(), pos);
                    if (next == null || component.members.containsKey(next)) continue;
                    if (!RotationPropagator.isConnected(current, next) && !RotationPropagator.isConnected(next, current)) continue;
                    float ratio = MagneticShaftRotationAccess.magnetization$ratio(current, next);
                    if (ratio == 0) {
                        float reverse = MagneticShaftRotationAccess.magnetization$ratio(next, current);
                        ratio = reverse == 0 ? 1 : 1 / reverse;
                    }
                    component.members.put(next, component.members.get(current) * ratio);
                    if (component.members.size() > MAX_COMPONENT) { component.invalid = true; return component; }
                    queue.add(next);
                }
            }
            return component;
        } finally { PHYSICAL_ONLY.set(old); }
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) update(level);
    }
    public static void update(ServerLevel level) {
        State state = LEVELS.get(level);
        if (state == null) return;
        long now = level.getGameTime();
        // Native kinetic hooks (including Simulated's extra connections) may refresh
        // short-lived UNKNOWN chunk tickets while examining blocks. Leave a gap
        // longer than their expiry so inspection itself cannot keep a source loaded.
        if (state.lastUpdate != Long.MIN_VALUE && now >= state.lastUpdate && now - state.lastUpdate < 4) return;
        state.lastUpdate = now;
        List<MagneticShaftBlockEntity> shafts = state.shafts.stream().sorted()
                .map(pos -> loaded(level, pos)).filter(MagneticShaftBlockEntity.class::isInstance)
                .map(MagneticShaftBlockEntity.class::cast).toList();
        // A ticket can temporarily drop below FULL while a chunk is still in memory.
        // Keep that position until the block entity's actual unload/removal event.
        state.shafts.removeIf(pos -> loaded(level, pos) != null && !(loaded(level, pos) instanceof MagneticShaftBlockEntity));
        Map<KineticBlockEntity, Component> components = new HashMap<>();
        for (var shaft : shafts) if (!components.containsKey(shaft)) {
            var component = discover(shaft);
            component.members.keySet().forEach(be -> components.put(be, component));
        }
        Map<BlockPos, BlockPos> desired = new HashMap<>();
        Set<MagneticShaftBlockEntity> conflicts = new HashSet<>();
        for (Component target : new HashSet<>(components.values())) {
            if (target.invalid) { conflicts.addAll(target.shafts); continue; }
            if (target.driven) continue;
            List<Candidate> candidates = new ArrayList<>();
            for (var receiver : target.shafts) {
                if (MagConfig.isBlockDisabled(receiver.getBlockState())) continue;
                double receiverRatio = target.members.get(receiver);
                for (var source : shafts) {
                    Component drive = components.get(source);
                    if (drive == target || drive.invalid || !drive.driven || source.getTheoreticalSpeed() == 0
                            || MagConfig.isBlockDisabled(source.getBlockState())) continue;
                    double distance = receiver.worldCenter().distanceToSqr(source.worldCenter());
                    if (distance > (double) source.transmissionRange() * source.transmissionRange()) continue;
                    candidates.add(new Candidate(receiver, source, source.getTheoreticalSpeed() / receiverRatio, distance));
                }
            }
            if (candidates.isEmpty()) continue;
            double rpm = candidates.getFirst().referenceRpm();
            if (candidates.stream().anyMatch(c -> !Double.isFinite(c.referenceRpm()) || Math.abs(c.referenceRpm() - rpm) > .001)) {
                conflicts.addAll(target.shafts);
                continue;
            }
            // Existing valid binding wins; otherwise nearest pair, then stable block coordinates.
            candidates.sort(Comparator.<Candidate>comparingInt(c -> c.source().getBlockPos().equals(state.links.get(c.receiver().getBlockPos())) ? 0 : 1)
                    .thenComparingDouble(Candidate::distance).thenComparing(c -> c.source().getBlockPos()).thenComparing(c -> c.receiver().getBlockPos()));
            Candidate chosen = candidates.getFirst();
            desired.put(chosen.receiver().getBlockPos(), chosen.source().getBlockPos());
        }
        // Remove invalid/changed links first. No temporary state can feed itself back.
        for (var entry : new ArrayList<>(state.links.entrySet())) {
            KineticBlockEntity receiver = loaded(level, entry.getKey());
            KineticBlockEntity source = loaded(level, entry.getValue());
            boolean changed = !Objects.equals(desired.get(entry.getKey()), entry.getValue()) || source == null || receiver == null
                    || Math.abs(source.getTheoreticalSpeed() - receiver.getTheoreticalSpeed()) > .001;
            if (!changed) continue;
            state.links.remove(entry.getKey());
            if (receiver != null && entry.getValue().equals(receiver.source)) MagneticShaftRotationAccess.magnetization$disconnect(receiver);
        }
        for (var entry : desired.entrySet()) {
            if (state.links.containsKey(entry.getKey())) continue;
            KineticBlockEntity source = loaded(level, entry.getValue());
            KineticBlockEntity receiver = loaded(level, entry.getKey());
            if (source == null || receiver == null) continue;
            state.links.put(entry.getKey(), entry.getValue());
            source.attachKinetics();
        }
        for (var shaft : shafts) {
            Component component = components.get(shaft);
            BlockPos transmitter = component.shafts.stream().map(be -> state.links.get(be.getBlockPos())).filter(Objects::nonNull).findFirst().orElse(null);
            var status = MagConfig.isBlockDisabled(shaft.getBlockState()) ? IDLE : conflicts.contains(shaft) ? CONFLICT : shaft.isOverStressed() ? OVERSTRESSED
                    : component.driven && shaft.getTheoreticalSpeed() != 0 ? SOURCE : transmitter != null ? RECEIVING : IDLE;
            shaft.display(status, transmitter);
        }
        if (state.shafts.isEmpty() && state.links.isEmpty()) LEVELS.remove(level);
    }
}
