package io.github.ticktickpatch;

/** An initialized ID is necessary but never sufficient: object identity must also match. */
final class CurrentUserGuard {
    private volatile Object current;

    void update(Object user, Object id) {
        current = user != null && id != null && !id.toString().trim().isEmpty() ? user : null;
    }

    void clear() { current = null; }
    boolean hasCurrent() { return current != null; }
    boolean matches(Object receiver) { return receiver != null && receiver == current; }
}
