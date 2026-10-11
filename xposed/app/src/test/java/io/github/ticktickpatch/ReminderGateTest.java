package io.github.ticktickpatch;

import android.content.Intent;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

public class ReminderGateTest {
    static class Handler {
        static boolean h() { return true; }
        void e(String action) {}
        void g(Intent intent) {}
    }

    static class Unrelated {
        static boolean h() { return true; }
        void e(String action) {}
    }

    static class InstanceGate {
        boolean h() { return true; }
        void e(String action) {}
        void g(Intent intent) {}
    }

    @Test
    public void resolvesOnlyStaticGateOnExpectedHandlerShape() throws Exception {
        Method method = ReminderGate.resolve(Handler.class);
        assertEquals(Handler.class, method.getDeclaringClass());
        assertEquals("h", method.getName());
        assertThrows(IllegalArgumentException.class, () -> ReminderGate.resolve(Unrelated.class));
        assertThrows(IllegalArgumentException.class, () -> ReminderGate.resolve(InstanceGate.class));
    }
}
