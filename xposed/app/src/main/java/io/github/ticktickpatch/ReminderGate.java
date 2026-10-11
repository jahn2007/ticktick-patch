package io.github.ticktickpatch;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Validate the known handler shape before touching its obfuscated h() method. */
final class ReminderGate {
    static Method resolve(Class<?> owner) throws NoSuchMethodException {
        Method gate = owner.getDeclaredMethod("h");
        if (gate.getReturnType() != boolean.class || !Modifier.isStatic(gate.getModifiers())
                || Modifier.isAbstract(gate.getModifiers()) || Modifier.isNative(gate.getModifiers())) {
            throw new IllegalArgumentException("Not a static concrete boolean reminder gate");
        }
        boolean schedules = false;
        boolean dispatches = false;
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getReturnType() != void.class || Modifier.isStatic(method.getModifiers())
                    || method.getParameterCount() != 1) continue;
            String argument = method.getParameterTypes()[0].getName();
            if (method.getName().equals("e") && argument.equals("java.lang.String")) schedules = true;
            if (method.getName().equals("g") && argument.equals("android.content.Intent")) dispatches = true;
        }
        if (!schedules || !dispatches) throw new IllegalArgumentException("Reminder handler shape mismatch");
        gate.setAccessible(true);
        return gate;
    }

    private ReminderGate() {}
}
