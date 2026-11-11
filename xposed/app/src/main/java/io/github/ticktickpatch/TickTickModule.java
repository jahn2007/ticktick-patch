package io.github.ticktickpatch;

import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import io.github.libxposed.api.XposedModule;
import static io.github.ticktickpatch.ReturnValues.Kind.*;

/** API 102: account-scoped overrides; no host fields are changed. */
public final class TickTickModule extends XposedModule {
    private static final String TAG = "TickTickPatch";
    private final Set<ClassLoader> installed = Collections.newSetFromMap(new IdentityHashMap<>());

    @Override public synchronized void onPackageReady(PackageReadyParam param) {
        if (param.getPackageName().equals("com.ticktick.task") || param.getPackageName().equals("cn.ticktick.task"))
            install(param.getClassLoader());
    }

    @Override public synchronized boolean onHotReloading(HotReloadingParam param) {
        param.setSavedInstanceState(installed.toArray(new ClassLoader[0]));
        return true;
    }

    @Override public synchronized void onHotReloaded(HotReloadedParam param) {
        for (var handle : param.getOldHookHandles()) handle.unhook();
        Object state = param.getSavedInstanceState();
        if (state instanceof ClassLoader[]) {
            for (ClassLoader loader : (ClassLoader[]) state) install(loader);
        }
    }

    private void install(ClassLoader loader) {
        if (!installed.add(loader)) return;
        try { new Installer(loader).install(); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            log(Log.ERROR, TAG, "Account hook unavailable; preserving host behavior", e);
        }
    }

    private final class Installer {
        final ClassLoader loader;
        final CurrentUserGuard guard = new CurrentUserGuard();
        final Set<Class<?>> managers = Collections.newSetFromMap(new IdentityHashMap<>());
        final ThreadLocal<Integer> storageDepth = ThreadLocal.withInitial(() -> 0);
        final AtomicInteger accountOperations = new AtomicInteger();
        final AtomicLong epoch = new AtomicLong();
        Class<?> userType;
        Field id;
        Method localMode;
        volatile Object manager;

        Installer(ClassLoader loader) { this.loader = loader; }

        void install() throws ReflectiveOperationException {
            userType = Class.forName("com.ticktick.task.data.User", false, loader);
            id = field(userType, "_id");
            localMode = noArgs(userType, "isLocalMode");
            if (localMode.getReturnType() != boolean.class || Modifier.isStatic(localMode.getModifiers()))
                throw new IllegalArgumentException("Unsupported local account predicate");
            ReturnValues.forType(TRUE, noArgs(userType, "isPro").getReturnType()).create();
            for (String name : new String[]{"getProType", "getProTypeForFake"})
                ReturnValues.forType(PRO_TYPE, noArgs(userType, name).getReturnType()).create();
            Class<?> app = Class.forName("com.ticktick.task.TickTickApplicationBase", false, loader);
            Method account = noArgs(app, "getAccountManager");
            if (Modifier.isStatic(account.getModifiers())) throw new IllegalArgumentException("Static account accessor");
            // Install before Application initialization, without invoking an uninitialized singleton.
            hook(account).setId("ticktickpatch.account").intercept(chain -> {
                Object result = chain.proceed();
                try { attach(result); }
                catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                    log(Log.WARN, TAG, "Cannot identify current account", e);
                    guard.clear();
                }
                return result;
            });
            values(TRUE, "isPro");
            values(FALSE, "isNeedSubscribe", "getNeedSubscribe", "getGracePeriod", "isDuplicateSubscribeError");
            values(PRO_TYPE, "getProType", "getProTypeForFake");
            values(END_TIME, "getProEndTime");
            // Hot reload or late injection: initialize from the already-created application.
            try {
                Object instance = noArgs(app, "getInstance").invoke(null);
                if (instance != null) account.invoke(instance);
            } catch (ReflectiveOperationException | RuntimeException e) {
                // On a cold start the account accessor hook initializes us later.
            }
            limits();
        }

