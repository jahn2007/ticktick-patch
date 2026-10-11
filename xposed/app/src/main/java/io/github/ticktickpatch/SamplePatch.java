package io.github.ticktickpatch;

import static io.github.ticktickpatch.ReturnValues.Kind.*;

/** Only the constant-result getters directly observed in the supplied build-8001 APK. */
final class SamplePatch {
    static final class Target {
        final String owner;
        final String method;
        final ReturnValues.Kind kind;

        Target(String owner, String method, ReturnValues.Kind kind) {
            this.owner = owner;
            this.method = method;
            this.kind = kind;
        }
    }

    static final Target[] TARGETS = {
            user("getActiveTeamUser", TRUE), user("isActiveTeamUser", TRUE),
            user("getTeamPro", TRUE), user("getTeamUser", TRUE), user("isTeamUser", TRUE),
            user("getNeedSubscribe", FALSE), user("isNeedSubscribe", FALSE),
            user("getProType", PRO_TYPE), user("getProTypeForFake", ZERO),
            user("getProEndTime", SAMPLE_END_TIME),
            legacy("isPro", TRUE), legacy("isActiveTeamUser", TRUE),
            legacy("isTeamPro", TRUE), legacy("getNeedSubscribe", FALSE),
            new Target("com.ticktick.task.network.sync.model.User7ProModel", "isPro", TRUE),
            new Target("com.ticktick.task.network.sync.model.User7ProModel", "isNeedSubscribe", FALSE),
            new Target("com.ticktick.task.network.sync.payment.model.SubscriptionInfo", "getIsPro", TRUE),
            new Target("com.ticktick.task.network.sync.payment.model.SubscriptionInfo", "isNeedSubscribe", FALSE)
    };

    static boolean includes(String owner, String method) {
        for (Target target : TARGETS) {
            if (target.method.equals(method) && (owner == null || target.owner.equals(owner))) return true;
        }
        return false;
    }

    private static Target user(String method, ReturnValues.Kind kind) {
        return new Target("com.ticktick.task.data.User", method, kind);
    }

    private static Target legacy(String method, ReturnValues.Kind kind) {
        return new Target("com.ticktick.task.network.sync.common.model.SignUserInfo", method, kind);
    }

    private SamplePatch() {}
}
