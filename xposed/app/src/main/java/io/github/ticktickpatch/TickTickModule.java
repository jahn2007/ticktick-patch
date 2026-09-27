package io.github.ticktickpatch;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

import static io.github.ticktickpatch.ReturnValues.Kind.*;

/** API 102 entry point. The framework supplies the host loader before Application creation. */
public final class TickTickModule extends XposedModule {
    private static final String TAG = "TickTickPatch";
    private final Set<ClassLoader> installed =
            Collections.newSetFromMap(new IdentityHashMap<>());

    @Override
    public synchronized void onPackageReady(PackageReadyParam param) {
        String name = param.getPackageName();
        if (!name.equals("com.ticktick.task") && !name.equals("cn.ticktick.task")) return;
        if (!installed.add(param.getClassLoader())) return;
        new Installer(param.getClassLoader()).install();
    }

    private final class Installer {
        private final ClassLoader loader;

        Installer(ClassLoader loader) { this.loader = loader; }

        void install() {
            Class<?> entity = find("com.ticktick.task.network.sync.entity.SignUserInfo");
            values(entity, TRUE, "getIsProN", "getActiveTeamUserN", "getTeamUserN", "getTeamPro");
            values(entity, FALSE, "getIsInGracePeriodN", "getNeedSubscribe");
            values(entity, NULL, "getNoGraceDate");
            values(entity, END_DATE, "getProEndDate");

            Class<?> legacy = find("com.ticktick.task.network.sync.common.model.SignUserInfo");
            values(legacy, TRUE, "isPro", "isActiveTeamUser", "isTeamPro", "isTeamUser");
            values(legacy, FALSE, "getNeedSubscribe", "getGracePeriod");
            values(legacy, END_DATE, "getProEndDate");

            Class<?> user7 = find("com.ticktick.task.network.sync.model.User7ProModel");
            values(user7, TRUE, "isPro");
            values(user7, FALSE, "isNeedSubscribe");
            values(user7, END_DATE, "getProEndDate");

            Class<?> subscription = find("com.ticktick.task.network.sync.payment.model.SubscriptionInfo");
            values(subscription, TRUE, "getIsPro");
            values(subscription, FALSE, "isNeedSubscribe");
            values(subscription, END_DATE, "getProEndDate");

            Class<?> user = find("com.ticktick.task.data.User");
            values(user, TRUE, "isPro", "isActiveTeamUser", "isTeamUser", "getTeamPro");
            values(user, FALSE, "isNeedSubscribe", "getNeedSubscribe", "getGracePeriod", "isDuplicateSubscribeError");
            values(user, PRO_TYPE, "getProType", "getProTypeForFake");
            values(user, END_TIME, "getProEndTime");
            values(user, NULL, "getNoGraceDate");

            values(find("com.ticktick.task.helper.pro.ProHelper"), TRUE, "isPro");
            values(find("com.ticktick.kernel.account.impl.AccountManager"), TRUE, "isPro");
            limits(find("com.ticktick.task.helper.LimitHelper"));

            Class<?> js = find("com.ticktick.task.javascript.CommonJavascriptObject$UserProfile");
            values(js, TRUE, "getPro", "getActiveTeamUser", "getTeamPro");
            values(js, FALSE, "getNeedSubscribe", "getGracePeriod");
            values(js, END_DATE, "getProEndDate", "getTeamProEndDate");

            Class<?> dbUser = find("com.ticktick.task.sync.db.User");
            values(dbUser, PRO_TYPE, "getPRO_TYPE");
            values(dbUser, END_TIME, "getPRO_END_TIME");
            values(dbUser, FALSE, "getNEED_SUBSCRIBE", "getGRACE_PERIOD");
            values(dbUser, TRUE, "getACTIVE_TEAM_USER", "getTEAM_PRO", "getTEAM_USER");

            if (BuildConfig.DEBUG) {
                observeDatabase(find("com.ticktick.task.sync.db.Database"));
                audit(entity);
                audit(user);
            }
        }

        Class<?> find(String name) {
            try {
                return Class.forName(name, false, loader);
            } catch (ClassNotFoundException | LinkageError e) {
                log(Log.INFO, TAG, "Class unavailable: " + name);
                return null;
            }
        }

