package io.github.ticktickpatch;

import org.junit.Test;

import static org.junit.Assert.*;

public class HookProfileTest {
    @Test
    public void proGateOnlyChangesBooleanMembershipQueries() {
        HookProfile profile = new HookProfile("pro-reminder-gate");
        for (String method : new String[]{"isPro", "getIsPro", "getIsProN", "getPro"}) {
            assertTrue(profile.includesValue(method));
        }
        for (String method : new String[]{"getProType", "getPRO_TYPE", "getProEndDate",
                "getProEndTime", "getTeamPro", "isNeedSubscribe"}) {
            assertFalse(profile.includesValue(method));
        }
        assertFalse(profile.includesLimits());
    }

    @Test
    public void mergedProfileExcludesTeamButRetainsNonTeamReplacements() throws Exception {
        HookProfile merged = new HookProfile("merged-no-team");
        for (String method : new String[]{"getTeamPro", "isActiveTeamUser", "getACTIVE_TEAM_USER",
                "getTEAM_USER", "getTEAM_PRO", "getTeamProEndDate"}) {
            assertFalse(method, merged.includesValue(method));
        }
        for (String method : new String[]{"isPro", "getIsProN", "getPro", "getProEndTime",
                "getPRO_END_TIME", "getProEndDate", "getProType", "getProTypeForFake",
                "getPRO_TYPE", "getNeedSubscribe", "getGracePeriod", "getNoGraceDate",
                "isDuplicateSubscribeError"}) {
            assertTrue(method, merged.includesValue(method));
        }
        assertTrue(merged.includesLimits());
        assertFalse(merged.usesSamplePatch());
        String user = "com.ticktick.task.data.User";
        assertEquals(0, ReturnValues.forType(merged.replacementKind(user, "getProTypeForFake",
                ReturnValues.Kind.PRO_TYPE), int.class).create());
        assertEquals(1, ReturnValues.forType(merged.replacementKind(user, "getProType",
                ReturnValues.Kind.PRO_TYPE), int.class).create());
        for (String[] target : new String[][]{{user, "getProEndTime"},
                {"com.ticktick.task.sync.db.User", "getPRO_END_TIME"}}) {
            assertEquals(ReturnValues.PRO_END_MS, ReturnValues.forType(merged.replacementKind(
                    target[0], target[1], ReturnValues.Kind.END_TIME), long.class).create());
        }
    }

    @Test
    public void retiredProfilesAreRejected() {
        for (String name : new String[]{"full", "pro", "pro-network", "pro-local", "none",
                "pass-through", "pro-reminder-safe", "no-team", "pro-n1", "pro-n2", "pro-n3",
                "pro-n4", "pro-l1", "pro-l2", "pro-l3", "pro-l4", "unknown"}) {
            assertThrows(name, IllegalArgumentException.class, () -> new HookProfile(name));
        }
    }
}