        synchronized void attach(Object value) throws ReflectiveOperationException {
            if (value == null) { manager = null; guard.clear(); return; }
            Method getter = AccountMethods.currentGetter(value.getClass(), userType);
            if (manager != value) { epoch.incrementAndGet(); manager = value; guard.clear(); }
            if (managers.add(value.getClass())) {
                hook(getter).setId("ticktickpatch.current-user").intercept(chain -> {
                    long generation = epoch.get();
                    Object result = chain.proceed();
                    if (chain.getThisObject() == manager && storageDepth.get() == 0
                            && accountOperations.get() == 0 && generation == epoch.get()) observe(result);
                    return result;
                });
                // Do not modify write arguments. Suppress overrides during account writes and
                // invalidate cached identity before login/logout/account updates can occur.
                for (Method method : value.getClass().getDeclaredMethods()) {
                    if (method.equals(getter) || method.isSynthetic() || Modifier.isStatic(method.getModifiers())
                            || Modifier.isAbstract(method.getModifiers()) || Modifier.isNative(method.getModifiers())) continue;
                    hook(method).setId("ticktickpatch.account-context").intercept(chain -> {
                        if (chain.getThisObject() != manager || storageDepth.get() != 0) return chain.proceed();
                        epoch.incrementAndGet();
                        accountOperations.incrementAndGet();
                        guard.clear();
                        int depth = storageDepth.get();
                        storageDepth.set(depth + 1);
                        try { return chain.proceed(); }
                        finally {
                            try { observe(getter.invoke(chain.getThisObject())); }
                            catch (ReflectiveOperationException | RuntimeException e) { guard.clear(); }
                            accountOperations.decrementAndGet();
                            if (depth == 0) storageDepth.remove(); else storageDepth.set(depth);
                        }
                    });
                }
            }
            observe(getter.invoke(value));
        }

        void observe(Object user) {
            try {
                Object key = user != null && userType.isInstance(user) ? id.get(user) : null;
                guard.update(user, key);
            } catch (IllegalAccessException | RuntimeException e) { guard.clear(); }
        }

        void values(ReturnValues.Kind kind, String... names) {
            for (String name : names) {
                try {
                    Method method = noArgs(userType, name);
                    ReturnValues.Factory factory = ReturnValues.forType(kind, method.getReturnType());
                    factory.create();
                    hook(method).setId("ticktickpatch.value").intercept(chain -> {
                        Object original = chain.proceed();
                        if (!eligible(chain.getThisObject())) return original;
                        try { return factory.create(); }
                        catch (ReflectiveOperationException | RuntimeException e) { return original; }
                    });
                } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                    log(Log.WARN, TAG, "Skipped User." + name, e);
                }
            }
        }

        boolean eligible(Object receiver) {
            if (storageDepth.get() != 0 || accountOperations.get() != 0 || !guard.matches(receiver)) return false;
            int depth = storageDepth.get();
            storageDepth.set(depth + 1);
            try {
                boolean local = Boolean.TRUE.equals(localMode.invoke(receiver));
                return MembershipPolicy.shouldOverride(guard, receiver, local, accountOperations.get() != 0);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return false;
            } finally {
                if (depth == 0) storageDepth.remove(); else storageDepth.set(depth);
            }
        }

        void limits() {
            try {
                Class<?> type = Class.forName("com.ticktick.task.helper.LimitHelper", false, loader);
                Method free = noArgs(type, "getLimitsFree"), pro = noArgs(type, "getLimitsPro");
                if (free.getReturnType().isPrimitive() || !free.getReturnType().isAssignableFrom(pro.getReturnType())
                        || Modifier.isStatic(free.getModifiers()) != Modifier.isStatic(pro.getModifiers())) return;
                hook(free).setId("ticktickpatch.limits").intercept(chain -> {
                    Object original = chain.proceed();
                    if (!eligible(guard.currentUser())) return original;
                    try { Object result = pro.invoke(chain.getThisObject()); return result != null ? result : original; }
                    catch (ReflectiveOperationException | RuntimeException e) { return original; }
                });
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { }
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static Method noArgs(Class<?> type, String name) throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try { Method m = c.getDeclaredMethod(name); m.setAccessible(true); return m; }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }
}
