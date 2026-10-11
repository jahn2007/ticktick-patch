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
