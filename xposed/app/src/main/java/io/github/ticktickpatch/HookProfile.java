package io.github.ticktickpatch;

import java.util.Locale;

/** Selects the merged default or one of the two device-verified alternatives. */
final class HookProfile {
    private final String name;

    HookProfile(String name) {
        if (!name.equals("merged-no-team") && !name.equals("apk-sample")
                && !name.equals("pro-reminder-gate")) {
            throw new IllegalArgumentException("Unknown patch profile: " + name);
        }
        this.name = name;
    }

    boolean includesLimits() { return name.equals("merged-no-team"); }
    boolean usesSamplePatch() { return name.equals("apk-sample"); }

    ReturnValues.Kind replacementKind(String className, String methodName, ReturnValues.Kind original) {
        if (name.equals("merged-no-team") && className.equals("com.ticktick.task.data.User")
                && methodName.equals("getProTypeForFake")) {
            return ReturnValues.Kind.ZERO;
        }
        return original;
    }

    boolean includesValue(String methodName) {
        if (usesSamplePatch()) return SamplePatch.includes(null, methodName);
        if (name.equals("pro-reminder-gate")) {
            return methodName.equals("isPro") || methodName.equals("getIsPro")
                    || methodName.equals("getIsProN") || methodName.equals("getPro");
        }
        return !methodName.toLowerCase(Locale.ROOT).contains("team");
    }

    boolean includesValue(String className, String methodName) {
        if (usesSamplePatch()) return SamplePatch.includes(className, methodName);
        return includesValue(methodName);
    }
}
