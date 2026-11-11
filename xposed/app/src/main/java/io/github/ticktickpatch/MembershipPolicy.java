package io.github.ticktickpatch;

/** Local-only Pro activates the host's reminder restriction; leave local users unchanged. */
final class MembershipPolicy {
    static boolean shouldOverride(CurrentUserGuard guard, Object receiver, boolean local, boolean busy) {
        return !local && !busy && guard.matches(receiver);
    }
}
