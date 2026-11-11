package io.github.ticktickpatch;

import org.junit.Test;
import static org.junit.Assert.*;

public class CurrentUserGuardTest {
    @Test public void blankProbeAndOtherAccountsNeverReceiveOverrides() {
        CurrentUserGuard guard = new CurrentUserGuard();
        Object current = new Object(), probe = new Object(), other = new Object();
        assertFalse(guard.matches(current));
        guard.update(current, "account-a");
        assertTrue(guard.matches(current));
        assertFalse(guard.matches(probe));
        assertFalse(guard.matches(other));
        guard.update(other, "account-b");
        assertFalse(guard.matches(current));
        assertTrue(guard.matches(other));
        guard.clear();
        assertFalse(guard.hasCurrent());
    }

    @Test public void missingIdentityDisablesOverridesAndEqualsIsNotIdentity() {
        CurrentUserGuard guard = new CurrentUserGuard();
        String first = new String("same"), second = new String("same");
        guard.update(first, "id");
        assertFalse(guard.matches(second));
        guard.update(first, null);
        assertFalse(guard.matches(first));
        guard.update(first, "  ");
        assertFalse(guard.hasCurrent());
        guard.update(null, "id");
        assertFalse(guard.matches(null));
    }
}
