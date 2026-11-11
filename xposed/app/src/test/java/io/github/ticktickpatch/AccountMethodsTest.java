package io.github.ticktickpatch;

import org.junit.Test;
import static org.junit.Assert.*;

public class AccountMethodsTest {
    static class User {}
    static class Account {
        User d() { return new User(); }
        User b(String id) { return new User(); }
        static User empty() { return new User(); }
    }
    static class Ambiguous extends Account { User another() { return new User(); } }
    static class Child extends Account {}

    @Test public void findsInheritedCurrentGetterWithoutSelectingByIdOrStaticProbe() throws Exception {
        assertEquals("d", AccountMethods.currentGetter(Child.class, User.class).getName());
    }
    @Test public void ambiguousOrMissingMethodsAreRejected() {
        assertThrows(NoSuchMethodException.class, () -> AccountMethods.currentGetter(Ambiguous.class, User.class));
        assertThrows(NoSuchMethodException.class, () -> AccountMethods.currentGetter(Object.class, User.class));
    }
}
