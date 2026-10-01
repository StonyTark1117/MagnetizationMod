package com.stonytark.magnetization.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Caches class metadata only. Instances, storage objects and returned values
 * are read on every call so same-tick machine changes remain visible. */
final class AdapterReflection {
    private AdapterReflection() {}

    private static final ClassValue<List<Field>> FIELDS = new ClassValue<>() {
        @Override protected List<Field> computeValue(Class<?> type) {
            final List<Field> result = new ArrayList<>();
            for (; type != null; type = type.getSuperclass()) {
                for (final Field field : type.getDeclaredFields()) {
                    try { if (field.trySetAccessible()) result.add(field); }
                    catch (RuntimeException ignored) { }
                }
            }
            return List.copyOf(result);
        }
    };
    private static final ClassValue<Map<String, List<Method>>> NO_ARGS = new ClassValue<>() {
        @Override protected Map<String, List<Method>> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };
    private static final ClassValue<Map<String, Optional<Method>>> WITH_INT = new ClassValue<>() {
        @Override protected Map<String, Optional<Method>> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };
    private static final ClassValue<Map<String, List<Field>>> BOOLEAN_FIELDS = new ClassValue<>() {
        @Override protected Map<String, List<Field>> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };
    private record EnergyAccess(Method stored, Method capacity) {}
    private static final ClassValue<Optional<EnergyAccess>> ENERGY = new ClassValue<>() {
        @Override protected Optional<EnergyAccess> computeValue(Class<?> type) {
            try {
                return Optional.of(new EnergyAccess(type.getMethod("getEnergyStored"),
                        type.getMethod("getMaxEnergyStored")));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return Optional.empty();
            }
        }
    };

    static Object noArgs(final Object target, final String name) {
        final List<Method> methods = NO_ARGS.get(target.getClass()).computeIfAbsent(name, ignored -> {
            final List<Method> result = new ArrayList<>();
            for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    final Method method = type.getDeclaredMethod(name);
                    if (method.trySetAccessible()) result.add(method);
                } catch (ReflectiveOperationException | RuntimeException absent) { }
            }
            return List.copyOf(result);
        });
        // Keep superclass fallbacks when a more specific method throws.
        for (final Method method : methods) {
            try { return method.invoke(target); }
            catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return null;
    }

    static Object withInt(final Object target, final String name, final int arg) {
        final Optional<Method> method = WITH_INT.get(target.getClass()).computeIfAbsent(name, ignored -> {
            try { return Optional.of(target.getClass().getMethod(name, int.class)); }
            catch (ReflectiveOperationException | RuntimeException absent) { return Optional.empty(); }
        });
        try { return method.isPresent() ? method.get().invoke(target, arg) : null; }
        catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }

    static boolean booleanField(final Object target, final String name, final boolean fallback) {
        final List<Field> fields = BOOLEAN_FIELDS.get(target.getClass()).computeIfAbsent(name,
                ignored -> FIELDS.get(target.getClass()).stream()
                        .filter(field -> field.getName().equals(name)).toList());
        for (final Field field : fields) {
            try { return field.getBoolean(target); }
            catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return fallback;
    }

    static double energyRatio(final Object target) {
        for (final Field field : FIELDS.get(target.getClass())) {
            try {
                final Object storage = field.get(target);
                if (storage == null) continue;
                final Optional<EnergyAccess> access = ENERGY.get(storage.getClass());
                if (access.isEmpty()) continue;
                final Object stored = access.get().stored().invoke(storage);
                final Object capacity = access.get().capacity().invoke(storage);
                if (stored instanceof Number have && capacity instanceof Number max && max.doubleValue() > 0) {
                    return Math.max(0, Math.min(1, have.doubleValue() / max.doubleValue()));
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return 0;
    }
}
