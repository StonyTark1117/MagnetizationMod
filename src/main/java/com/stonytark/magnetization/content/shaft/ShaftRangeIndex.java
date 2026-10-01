package com.stonytark.magnetization.content.shaft;

import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/** Per-manager-pass broad phase. Never retains ship poses or topology across ticks. */
final class ShaftRangeIndex<T> {
    private record Entry<T>(int order, T value) {}
    private final List<T> all;
    private final NavigableMap<Double, NavigableMap<Double, List<Entry<T>>>> rows = new TreeMap<>();
    private double reach;
    private boolean fallback;

    ShaftRangeIndex(List<T> sources, Function<T, Vec3> center, ToDoubleFunction<T> range) {
        all = sources;
        for (int i = 0; i < sources.size(); i++) {
            T source = sources.get(i);
            Vec3 p = center.apply(source);
            double radius = Math.abs(range.applyAsDouble(source));
            if (!finite(p) || !Double.isFinite(radius)) { fallback = true; continue; }
            reach = Math.max(reach, radius);
            rows.computeIfAbsent(p.x, ignored -> new TreeMap<>())
                    .computeIfAbsent(p.z, ignored -> new ArrayList<>()).add(new Entry<>(i, source));
        }
    }

    List<T> near(Vec3 point) {
        if (fallback || !finite(point)) return all;
        List<Entry<T>> found = new ArrayList<>();
        // Expand by an ulp: the exact squared-distance check remains authoritative
        // at a floating-point boundary after a ship transformation.
        for (var row : rows.subMap(Math.nextDown(point.x - reach), true,
                Math.nextUp(point.x + reach), true).values()) {
            for (var bucket : row.subMap(Math.nextDown(point.z - reach), true,
                    Math.nextUp(point.z + reach), true).values()) found.addAll(bucket);
        }
        found.sort(Comparator.comparingInt(Entry::order));
        return found.stream().map(Entry::value).toList();
    }

    private static boolean finite(Vec3 p) {
        return Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z);
    }
}
