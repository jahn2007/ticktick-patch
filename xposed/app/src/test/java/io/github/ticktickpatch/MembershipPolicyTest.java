package io.github.ticktickpatch;

import org.junit.Test;
import static io.github.ticktickpatch.ReturnValues.Kind.*;
import static org.junit.Assert.*;

public class MembershipPolicyTest {
    static class User {
        final boolean local;
        User(boolean local) { this.local = local; }
    }

    // Host formula supplied for the newer APK, independent of its obfuscated class/method names.
    private boolean blocked(boolean probePro, int probeType, boolean probeResponse,
                            boolean local, boolean pro, int fakeType) {
        if (probePro || probeType != 0 || probeResponse) return true;
        return local ? pro : pro && fakeType == 0;
    }

    @Test public void currentServerAccountUsesConsistentTypesAndPassesReminderCheck() throws Exception {
        CurrentUserGuard guard = new CurrentUserGuard();
        User current = new User(false), probe = new User(false);
        guard.update(current, "current");
        boolean eligible = MembershipPolicy.shouldOverride(guard, current, current.local, false);
        assertTrue(eligible);
        boolean pro = (Boolean) ReturnValues.forType(TRUE, boolean.class).create();
        int type = (Integer) ReturnValues.forType(PRO_TYPE, int.class).create();
        int fake = (Integer) ReturnValues.forType(PRO_TYPE, int.class).create();
        assertEquals(type, fake);
        assertEquals(pro, fake == 1);
        assertFalse(MembershipPolicy.shouldOverride(guard, probe, probe.local, false));
        assertFalse(blocked(false, 0, false, false, pro, fake));
        assertTrue(blocked(false, 0, false, false, true, 0)); // 2.0 regression
    }

    @Test public void localOtherMissingAndUpdatingAccountsKeepOriginalState() {
        CurrentUserGuard guard = new CurrentUserGuard();
        User local = new User(true), other = new User(false);
        guard.update(local, "local");
        assertFalse(MembershipPolicy.shouldOverride(guard, local, true, false));
        assertFalse(blocked(false, 0, false, true, false, 0));
        assertFalse(MembershipPolicy.shouldOverride(guard, other, false, false));
        assertFalse(MembershipPolicy.shouldOverride(guard, local, false, true));
        guard.clear();
        assertFalse(MembershipPolicy.shouldOverride(guard, local, false, false));
    }
}
