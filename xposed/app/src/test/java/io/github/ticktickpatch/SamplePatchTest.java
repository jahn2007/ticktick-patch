package io.github.ticktickpatch;

import org.junit.Test;

import static org.junit.Assert.*;

public class SamplePatchTest {
    @Test
    public void samplePreservesComputedAndStoredGettersInsteadOfApplyingScriptExtras() {
        HookProfile profile = new HookProfile("apk-sample");
        assertTrue(profile.usesSamplePatch());
        assertFalse(profile.includesLimits());
        assertTrue(profile.includesValue("com.ticktick.task.data.User", "getProTypeForFake"));
        assertFalse(profile.includesValue("com.ticktick.task.data.User", "isPro"));
        assertFalse(profile.includesValue("com.ticktick.task.data.User", "getProEndDate"));
        assertFalse(profile.includesValue("com.ticktick.task.network.sync.entity.SignUserInfo", "getIsProN"));
        assertFalse(profile.includesValue("com.ticktick.task.sync.db.User", "getPRO_TYPE"));
        assertFalse(profile.includesValue("com.ticktick.task.javascript.CommonJavascriptObject$UserProfile", "getPro"));
    }

    @Test
    public void sampleTypeGettersRetainTheirDifferentResults() throws Exception {
        Object proType = null;
        Object fakeType = null;
        Object endTime = null;
        for (SamplePatch.Target target : SamplePatch.TARGETS) {
            if (!target.owner.equals("com.ticktick.task.data.User")) continue;
            if (target.method.equals("getProType")) proType = ReturnValues.forType(target.kind, int.class).create();
            if (target.method.equals("getProTypeForFake")) fakeType = ReturnValues.forType(target.kind, int.class).create();
            if (target.method.equals("getProEndTime")) endTime = ReturnValues.forType(target.kind, long.class).create();
        }
        assertEquals(1, proType);
        assertEquals(0, fakeType);
        assertEquals(10611728865536L, endTime);
    }

}
