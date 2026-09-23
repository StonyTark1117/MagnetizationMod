package com.stonytark.magnetization.compat.jammarr;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import stonytark.jammarr.core.model.QueueTrack;
import stonytark.jammarr.core.model.StationModels;
import stonytark.jammarr.core.platform.CoreLogger;
import stonytark.jammarr.core.protocol.ControlPackets;
import stonytark.jammarr.core.protocol.StatePackets;
import stonytark.jammarr.core.protocol.TransportPackets;
import stonytark.jammarr.core.server.*;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Loads the core from the release JAR supplied to jammarrCompatTest, not a source substitute. */
class JammarrPlaybackTest {
    @TempDir Path temporary;

    @Test void commandOnlyExistsFor110() {
        for (String version : Arrays.asList(null, "1.0.0", "1.0.3", "1.1.1", "1.1.0")) {
            LiteralArgumentBuilder<CommandSourceStack> root = LiteralArgumentBuilder.literal("magnetization");
            JammarrCompat.register(root, version);
            assertEquals("1.1.0".equals(version), root.build().getChild("how") != null);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"idle", "playing", "paused", "preparing"})
    void immediatelySwitchesAndPreservesPendingQueueAndStation(String initial) throws Exception {
        try (Fixture f = new Fixture()) {
            if (initial.equals("preparing")) f.blockOld = true;
            if (!initial.equals("idle")) {
                f.coordinator.queue(f.operator, new ControlPackets.QueueRequest(StationModels.ItemKind.TRACK, "old"));
                f.await(() -> f.saved.current != null);
                if (!initial.equals("preparing")) f.await(() -> f.lastManifest() != null);
                else assertTrue(f.oldStarted.await(3, TimeUnit.SECONDS));
                if (initial.equals("paused")) f.control(ControlPackets.ControlAction.PAUSE);
            }
            f.saved.queue.add(song("next-a")); f.saved.queue.add(song("next-b"));
            f.saved.station = new StationModels.StationDefinition(StationModels.StationType.LIBRARY_SHUFFLE,
                    "Keep this station", List.of(), 12L);
            f.saved.autoplay = true;
            var station = f.saved.station;
            var answer = f.bridge.request(() -> true);
            f.await(answer::isDone);
            assertEquals(JammarrPlayback.Result.STARTED, answer.join());
            assertEquals("miracles", f.saved.current.key());
            assertEquals(List.of("next-a", "next-b"), f.saved.queue.stream().map(QueueTrack::key).toList());
            assertSame(station, f.saved.station); assertTrue(f.saved.autoplay);
            assertEquals(StatePackets.PlaybackOrigin.MANUAL, f.saved.origin);
            assertFalse(f.saved.paused); assertEquals(0L, f.saved.checkpoint);
            assertEquals(initial.equals("idle") ? List.of() : List.of("old"), f.saved.history.stream().map(QueueTrack::key).toList());
            var manifest = f.lastManifest();
            assertNotNull(manifest);
            assertTrue(manifest.startedAtEpochMs() >= System.currentTimeMillis() + 3500, "Retains Jammarr's sync buffer");
            assertTrue(f.sent.stream().filter(s -> s.message instanceof TransportPackets.AudioManifest m && m.sessionId().equals(manifest.sessionId()) && m.startedAtEpochMs() == manifest.startedAtEpochMs()).map(Sent::player).distinct().count() == 2);
            f.releaseOld.countDown();
            if (initial.equals("preparing")) {
                assertTrue(f.oldFinished.await(3, TimeUnit.SECONDS));
                f.pump();
                assertEquals("miracles", f.saved.current.key(), "Obsolete preparation cannot reactivate old track");
            }
            f.control(ControlPackets.ControlAction.SKIP);
            assertEquals("next-a", f.saved.current.key(), "Normal playback continues with the original queue");
        }
    }

