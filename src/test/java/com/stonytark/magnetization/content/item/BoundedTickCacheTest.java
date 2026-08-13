package com.stonytark.magnetization.content.item;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedTickCacheTest {

    @Test
    void rejectsAnInvalidMaximumSize() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedTickCache<>(0));
    }

    @Test
    void replacesBucketsWithoutGrowingAndRejectsLateOlderWrites() {
        final var cache = new BoundedTickCache<String, String>(4);

        assertEquals("first", cache.putIfNewer("origin", 10, "first"));
        assertEquals("newest", cache.putIfNewer("origin", 12, "newest"));
        assertEquals("newest", cache.putIfNewer("origin", 11, "late-old-reading"));

        assertEquals(1, cache.size());
        assertEquals("newest", cache.get("origin", 12));
        assertNull(cache.get("origin", 11));
    }

    @Test
    void evictsTheLeastRecentlyUsedOriginAtTheHardLimit() {
        final var cache = new BoundedTickCache<String, Integer>(3);
        cache.putIfNewer("a", 1, 1);
        cache.putIfNewer("b", 1, 2);
        cache.putIfNewer("c", 1, 3);
        assertEquals(1, cache.get("a", 1));

        cache.putIfNewer("d", 1, 4);

        assertEquals(3, cache.size());
        assertNull(cache.get("b", 1));
        assertEquals(1, cache.get("a", 1));
    }

    @Test
    void concurrentCallersCannotExceedTheHardLimit() throws Exception {
        final int maximumSize = 32;
        final var cache = new BoundedTickCache<Integer, Integer>(maximumSize);
        try (var executor = Executors.newFixedThreadPool(8)) {
            final var tasks = new ArrayList<java.util.concurrent.Callable<Void>>();
            for (int thread = 0; thread < 8; thread++) {
                final int offset = thread * 1_000;
                tasks.add(() -> {
                    for (int i = 0; i < 1_000; i++) {
                        cache.putIfNewer(offset + i, i, i);
                        cache.get(offset + i, i);
                    }
                    return null;
                });
            }
            for (final var future : executor.invokeAll(tasks)) future.get();
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
        assertEquals(maximumSize, cache.size());
    }
}
