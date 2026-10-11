package io.github.ticktickpatch;

import java.util.Locale;

/** Selects independent reminder diagnostics without changing the default full profile. */
final class HookProfile {
    private final String name;

    HookProfile(String name) {
        if (!name.equals("none") && !name.equals("pass-through") && !name.equals("pro")
                && !name.equals("pro-network") && !name.equals("pro-local")
                && !name.equals("no-team") && !name.equals("full")) {
            throw new IllegalArgumentException("Unknown patch profile: " + name);
        }
        this.name = name;
    }

    boolean installsHooks() { return !name.equals("none"); }
    boolean changesResults() { return !name.equals("none") && !name.equals("pass-through"); }
    boolean tracesCalls() { return !name.equals("none") && !name.equals("full"); }
    boolean includesLimits() { return name.equals("pass-through") || name.equals("no-team") || name.equals("full"); }
    boolean includesLegacyDiagnostics() { return name.equals("full"); }

    boolean includesValue(String methodName) {
        if (!installsHooks()) return false;
        if (name.equals("pro") || name.equals("pro-network") || name.equals("pro-local")) {
            return methodName.equals("isPro") || methodName.equals("getIsPro")
                    || methodName.equals("getIsProN") || methodName.equals("getPro");
        }
        if (name.equals("no-team")) {
            return !methodName.toLowerCase(Locale.ROOT).contains("team");
        }
        return true;
    }

    boolean includesValue(String className, String methodName) {
        if (!includesValue(methodName)) return false;
        boolean networkModel = className.startsWith("com.ticktick.task.network.sync.");
        if (name.equals("pro-network")) return networkModel;
        if (name.equals("pro-local")) return !networkModel;
        return true;
    }
}
