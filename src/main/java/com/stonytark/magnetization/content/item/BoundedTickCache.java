package com.stonytark.magnetization.content.item;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small synchronized LRU cache whose values are replaced only by readings from
 * the same or a newer tick bucket. Expensive work deliberately happens outside
 * this class so client and integrated-server callers do not hold the cache lock
 * while scanning the world.
 */
final class BoundedTickCache<K, V> {
    private final int maximumSize;
    private final Map<K, Entry<V>> entries = new LinkedHashMap<>(16, 0.75f, true);

    BoundedTickCache(final int maximumSize) {
        if (maximumSize < 1) throw new IllegalArgumentException("maximumSize must be positive");
        this.maximumSize = maximumSize;
    }

    synchronized V get(final K key, final long tickBucket) {
        final Entry<V> entry = entries.get(key);
        return entry != null && entry.tickBucket() == tickBucket ? entry.value() : null;
    }

    synchronized V putIfNewer(final K key, final long tickBucket, final V value) {
        final Entry<V> current = entries.get(key);
        if (current != null && current.tickBucket() >= tickBucket) return current.value();

        entries.put(key, new Entry<>(tickBucket, value));
        while (entries.size() > maximumSize) {
            final var iterator = entries.entrySet().iterator();
            iterator.next();
            iterator.remove();
        }
        return value;
    }

    synchronized int size() {
        return entries.size();
    }

    private record Entry<V>(long tickBucket, V value) {}
}