    @Test void paginatesVerifiesArtistAndChoosesDeterministicDuplicate() throws Exception {
        try (Fixture f = new Fixture()) {
            f.pages = List.of(List.of(new QueueTrack("wrong", "Miracles", "Someone Else", "", 1000),
                            new QueueTrack("z", "  mIrAcLeS  ", " Insane Clown Posse ", "", 1000)),
                    List.of(new QueueTrack("a", "Miracles", "INSANE CLOWN POSSE", "", 1000)));
            var answer = f.bridge.request(() -> true); f.await(answer::isDone);
            assertEquals(JammarrPlayback.Result.STARTED, answer.join());
            assertEquals("a", f.saved.current.key());
            assertEquals(List.of(0, 1), f.browsedPages);
        }
    }

    @Test void missingAndWrongArtistDoNotInterrupt() throws Exception {
        try (Fixture f = new Fixture()) {
            f.startOld();
            f.pages = List.of(List.of(new QueueTrack("wrong", "Miracles", "Other", "", 1000)));
            var answer = f.bridge.request(() -> true); f.await(answer::isDone);
            assertEquals(JammarrPlayback.Result.NOT_FOUND, answer.join());
            assertEquals("old", f.saved.current.key()); assertTrue(f.saved.history.isEmpty());
        }
    }

    @Test void preparationFailureDoesNotInterruptOrQueue() throws Exception {
        try (Fixture f = new Fixture()) {
            f.startOld(); f.failPreparation = true;
            var answer = f.bridge.request(() -> true); f.await(answer::isDone);
            assertEquals(JammarrPlayback.Result.FAILED, answer.join());
            assertEquals("old", f.saved.current.key()); assertTrue(f.saved.queue.isEmpty());
        }
    }

    @Test void busyCooldownAndRevokedAuthorization() throws Exception {
        try (Fixture f = new Fixture()) {
            f.blockBrowse = true;
            AtomicBoolean allowed = new AtomicBoolean(true);
            var first = f.bridge.request(allowed::get);
            assertTrue(f.browseStarted.await(3, TimeUnit.SECONDS));
            assertEquals(JammarrPlayback.Result.BUSY, f.bridge.request(() -> true).join());
            allowed.set(false); f.releaseBrowse.countDown(); f.await(first::isDone);
            assertEquals(JammarrPlayback.Result.CANCELLED, first.join()); assertNull(f.saved.current);
            assertEquals(JammarrPlayback.Result.COOLDOWN, f.bridge.request(() -> true).join());
            f.clock.addAndGet(TimeUnit.SECONDS.toNanos(11));
            var retry = f.bridge.request(() -> true); f.await(retry::isDone);
            assertEquals(JammarrPlayback.Result.STARTED, retry.join());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"PAUSE", "SKIP", "CLEAR", "station", "queue", "disconnect"})
    void newerDecisionsCancelDelayedRequest(String change) throws Exception {
        try (Fixture f = new Fixture()) {
            f.startOld(); f.blockBrowse = true;
            AtomicBoolean connected = new AtomicBoolean(true);
            var answer = f.bridge.request(connected::get);
            assertTrue(f.browseStarted.await(3, TimeUnit.SECONDS));
            switch (change) {
                case "station" -> f.saved.station = StationModels.StationDefinition.none(9L);
                case "queue" -> f.saved.queue.add(song("new-request"));
                case "disconnect" -> connected.set(false);
                default -> f.control(ControlPackets.ControlAction.valueOf(change));
            }
            f.releaseBrowse.countDown(); f.await(answer::isDone);
            assertEquals(JammarrPlayback.Result.CANCELLED, answer.join());
            assertTrue(f.saved.current == null || !f.saved.current.key().equals("miracles"));
        }
    }

