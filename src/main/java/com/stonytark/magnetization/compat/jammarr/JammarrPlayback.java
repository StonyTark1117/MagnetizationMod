package com.stonytark.magnetization.compat.jammarr;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Server-thread-owned adapter for the verified Jammarr 1.1.0 coordinator. */
final class JammarrPlayback {
    enum Result { STARTED, BUSY, COOLDOWN, NOT_FOUND, UNAVAILABLE, CANCELLED, FAILED }
    private static final long COOLDOWN_NANOS = Duration.ofSeconds(10).toNanos();
    private static final int PAGE_SIZE = 100;
    // Bound work against a broken/repeating library response; never choose a partial match set.
    private static final int MAX_PAGES = 1000;
    private final Object coordinator;
    private final JammarrAccess core, store, gateway, page, item, track, executor, runtime;
    private final JammarrAccess settings, cache, prepared, asset, timeline, station;
    private final Object search, trackKind, manual;
    private final LongSupplier clock;
    private CompletableFuture<Result> pending;
    private boolean closed;
    private long lastRequest;
    private boolean requested;

    JammarrPlayback(Object coordinator) throws ReflectiveOperationException {
        this(coordinator, System::nanoTime);
    }

    JammarrPlayback(Object coordinator, LongSupplier clock) throws ReflectiveOperationException {
        this.coordinator = coordinator;
        this.clock = clock;
        ClassLoader loader = coordinator.getClass().getClassLoader();
        track = new JammarrAccess(loader, "core.model.QueueTrack")
                .method("key").method("title").method("artist");
        JammarrAccess origin = new JammarrAccess(loader, "core.protocol.StatePackets$PlaybackOrigin");
        JammarrAccess browseKind = new JammarrAccess(loader, "core.protocol.ControlPackets$BrowseKind");
        JammarrAccess itemKind = new JammarrAccess(loader, "core.model.StationModels$ItemKind");
        search = browseKind.constant("SEARCH"); trackKind = itemKind.constant("TRACK"); manual = origin.constant("MANUAL");
        store = new JammarrAccess(loader, "core.server.PlaybackStore")
                .method("current").method("station").method("autoplayEnabled").method("queue")
                .method("remember", track.type).method("update", long.class, boolean.class)
                .method("current", track.type, origin.type, String.class);
        gateway = new JammarrAccess(loader, "core.server.PlexGateway")
                .method("browse", browseKind.type, String.class, int.class, int.class)
                .method("expand", itemKind.type, String.class, int.class);
        page = new JammarrAccess(loader, "core.server.PlexService$Page").method("items").method("hasMore");
        item = new JammarrAccess(loader, "core.model.StationModels$MediaItem").method("kind").method("key").method("title");
        executor = new JammarrAccess(loader, "core.server.BoundedWorkExecutor").method("supply", Supplier.class);
        runtime = new JammarrAccess(loader, "core.server.CoordinatorRuntime").method("execute", Runnable.class);
        settings = new JammarrAccess(loader, "core.platform.JammarrSettings").method("audioBitrateKbps");
        cache = new JammarrAccess(loader, "core.server.AudioCache").method("target", String.class, int.class);
        asset = new JammarrAccess(loader, "core.server.AudioAsset").method("path");
        prepared = new JammarrAccess(loader, "core.server.GlobalPlaybackCoordinator$PreparedAsset").field("asset");
        timeline = new JammarrAccess(loader, "core.server.PlaybackTimeline")
                .method("startedAtMs").method("pausedPositionMs").method("paused").method("active");
        station = new JammarrAccess(loader, "core.model.StationModels$StationDefinition").method("generation");
        core = new JammarrAccess(loader, "core.server.GlobalPlaybackCoordinator")
                .method("prepare", track.type, int.class, Set.class).method("pinnedPaths", Path.class)
                .method("stopAudio").method("activate", track.type, asset.type);
        for (String field : List.of("saved", "plex", "io", "runtime", "cache", "timeline", "closed",
                "playbackGeneration", "mutationGeneration", "plexHealth", "restorePositionMs", "restorePaused", "autoPaused")) {
            core.field(field);
        }
        if (!core.type.isInstance(coordinator)) throw new IllegalArgumentException("Unexpected Jammarr coordinator");
    }

