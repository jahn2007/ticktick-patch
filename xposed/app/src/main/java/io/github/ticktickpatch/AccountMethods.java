package io.github.ticktickpatch;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

final class AccountMethods {
    static Method currentGetter(Class<?> type, Class<?> user) throws NoSuchMethodException {
        Method selected = null;
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (method.getReturnType() != user || method.getParameterCount() != 0
                        || Modifier.isStatic(method.getModifiers()) || method.isSynthetic()
                        || method.isBridge() || Modifier.isAbstract(method.getModifiers())) continue;
                if (selected != null && !selected.getName().equals(method.getName()))
                    throw new NoSuchMethodException("Ambiguous current user getters");
                if (selected == null) selected = method;
            }
        }
        if (selected == null) throw new NoSuchMethodException("No current user getter");
        selected.setAccessible(true);
        return selected;
    }
}
