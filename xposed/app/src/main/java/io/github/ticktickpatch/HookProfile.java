package io.github.ticktickpatch;

import java.util.Locale;

/** Selects independent reminder diagnostics without changing the default full profile. */
final class HookProfile {
    // Each single-method profile selects the requested class and getter, not just its name.
    private static final String[][] SINGLE_TARGETS = {
            {"pro-n1", "com.ticktick.task.network.sync.entity.SignUserInfo", "getIsProN"},
            {"pro-n2", "com.ticktick.task.network.sync.common.model.SignUserInfo", "isPro"},
            {"pro-n3", "com.ticktick.task.network.sync.model.User7ProModel", "isPro"},
            {"pro-n4", "com.ticktick.task.network.sync.payment.model.SubscriptionInfo", "getIsPro"},
            {"pro-l1", "com.ticktick.task.data.User", "isPro"},
            {"pro-l2", "com.ticktick.task.helper.pro.ProHelper", "isPro"},
            {"pro-l3", "com.ticktick.kernel.account.impl.AccountManager", "isPro"},
            {"pro-l4", "com.ticktick.task.javascript.CommonJavascriptObject$UserProfile", "getPro"}
    };
    private final String name;
    private final String[] singleTarget;

    HookProfile(String name) {
        singleTarget = singleTarget(name);
        if (!name.equals("none") && !name.equals("pass-through") && !name.equals("pro")
                && !name.equals("pro-network") && !name.equals("pro-local")
                && !name.equals("pro-reminder-safe")
                && !name.equals("pro-reminder-gate") && !name.equals("apk-sample")
                && !name.equals("no-team") && !name.equals("full") && singleTarget == null) {
            throw new IllegalArgumentException("Unknown patch profile: " + name);
        }
        this.name = name;
    }

    boolean installsHooks() { return !name.equals("none"); }
    boolean changesResults() { return !name.equals("none") && !name.equals("pass-through"); }
    boolean tracesCalls() { return !name.equals("none") && !name.equals("full"); }
    boolean includesLimits() { return name.equals("pass-through") || name.equals("no-team") || name.equals("full"); }
    boolean includesLegacyDiagnostics() { return name.equals("full"); }
    boolean preservesReminderCalls() { return name.equals("pro-reminder-safe"); }
    boolean usesSamplePatch() { return name.equals("apk-sample"); }
    boolean includesReminderGate() { return usesSamplePatch() || name.equals("pro-reminder-gate"); }

    boolean includesValue(String methodName) {
        if (!installsHooks()) return false;
        if (usesSamplePatch()) return SamplePatch.includes(null, methodName);
        if (singleTarget != null) return methodName.equals(singleTarget[2]);
        if (name.equals("pro") || name.equals("pro-network") || name.equals("pro-local")
                || name.equals("pro-reminder-safe") || name.equals("pro-reminder-gate")) {
            return methodName.equals("isPro") || methodName.equals("getIsPro")
                    || methodName.equals("getIsProN") || methodName.equals("getPro");
        }
        if (name.equals("no-team")) {
            return !methodName.toLowerCase(Locale.ROOT).contains("team");
        }
        return true;
    }

    boolean includesValue(String className, String methodName) {
        if (usesSamplePatch()) return SamplePatch.includes(className, methodName);
        if (singleTarget != null) {
            return className.equals(singleTarget[1]) && methodName.equals(singleTarget[2]);
        }
        if (!includesValue(methodName)) return false;
        boolean networkModel = className.startsWith("com.ticktick.task.network.sync.");
        if (name.equals("pro-network")) return networkModel;
        if (name.equals("pro-local") || name.equals("pro-reminder-safe")) return !networkModel;
        return true;
    }

    private static String[] singleTarget(String name) {
        for (String[] target : SINGLE_TARGETS) {
            if (target[0].equals(name)) return target;
        }
        return null;
    }
}