        void values(Class<?> type, ReturnValues.Kind kind, String... names) {
            if (type == null) return;
            for (String name : names) {
                try {
                    // Only zero-argument getters are targeted, never arbitrary overloads.
                    Method method = noArgs(type, name);
                    ReturnValues.Factory factory = ReturnValues.forType(kind, method.getReturnType());
                    // Reject unsupported date constructors before touching the host method.
                    factory.create();
                    AtomicBoolean warned = new AtomicBoolean();
                    hook(method).setId("ticktickpatch.value").intercept(chain -> {
                        Object original = chain.proceed();
                        try {
                            return factory.create();
                        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                            if (warned.compareAndSet(false, true)) {
                                log(Log.WARN, TAG, "Replacement failed: " + method, e);
                            }
                            return original;
                        }
                    });
                } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                    skip(type.getName() + "." + name, e);
                }
            }
        }

        void limits(Class<?> type) {
            if (type == null) return;
            try {
                Method free = noArgs(type, "getLimitsFree");
                Method pro = noArgs(type, "getLimitsPro");
                if (free.getReturnType().isPrimitive()
                        || !free.getReturnType().isAssignableFrom(pro.getReturnType())
                        || Modifier.isStatic(free.getModifiers()) != Modifier.isStatic(pro.getModifiers())) {
                    throw new IllegalArgumentException("Incompatible limit getters");
                }
                AtomicBoolean warned = new AtomicBoolean();
                hook(free).setId("ticktickpatch.limits").intercept(chain -> {
                    Object original = chain.proceed();
                    try {
                        Object replacement = pro.invoke(chain.getThisObject());
                        return replacement != null ? replacement : original;
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                        if (warned.compareAndSet(false, true)) log(Log.WARN, TAG, "Limits fallback", e);
                        return original;
                    }
                });
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                skip(type.getName() + ".getLimitsFree", e);
            }
        }

        void observeDatabase(Class<?> type) {
            if (type == null) return;
            try {
                for (Method method : type.getDeclaredMethods()) {
                    if (!method.getName().equals("updateUser") || method.getParameterCount() == 0) continue;
                    AtomicBoolean printed = new AtomicBoolean();
                    hook(method).setId("ticktickpatch.observe").intercept(chain -> {
                        if (printed.compareAndSet(false, true)) {
                            Object data = chain.getArg(0);
                            if (data != null) {
                                StringBuilder message = new StringBuilder("[SERVER->APP]");
                                for (String field : new String[]{"pro", "activeTeamUser", "teamUser",
                                        "needSubscribe", "gracePeriod", "proEndDate"}) {
                                    message.append(' ').append(field).append('=').append(readField(data, field));
                                }
                                log(Log.DEBUG, TAG, message.toString());
                            }
                        }
                        return chain.proceed();
                    });
                }
            } catch (RuntimeException | LinkageError e) { skip("Database observer", e); }
        }

        void audit(Class<?> type) {
            if (type == null) return;
            try {
                for (Method method : type.getDeclaredMethods()) {
                    if (method.getParameterCount() == 0
                            && method.getName().matches("(?i).*(pro|team|subscrib|grace|vip|pay).*")) {
                        log(Log.DEBUG, TAG, "[audit] " + method);
                    }
                }
            } catch (RuntimeException | LinkageError e) { log(Log.DEBUG, TAG, "Audit unavailable", e); }
        }

        void skip(String name, Throwable error) {
            log(Log.WARN, TAG, "Skipped " + name + ": " + error);
        }
    }

    private static Method noArgs(Class<?> type, String name) throws NoSuchMethodException {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name);
                if (Modifier.isAbstract(method.getModifiers())) throw new IllegalArgumentException("Abstract getter");
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) { /* Search inherited model getters. */ }
        }
        throw new NoSuchMethodException(type.getName() + "." + name + "()");
    }

    private static String readField(Object object, String name) {
        for (Class<?> current = object.getClass(); current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return String.valueOf(field.get(object));
            } catch (NoSuchFieldException ignored) {
                // Continue into the superclass.
            } catch (IllegalAccessException | RuntimeException | LinkageError e) { return "<unavailable>"; }
        }
        return "<absent>";
    }
}
