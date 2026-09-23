package com.stonytark.magnetization.compat.jammarr;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/** A small, eagerly resolved ABI boundary. No Jammarr types enter our class signatures. */
final class JammarrAccess {
    final Class<?> type;
    private final Map<String, Field> fields = new HashMap<>();
    private final Map<String, Method> methods = new HashMap<>();

    JammarrAccess(ClassLoader loader, String name) throws ReflectiveOperationException {
        type = Class.forName("stonytark.jammarr." + name, false, loader);
    }

    JammarrAccess field(String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        fields.put(name, field);
        return this;
    }

    JammarrAccess method(String name, Class<?>... arguments) throws ReflectiveOperationException {
        Method method;
        try { method = type.getMethod(name, arguments); }
        catch (NoSuchMethodException absent) { method = type.getDeclaredMethod(name, arguments); }
        method.setAccessible(true);
        methods.put(name + "/" + arguments.length, method);
        return this;
    }

    Object get(Object target, String name) {
        try { return fields.get(name).get(target); }
        catch (IllegalAccessException e) { throw new IllegalStateException("Jammarr field inaccessible: " + name, e); }
    }

    void set(Object target, String name, Object value) {
        try { fields.get(name).set(target, value); }
        catch (IllegalAccessException e) { throw new IllegalStateException("Jammarr field inaccessible: " + name, e); }
    }

    Object call(Object target, String name, Object... arguments) {
        try { return methods.get(name + "/" + arguments.length).invoke(target, arguments); }
        catch (InvocationTargetException e) {
            throw new IllegalStateException("Jammarr operation failed: " + name, e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Jammarr method inaccessible: " + name, e);
        }
    }

    Object constant(String name) throws ReflectiveOperationException { return type.getField(name).get(null); }
}
