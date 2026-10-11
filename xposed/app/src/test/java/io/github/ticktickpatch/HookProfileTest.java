package io.github.ticktickpatch;

import org.junit.Test;

import static org.junit.Assert.*;

public class HookProfileTest {
    @Test
    public void baselineDoesNotRegisterOrMutateAnything() {
        HookProfile profile = new HookProfile("none");
        assertFalse(profile.installsHooks());
        assertFalse(profile.changesResults());
        assertFalse(profile.includesValue("isPro"));
        assertFalse(profile.includesLimits());
        assertFalse(profile.includesLegacyDiagnostics());
    }

    @Test
    public void passThroughRegistersWithoutChangingResultsOrObservingDatabase() {
        HookProfile profile = new HookProfile("pass-through");
        assertTrue(profile.installsHooks());
        assertFalse(profile.changesResults());
        assertTrue(profile.includesValue("getACTIVE_TEAM_USER"));
        assertTrue(profile.includesLimits());
        assertFalse(profile.includesLegacyDiagnostics());
    }

    @Test
    public void minimalProfileOnlyChangesBooleanProQueries() {
        HookProfile profile = new HookProfile("pro");
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
    public void proSubsetsPartitionAllEightCandidatesWithoutOverlap() {
        String[][] candidates = {
                {"com.ticktick.task.network.sync.entity.SignUserInfo", "getIsProN"},
                {"com.ticktick.task.network.sync.common.model.SignUserInfo", "isPro"},
                {"com.ticktick.task.network.sync.model.User7ProModel", "isPro"},
                {"com.ticktick.task.network.sync.payment.model.SubscriptionInfo", "getIsPro"},
                {"com.ticktick.task.data.User", "isPro"},
                {"com.ticktick.task.helper.pro.ProHelper", "isPro"},
                {"com.ticktick.kernel.account.impl.AccountManager", "isPro"},
                {"com.ticktick.task.javascript.CommonJavascriptObject$UserProfile", "getPro"}
        };
        HookProfile all = new HookProfile("pro");
        HookProfile network = new HookProfile("pro-network");
        HookProfile local = new HookProfile("pro-local");
        int networkCount = 0;
        int localCount = 0;
        for (String[] candidate : candidates) {
            assertTrue(all.includesValue(candidate[0], candidate[1]));
            boolean inNetwork = network.includesValue(candidate[0], candidate[1]);
            boolean inLocal = local.includesValue(candidate[0], candidate[1]);
            assertTrue("Exactly one subset: " + candidate[0], inNetwork ^ inLocal);
            if (inNetwork) networkCount++;
            if (inLocal) localCount++;
            assertFalse(network.includesValue(candidate[0], "getProEndDate"));
            assertFalse(local.includesValue(candidate[0], "getProEndDate"));
        }
        assertEquals(4, networkCount);
        assertEquals(4, localCount);
        assertFalse(network.includesLimits());
        assertFalse(local.includesLimits());
        assertFalse(network.includesLegacyDiagnostics());
        assertFalse(local.includesLegacyDiagnostics());
    }

    @Test
    public void noTeamProfilePreservesTeamFieldsAcrossNamingStyles() {
        HookProfile profile = new HookProfile("no-team");
        for (String method : new String[]{"getTeamPro", "getTeamProEndDate", "isActiveTeamUser",
                "getTEAM_USER", "getACTIVE_TEAM_USER", "getTEAM_PRO"}) {
            assertFalse(profile.includesValue(method));
        }
        assertTrue(profile.includesValue("isPro"));
        assertTrue(profile.includesValue("getPRO_END_TIME"));
        assertTrue(profile.includesLimits());
    }

    @Test
    public void fullProfileKeepsOriginalCoverageAndInvalidProfilesFail() {
        HookProfile profile = new HookProfile("full");
        assertTrue(profile.includesValue("getTEAM_PRO"));
        assertTrue(profile.includesLegacyDiagnostics());
        assertFalse(profile.tracesCalls());
        assertThrows(IllegalArgumentException.class, () -> new HookProfile("unknown"));
    }
}