    /** Called on the server thread. All asynchronous work uses Jammarr's bounded executor. */
    CompletableFuture<Result> request(BooleanSupplier authorized) {
        if (closed || isCoordinatorClosed() || !authorized.getAsBoolean()) return done(Result.CANCELLED);
        if (pending != null) return done(Result.BUSY);
        if (requested && clock.getAsLong() - lastRequest < COOLDOWN_NANOS) return done(Result.COOLDOWN);
        if (!"ONLINE".equals(core.get(coordinator, "plexHealth").toString())) return done(Result.UNAVAILABLE);
        Snapshot before = snapshot();
        int bitrate = (int) settings.call(null, "audioBitrateKbps");
        Set<Path> pins = new HashSet<>(paths(core.call(coordinator, "pinnedPaths", (Object) null)));
        Object plex = core.get(coordinator, "plex"), audioCache = core.get(coordinator, "cache");
        CompletableFuture<Result> answer = new CompletableFuture<>();
        pending = answer; requested = true; lastRequest = clock.getAsLong();
        Supplier<Ready> work = () -> {
            Object match = findTrack(plex);
            if (match == null) return null;
            Path target = (Path) cache.call(audioCache, "target", track.call(match, "key"), bitrate);
            pins.add(target);
            Object result = core.call(coordinator, "prepare", match, bitrate, Set.copyOf(pins));
            return new Ready(match, prepared.get(result, "asset"));
        };
        try {
            future(executor.call(core.get(coordinator, "io"), "supply", work)).whenComplete((ready, error) -> {
                if (isCoordinatorClosed()) { answer.complete(Result.CANCELLED); return; }
                runtime.call(core.get(coordinator, "runtime"), "execute", (Runnable) () -> {
                    try {
                        if (closed || isCoordinatorClosed() || !authorized.getAsBoolean() || !before.equals(snapshot())) {
                            answer.complete(Result.CANCELLED);
                        } else if (error != null) {
                            answer.complete(Result.FAILED);
                        } else if (ready == null) {
                            answer.complete(Result.NOT_FOUND);
                        } else {
                            switchTrack((Ready) ready);
                            answer.complete(Result.STARTED);
                        }
                    } catch (RuntimeException failure) {
                        // Never attempt a queue fallback after a possibly partial switch.
                        answer.complete(Result.FAILED);
                    } finally { if (pending == answer) pending = null; }
                });
            });
        } catch (RuntimeException failure) {
            pending = null; answer.complete(Result.FAILED);
        }
        return answer;
    }

    private Object findTrack(Object plex) {
        Object best = null;
        Set<String> visited = new HashSet<>();
        for (int number = 0; number < MAX_PAGES; number++) {
            if (isCoordinatorClosed()) throw new IllegalStateException("Jammarr stopped");
            Object response = gateway.call(plex, "browse", search, "Miracles", number, PAGE_SIZE);
            for (Object entry : (List<?>) page.call(response, "items")) {
                String key = (String) item.call(entry, "key");
                if (!trackKind.equals(item.call(entry, "kind")) || !matches("Miracles", item.call(entry, "title"))
                        || !visited.add(key)) continue;
                for (Object candidate : (List<?>) gateway.call(plex, "expand", trackKind, key, 1)) {
                    if (matches("Miracles", track.call(candidate, "title"))
                            && matches("Insane Clown Posse", track.call(candidate, "artist"))
                            && (best == null || ((String) track.call(candidate, "key")).compareTo((String) track.call(best, "key")) < 0)) {
                        best = candidate;
                    }
                }
            }
            if (!(boolean) page.call(response, "hasMore")) return best;
        }
        throw new IllegalStateException("Jammarr search exceeded page limit");
    }

    private void switchTrack(Ready ready) {
        // Another cache worker may have evicted the file before this server-thread callback.
        if (!Files.isRegularFile((Path) asset.call(ready.asset, "path"))) {
            throw new IllegalStateException("Prepared audio no longer cached");
        }
        Object saved = core.get(coordinator, "saved");
        Object interrupted = store.call(saved, "current");
        core.call(coordinator, "stopAudio");
        if (interrupted != null) store.call(saved, "remember", interrupted);
        store.call(saved, "current", ready.track, manual, "Manual request");
        store.call(saved, "update", 0L, false);
        core.set(coordinator, "restorePositionMs", 0L);
        core.set(coordinator, "restorePaused", false);
        core.set(coordinator, "autoPaused", false);
        core.call(coordinator, "activate", ready.track, ready.asset);
    }

    private Snapshot snapshot() {
        Object saved = core.get(coordinator, "saved"), time = core.get(coordinator, "timeline");
        // The Minecraft adapter constructs a fresh StationDefinition on every read.
        // Its generation, not object identity, identifies a station decision.
        return new Snapshot(store.call(saved, "current"), (long) station.call(store.call(saved, "station"), "generation"),
                (boolean) store.call(saved, "autoplayEnabled"), List.copyOf((List<?>) store.call(saved, "queue")),
                ((AtomicLong) core.get(coordinator, "playbackGeneration")).get(),
                ((AtomicLong) core.get(coordinator, "mutationGeneration")).get(),
                (long) timeline.call(time, "startedAtMs"), (long) timeline.call(time, "pausedPositionMs"),
                (boolean) timeline.call(time, "paused"), (boolean) timeline.call(time, "active"));
    }

    void close() { closed = true; if (pending != null) pending.complete(Result.CANCELLED); pending = null; }
    private boolean isCoordinatorClosed() { return ((AtomicBoolean) core.get(coordinator, "closed")).get(); }
    private static boolean matches(String expected, Object actual) { return actual instanceof String s && expected.equalsIgnoreCase(s.strip()); }
    private static CompletableFuture<Result> done(Result result) { return CompletableFuture.completedFuture(result); }
    @SuppressWarnings("unchecked") private static Set<Path> paths(Object value) { return (Set<Path>) value; }
    private static CompletableFuture<?> future(Object value) { return (CompletableFuture<?>) value; }
    private record Ready(Object track, Object asset) {}
    private record Snapshot(Object current, long stationGeneration, boolean autoplay, List<?> queue,
                            long playback, long mutation, long startedAt, long pausedPosition, boolean paused, boolean active) {}
}