    @Test void closedCoordinatorAndOfflineLibraryDoNotMutatePlayback() throws Exception {
        try (Fixture f = new Fixture()) {
            f.startOld();
            var health = f.coordinator.getClass().getDeclaredField("plexHealth"); health.setAccessible(true);
            Object online = health.get(f.coordinator);
            health.set(f.coordinator, Arrays.stream(online.getClass().getEnumConstants()).filter(v -> v.toString().equals("OFFLINE")).findFirst().orElseThrow());
            assertEquals(JammarrPlayback.Result.UNAVAILABLE, f.bridge.request(() -> true).join());
            health.set(f.coordinator, online);
            f.blockBrowse = true; var answer = f.bridge.request(() -> true);
            assertTrue(f.browseStarted.await(3, TimeUnit.SECONDS));
            f.bridge.close(); f.coordinator.close(); f.releaseBrowse.countDown();
            assertEquals(JammarrPlayback.Result.CANCELLED, answer.join());
        }
    }

    private static QueueTrack song(String key) { return new QueueTrack(key, key.equals("miracles") ? "Miracles" : "Other", "Insane Clown Posse", "Album", 100_000L); }
    private record Sent(Object player, Object message) {}

    private final class Fixture implements AutoCloseable {
        final UUID operator = UUID.randomUUID(), listener = UUID.randomUUID();
        final Thread owner = Thread.currentThread();
        final Queue<Runnable> callbacks = new ConcurrentLinkedQueue<>();
        final List<Sent> sent = new ArrayList<>();
        final MemoryStore saved = new MemoryStore();
        final AtomicLong clock = new AtomicLong();
        final CountDownLatch browseStarted = new CountDownLatch(1), releaseBrowse = new CountDownLatch(1);
        final CountDownLatch oldStarted = new CountDownLatch(1), releaseOld = new CountDownLatch(1), oldFinished = new CountDownLatch(1);
        volatile boolean blockBrowse, blockOld, failPreparation;
        volatile List<List<QueueTrack>> pages = List.of(List.of(song("miracles")));
        final List<Integer> browsedPages = new CopyOnWriteArrayList<>();
        final GlobalPlaybackCoordinator<UUID> coordinator;
        final JammarrPlayback bridge;

