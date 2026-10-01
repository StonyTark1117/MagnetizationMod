import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Multimaps;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;

/** Diagnostic model of Registrate's callback put/removeAll lifecycle, not a Minecraft startup test. */
public final class RegistrateCallbackRaceProbe {
    public static void main(String[] args) throws Exception {
        int attempts = Integer.parseInt(args.length == 0 ? "10000" : args[0]);
        boolean synchronizedMap = args.length > 1 && args[1].equals("synchronized");
        var current = new AtomicReference<SetMultimap<String, Object>>();
        var failure = new AtomicReference<Throwable>();
        var barrier = new CyclicBarrier(3);
        Thread[] workers = new Thread[2];
        for (int worker = 0; worker < workers.length; worker++) {
            final String key = "registration-" + worker;
            workers[worker] = Thread.ofPlatform().start(() -> {
                try {
                    for (int round = 0; round < attempts; round++) {
                        barrier.await();
                        try {
                            var callbacks = current.get();
                            for (int entry = 0; entry < 20; entry++) {
                                // Each thread owns different entries, as independent Create registration classes do.
                                callbacks.put(key, new Object());
                                callbacks.removeAll(key);
                            }
                        } catch (Throwable error) { failure.compareAndSet(null, error); }
                        barrier.await();
                    }
                } catch (Exception error) { failure.compareAndSet(null, error); }
            });
        }
        int inconsistent = 0;
        int nonemptyWithoutEntries = 0;
        for (int round = 0; round < attempts; round++) {
            var map = HashMultimap.<String, Object>create();
            current.set(synchronizedMap ? Multimaps.synchronizedSetMultimap(map) : map);
            barrier.await();
            barrier.await();
            var callbacks = current.get();
            int entries = callbacks.asMap().values().stream().mapToInt(java.util.Collection::size).sum();
            if (callbacks.size() != entries) {
                inconsistent++;
                if (!callbacks.isEmpty() && callbacks.asMap().isEmpty()) nonemptyWithoutEntries++;
                if (inconsistent <= 3) System.out.println("RACE_STATE round=" + round + " reportedSize=" + callbacks.size() + " entries=" + callbacks.asMap());
            }
        }
        for (var thread : workers) thread.join();
        if (failure.get() != null) throw new IllegalStateException("Worker failed", failure.get());
        System.out.println("RACE_RESULT synchronized=" + synchronizedMap + " attempts=" + attempts + " inconsistent=" + inconsistent + " nonemptyWithoutEntries=" + nonemptyWithoutEntries);
        // A sequential control must remain consistent for exactly the same operations.
        var control = HashMultimap.<String, Object>create();
        for (int i = 0; i < attempts * 40; i++) { control.put("control", new Object()); control.removeAll("control"); }
        if (!control.isEmpty() || !control.asMap().isEmpty()) throw new AssertionError("Sequential control failed");
        System.out.println("SEQUENTIAL_CONTROL_PASS");
    }
}
