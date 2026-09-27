package io.github.ticktickpatch;

import java.lang.reflect.Constructor;
import java.util.Date;

/** Validates replacements before a hook is installed; dates are never shared across calls. */
final class ReturnValues {
    static final long PRO_END_MS = 253382774400000L;
    static final String PRO_END_STR = "9999-05-20T00:00:00.000+0000";

    enum Kind { TRUE, FALSE, PRO_TYPE, END_TIME, END_DATE, NULL }

    interface Factory {
        Object create() throws ReflectiveOperationException;
    }

    static Factory forType(Kind kind, Class<?> type) throws ReflectiveOperationException {
        switch (kind) {
            case TRUE:
            case FALSE:
                if (type == boolean.class || type == Boolean.class) {
                    return () -> kind == Kind.TRUE;
                }
                break;
            case PRO_TYPE:
                if (type == int.class || type == Integer.class) return () -> 1;
                break;
            case END_TIME:
                if (type == long.class || type == Long.class) return () -> PRO_END_MS;
                break;
            case NULL:
                if (!type.isPrimitive()) return () -> null;
                break;
            case END_DATE:
                if (type == String.class) return () -> PRO_END_STR;
                if (type == Date.class) return () -> new Date(PRO_END_MS);
                if (!type.isPrimitive() && type != Object.class) {
                    Constructor<?> constructor = type.getDeclaredConstructor(long.class);
                    constructor.setAccessible(true);
                    return () -> constructor.newInstance(PRO_END_MS);
                }
                break;
        }
        throw new IllegalArgumentException(kind + " does not support " + type.getName());
    }

    private ReturnValues() {}
}