        @SuppressWarnings("unchecked") Fixture() throws Exception {
            CoordinatorRuntime<UUID> runtime = (CoordinatorRuntime<UUID>) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{CoordinatorRuntime.class}, (p, m, a) -> switch (m.getName()) {
                case "playerId" -> a[0]; case "isOperator" -> operator.equals(a[0]);
                case "players" -> List.of(operator, listener); case "playerCount", "totalPlayerCount" -> 2;
                case "cacheDirectory" -> temporary.resolve(UUID.randomUUID().toString()); case "logger" -> CoreLogger.NO_OP;
                case "execute" -> { callbacks.add((Runnable) a[0]); yield null; }
                case "send" -> { assertSame(owner, Thread.currentThread()); sent.add(new Sent(a[0], a[1])); yield null; }
                case "chat" -> null; default -> throw new UnsupportedOperationException(m.getName());
            });
            PlexGateway plex = (PlexGateway) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{PlexGateway.class}, (p, m, a) -> {
                assertNotSame(owner, Thread.currentThread(), "Plex work must not block server thread");
                return switch (m.getName()) {
                    case "validate" -> null;
                    case "sonicStatus" -> new PlexService.SonicStatus(StationModels.SonicCapability.READY, "ready");
                    case "browse" -> {
                        assertEquals("Miracles", a[1]); browseStarted.countDown();
                        if (blockBrowse) assertTrue(releaseBrowse.await(5, TimeUnit.SECONDS));
                        int n = (int) a[2]; browsedPages.add(n);
                        yield new PlexService.Page(pages.get(n).stream().map(t -> new StationModels.MediaItem(StationModels.ItemKind.TRACK, t.key(), t.title(), t.artist(), t.durationMs())).toList(), n + 1 < pages.size());
                    }
                    case "expand" -> List.of(pages.stream().flatMap(List::stream).filter(t -> t.key().equals(a[1])).findFirst().orElseGet(() -> song((String) a[1])));
                    case "transcode" -> {
                        QueueTrack t = (QueueTrack) a[0];
                        if (blockOld && t.key().equals("old")) { oldStarted.countDown(); assertTrue(releaseOld.await(5, TimeUnit.SECONDS)); }
                        if (failPreparation && t.key().equals("miracles")) throw new java.io.IOException("fixture failure");
                        byte[] bytes = new byte[522 * 4000];
                        for (int i = 0; i < bytes.length; i += 522) { bytes[i] = (byte) 0xff; bytes[i+1] = (byte) 0xfb; bytes[i+2] = (byte) 0xa0; }
                        Files.write((Path) a[1], bytes);
                        if (t.key().equals("old")) oldFinished.countDown();
                        yield null;
                    }
                    case "hasSonicAnalysis" -> true;
                    default -> List.of();
                };
            });
            coordinator = new GlobalPlaybackCoordinator<>(runtime, saved, plex);
            await(() -> coordinator.diagnostics().contains("Plex=ONLINE"));
            bridge = new JammarrPlayback(coordinator, clock::get);
        }
        void startOld() throws Exception {
            coordinator.queue(operator, new ControlPackets.QueueRequest(StationModels.ItemKind.TRACK, "old"));
            await(() -> lastManifest() != null);
        }
        void control(ControlPackets.ControlAction action) { coordinator.control(operator, new ControlPackets.ControlRequest(action, -1, "")); }
        TransportPackets.AudioManifest lastManifest() {
            return sent.stream().map(Sent::message).filter(TransportPackets.AudioManifest.class::isInstance).map(TransportPackets.AudioManifest.class::cast)
                    .filter(m -> m.durationMs() > 0).reduce((a,b) -> b).orElse(null);
        }
        void pump() { Runnable r; while ((r = callbacks.poll()) != null) r.run(); }
        void await(BooleanSupplier condition) throws Exception {
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            do { pump(); if (condition.getAsBoolean()) return; Thread.sleep(5); } while (System.nanoTime() < until);
            fail("Timed out waiting for Jammarr fixture");
        }
        @Override public void close() throws Exception {
            releaseBrowse.countDown(); releaseOld.countDown(); bridge.close(); coordinator.close();
            var ioField = coordinator.getClass().getDeclaredField("io"); ioField.setAccessible(true);
            Object io = ioField.get(coordinator);
            var poolField = io.getClass().getDeclaredField("executor"); poolField.setAccessible(true);
            assertTrue(((ExecutorService) poolField.get(io)).awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static final class MemoryStore implements PlaybackStore {
        final List<QueueTrack> queue = new ArrayList<>(), history = new ArrayList<>();
        QueueTrack current;
        StatePackets.PlaybackOrigin origin = StatePackets.PlaybackOrigin.NONE;
        String source = "";
        StationModels.StationDefinition station = StationModels.StationDefinition.none(0);
        boolean autoplay, paused; long checkpoint;
        public List<QueueTrack> queue() { return queue; } public List<QueueTrack> history() { return history; }
        public QueueTrack current() { return current; } public StatePackets.PlaybackOrigin currentOrigin() { return origin; }
        public String currentSourceName() { return source; }
        // Match GlobalPlayer.ModernPlaybackStore: each call returns a new core value.
        public StationModels.StationDefinition station() {
            return new StationModels.StationDefinition(station.type(), station.name(), station.seeds(), station.generation());
        }
        public boolean autoplayEnabled() { return autoplay; } public long checkpointMs() { return checkpoint; } public boolean paused() { return paused; }
        public void current(QueueTrack t, StatePackets.PlaybackOrigin o, String s) { current=t; origin=o; source=s; }
        public void station(StationModels.StationDefinition s) { station=s; } public void autoplayEnabled(boolean v) { autoplay=v; }
        public void remember(QueueTrack t) { history.add(t); } public void update(long p, boolean v) { checkpoint=p; paused=v; }
        public void clearAll() { queue.clear(); history.clear(); current=null; origin=StatePackets.PlaybackOrigin.NONE; station=StationModels.StationDefinition.none(station.generation()+1); autoplay=false; checkpoint=0; paused=false; }
        public void markChanged() {}
    }
}
