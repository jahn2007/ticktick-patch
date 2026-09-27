/* ==================================================================================
 * TickTick 会员(VIP)解锁插件 —— LSPilot / BeanShell   v2.0
 * ----------------------------------------------------------------------------------
 * 【v2.0 关键修复】上一版加载后什么都没发生，原因是本机 LSPilot 版本里
 *   hostPackage / hostVerName / hostVerCode / hostLoader / hostContext 这些预置变量
 *   **并不存在**，脚本第一条 log 就"引用未定义变量"→ 整个插件直接中止（无日志、无 hook）。
 *   现在改为自力更生：
 *     ActivityThread.currentApplication()  ->  Application 实例
 *     app.getClassLoader()                 ->  宿主 ClassLoader
 *     Class.forName(name, false, CL)       ->  取宿主类
 *   实测可用（见 ZZProbe 探针日志 p3.txt）。
 *
 * 【破解思路】与 QuanX 脚本等价：改写 /api/v2/user/status 的 pro / needSubscribe /
 *   proEndDate，本插件在"响应模型层 + 用户模型层 + 限额层"同时把状态钉成永久会员。
 *
 * 【实机证据（官方 8.2.2.0 未破解版）】
 *   用户原始字段: proType=0 proEndTime=0 needSubscribe=true activeTeamUser=false ...
 *   → 官方版确实是免费用户，需要本插件。
 * ================================================================================== */

import java.util.Date;

// ------------------------------- 常量 -------------------------------
PRO_END_MS = 253382774400000L
PRO_END_STR = "9999-05-20T00:00:00.000+0000"
CL = null
APP = null
proDateObj = null
proDateCls = null
hookOk = 0
hookMiss = 0

// ------------------------------- 引导：拿宿主 ClassLoader -------------------------------
at = null
try { at = java.lang.Class.forName("android.app.ActivityThread") } catch (Throwable e1) { log("boot1 ERR: " + e1) }
log("boot: at = " + at)
if (at != null) {
    try { APP = at.getMethod("currentApplication").invoke(null) } catch (Throwable e2) { log("boot2 ERR: " + e2) }
}
log("boot: APP = " + APP)
if (APP != null) {
    try { CL = APP.getClassLoader() } catch (Throwable e3) { log("boot3 ERR: " + e3) }
}
log("boot: CL = " + CL)

log("==============================================================")
log("[TT-Unlock] v2.0 开始加载; CL=" + CL)
log("[TT-Unlock] Application=" + APP)

// 取宿主类（失败返回 null，不再让脚本崩掉）
clsOf(n) {
    if (CL == null) { return null }
    try { return java.lang.Class.forName(n, false, CL) } catch (Throwable t) { return null }
}

// 某个方法的返回类型（用于动态构造日期对象）
rtOf(c, m) {
    if (c == null) { return null }
    try { return c.getDeclaredMethod(m).getReturnType() } catch (Throwable t) { return null }
}

// 打印会员相关方法清单（版本升级后用来比对）
audit(tag, c) {
    if (c == null) { log("    [audit] " + tag + " : 类不存在"); return }
    try {
        for (m : c.getDeclaredMethods()) {
            n = m.getName()
            if (n.matches("(?i).*(pro|team|subscrib|grace|vip|pay).*")) {
                if (m.getParameterCount() == 0) { log("    [audit] " + tag + "." + n + "() : " + m.getReturnType().getName()) }
            }
        }
    } catch (Throwable t) { log("    [audit] " + tag + " 失败: " + t) }
}

// 读取当前用户真实的会员字段（破解前后对比用）
probeUser(tag) {
    try {
        if (APP == null) { log(tag + "APP="); return }
        am = callMethod(APP, "getAccountManager")
        cur = callMethod(am, "getCurrentUser")
        if (cur == null) { log(tag + "未登录/无用户"); return }
        log(tag + "原始字段: proType=" + getIntField(cur, "proType") + " proEndTime=" + getLongField(cur, "proEndTime") + " needSubscribe=" + getBooleanField(cur, "needSubscribe") + " activeTeamUser=" + getBooleanField(cur, "activeTeamUser") + " teamUser=" + getBooleanField(cur, "teamUser") + " teamPro=" + getBooleanField(cur, "teamPro") + " gracePeriod=" + getBooleanField(cur, "gracePeriod") + " accountType=" + getIntField(cur, "accountType"))
        log(tag + "API: isPro()=" + callMethod(cur, "isPro") + " getProType()=" + callMethod(cur, "getProType") + " getProEndTime()=" + callMethod(cur, "getProEndTime") + " isNeedSubscribe()=" + callMethod(cur, "isNeedSubscribe"))
    } catch (Throwable t) { log(tag + "探测失败: " + t) }
}

/* ==========================================================================
 * ① 响应实体： /api/v2/user/status 的落点
 * ========================================================================== */
sigE = clsOf("com.ticktick.task.network.sync.entity.SignUserInfo")
if (sigE != null) {
    log("[1] " + sigE.getName())
    try { hookMethodAfter(sigE, "getIsProN", param -> { param.result = true }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getIsProN : " + t) }
    try { hookMethodAfter(sigE, "getActiveTeamUserN", param -> { param.result = true }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getActiveTeamUserN : " + t) }
    try { hookMethodAfter(sigE, "getTeamUserN", param -> { param.result = true }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getTeamUserN : " + t) }
    try { hookMethodAfter(sigE, "getTeamPro", param -> { param.result = true }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getTeamPro : " + t) }
    try { hookMethodAfter(sigE, "getIsInGracePeriodN", param -> { param.result = false }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getIsInGracePeriodN : " + t) }
    try { hookMethodAfter(sigE, "getNeedSubscribe", param -> { param.result = Boolean.FALSE }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getNeedSubscribe : " + t) }
    try { hookMethodAfter(sigE, "getNoGraceDate", param -> { param.result = null }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getNoGraceDate : " + t) }
    proDateCls = rtOf(sigE, "getProEndDate")
    log("    proEndDate 的类型 = " + proDateCls)
    if (proDateCls != null) {
        try {
            if (proDateCls.getName().equals("java.util.Date")) { proDateObj = new Date(PRO_END_MS) }
            if (proDateCls.getName().equals("java.lang.String")) { proDateObj = PRO_END_STR }
            if (proDateObj == null) {
                for (ctor : proDateCls.getDeclaredConstructors()) {
                    ps = ctor.getParameterTypes()
                    if (ps.length == 1 && ps[0].getName().equals("long")) { ctor.setAccessible(true); proDateObj = ctor.newInstance(PRO_END_MS) }
                }
            }
        } catch (Throwable e) { log("    预构造 proEndDate 失败: " + e) }
        log("    proEndDate 预构造结果 = " + proDateObj)
    }
    try {
        hookMethodAfter(sigE, "getProEndDate", param -> {
            try {
                if (proDateObj == null) {
                    if (proDateCls != null) {
                        if (proDateCls.getName().equals("java.util.Date")) { proDateObj = new Date(PRO_END_MS) }
                        if (proDateCls.getName().equals("java.lang.String")) { proDateObj = PRO_END_STR }
                        if (proDateObj == null) {
                            for (ctor : proDateCls.getDeclaredConstructors()) {
                                ps = ctor.getParameterTypes()
                                if (ps.length == 1 && ps[0].getName().equals("long")) { ctor.setAccessible(true); proDateObj = ctor.newInstance(PRO_END_MS) }
                            }
                        }
                    }
                }
                if (proDateObj != null) { param.result = proDateObj }
            } catch (Throwable e) { log("    构造 proEndDate 失败: " + e) }
        })
        hookOk++
        log("    getProEndDate() -> 9999-05-20")
    } catch (Throwable t) { hookMiss++; log("    getProEndDate : " + t) }
} else { log("[1] 未找到 entity.SignUserInfo") }

/* ==========================================================================
 * ② 老版响应模型
 * ========================================================================== */
sigC = clsOf("com.ticktick.task.network.sync.common.model.SignUserInfo")
if (sigC != null) {
    log("[2] " + sigC.getName())
    try { hookMethodAfter(sigC, "isPro", param -> { param.result = true }); hookOk++ } catch (Throwable t) { hookMiss++; log("    isPro : " + t) }
    try { hookMethodAfter(sigC, "isActiveTeamUser", param -> { param.result = true }); hookOk++ } catch (Throwable t) { hookMiss++; log("    isActiveTeamUser : " + t) }
    try { hookMethodAfter(sigC, "isTeamPro", param -> { param.result = true }); hookOk++ } catch (Throwable t) { hookMiss++; log("    isTeamPro : " + t) }
    try { hookMethodAfter(sigC, "isTeamUser", param -> { param.result = true }); hookOk++ } catch (Throwable t) { hookMiss++; log("    isTeamUser : " + t) }
    try { hookMethodAfter(sigC, "getNeedSubscribe", param -> { param.result = Boolean.FALSE }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getNeedSubscribe : " + t) }
    try { hookMethodAfter(sigC, "getGracePeriod", param -> { param.result = Boolean.FALSE }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getGracePeriod : " + t) }
    try { hookMethodAfter(sigC, "getProEndDate", param -> { param.result = new Date(PRO_END_MS) }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getProEndDate : " + t) }
} else { log("[2] 未找到 common.model.SignUserInfo") }

/* ==========================================================================
 * ③ TickTick 7 Pro 模型
 * ========================================================================== */
u7 = clsOf("com.ticktick.task.network.sync.model.User7ProModel")
if (u7 != null) {
    log("[3] " + u7.getName())
    try { hookMethodAfter(u7, "isPro", param -> { param.result = true }); hookOk++ } catch (Throwable t) { hookMiss++; log("    isPro : " + t) }
    try { hookMethodAfter(u7, "isNeedSubscribe", param -> { param.result = false }); hookOk++ } catch (Throwable t) { hookMiss++; log("    isNeedSubscribe : " + t) }
    try { hookMethodAfter(u7, "getProEndDate", param -> { param.result = PRO_END_STR }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getProEndDate : " + t) }
} else { log("[3] 未找到 User7ProModel") }

/* ==========================================================================
 * ④ 订阅校验模型
 * ========================================================================== */
sub = clsOf("com.ticktick.task.network.sync.payment.model.SubscriptionInfo")
if (sub != null) {
    log("[4] " + sub.getName())
    try { hookMethodAfter(sub, "getIsPro", param -> { param.result = true }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getIsPro : " + t) }
    try { hookMethodAfter(sub, "isNeedSubscribe", param -> { param.result = false }); hookOk++ } catch (Throwable t) { hookMiss++; log("    isNeedSubscribe : " + t) }
    try { hookMethodAfter(sub, "getProEndDate", param -> { param.result = new Date(PRO_END_MS) }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getProEndDate : " + t) }
} else { log("[4] 未找到 SubscriptionInfo") }

/* ==========================================================================
 * ⑤ ★用户模型层：所有会员限制的最终依据
 *    AccountLimitManager.handleQuickBall()/handleHabitLimit() -> getCurrentUser().isPro()
 * ========================================================================== */
u = clsOf("com.ticktick.task.data.User")
if (u != null) {
    log("[5] " + u.getName())
    try { hookMethodAfter(u, "isPro", param -> { param.result = true }); hookOk++ } catch (Throwable t) { hookMiss++; log("    isPro : " + t) }
    try { hookMethodAfter(u, "isActiveTeamUser", param -> { param.result = true }); hookOk++ } catch (Throwable t) { hookMiss++; log("    isActiveTeamUser : " + t) }
    try { hookMethodAfter(u, "isTeamUser", param -> { param.result = true }); hookOk++ } catch (Throwable t) { hookMiss++; log("    isTeamUser : " + t) }
    try { hookMethodAfter(u, "getTeamPro", param -> { param.result = true }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getTeamPro : " + t) }
    try { hookMethodAfter(u, "isNeedSubscribe", param -> { param.result = false }); hookOk++ } catch (Throwable t) { hookMiss++; log("    isNeedSubscribe : " + t) }
    try { hookMethodAfter(u, "getNeedSubscribe", param -> { param.result = false }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getNeedSubscribe : " + t) }
    try { hookMethodAfter(u, "getGracePeriod", param -> { param.result = false }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getGracePeriod : " + t) }
    try { hookMethodAfter(u, "getProType", param -> { param.result = 1 }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getProType : " + t) }
    try { hookMethodAfter(u, "getProTypeForFake", param -> { param.result = 1 }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getProTypeForFake : " + t) }
    try { hookMethodAfter(u, "getProEndTime", param -> { param.result = PRO_END_MS }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getProEndTime : " + t) }
    try { hookMethodAfter(u, "getNoGraceDate", param -> { param.result = null }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getNoGraceDate : " + t) }
    try { hookMethodAfter(u, "isDuplicateSubscribeError", param -> { param.result = false }); hookOk++ } catch (Throwable t) { hookMiss++; log("    isDuplicateSubscribeError : " + t) }
} else { log("[5] 未找到 data.User") }

/* ==========================================================================
 * ⑥ 判定工具层
 * ========================================================================== */
ph = clsOf("com.ticktick.task.helper.pro.ProHelper")
if (ph != null) {
    try { hookMethodAfter(ph, "isPro", param -> { param.result = true }); hookOk++ } catch (Throwable t) { hookMiss++; log("[6] ProHelper.isPro : " + t) }
} else { log("[6] 未找到 ProHelper") }

kam = clsOf("com.ticktick.kernel.account.impl.AccountManager")
if (kam != null) {
    try { hookMethodAfter(kam, "isPro", param -> { param.result = true }); hookOk++ } catch (Throwable t) { hookMiss++; log("[7] AccountManager.isPro : " + t) }
} else { log("[7] 未找到 kernel.AccountManager") }

/* ==========================================================================
 * ⑦ 限额层：免费限额 -> 专业版限额
 * ========================================================================== */
lh = clsOf("com.ticktick.task.helper.LimitHelper")
if (lh != null) {
    try {
        hookMethodAfter(lh, "getLimitsFree", param -> {
            try { p = callMethod(param.thisObject, "getLimitsPro"); if (p != null) { param.result = p } } catch (Throwable e) { }
        })
        hookOk++
    } catch (Throwable t) { hookMiss++; log("[8] LimitHelper.getLimitsFree : " + t) }
} else { log("[8] 未找到 LimitHelper") }

/* ==========================================================================
 * ⑧ JS / 小组件桥接层
 * ========================================================================== */
jsu = clsOf("com.ticktick.task.javascript.CommonJavascriptObject" + (char)36 + "UserProfile")
if (jsu != null) {
    log("[9] " + jsu.getName())
    try { hookMethodAfter(jsu, "getPro", param -> { param.result = Boolean.TRUE }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getPro : " + t) }
    try { hookMethodAfter(jsu, "getNeedSubscribe", param -> { param.result = Boolean.FALSE }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getNeedSubscribe : " + t) }
    try { hookMethodAfter(jsu, "getActiveTeamUser", param -> { param.result = Boolean.TRUE }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getActiveTeamUser : " + t) }
    try { hookMethodAfter(jsu, "getTeamPro", param -> { param.result = true }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getTeamPro : " + t) }
    try { hookMethodAfter(jsu, "getGracePeriod", param -> { param.result = false }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getGracePeriod : " + t) }
    try { hookMethodAfter(jsu, "getProEndDate", param -> { param.result = new Date(PRO_END_MS) }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getProEndDate : " + t) }
    try { hookMethodAfter(jsu, "getTeamProEndDate", param -> { param.result = new Date(PRO_END_MS) }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getTeamProEndDate : " + t) }
} else { log("[9] 未找到 JS UserProfile") }

/* ==========================================================================
 * ⑨ 新版 Room 用户实体（Database.updateUser 的落点）
 * ========================================================================== */
dbu = clsOf("com.ticktick.task.sync.db.User")
if (dbu != null) {
    log("[10] " + dbu.getName())
    try { hookMethodAfter(dbu, "getPRO_TYPE", param -> { param.result = 1 }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getPRO_TYPE : " + t) }
    try { hookMethodAfter(dbu, "getPRO_END_TIME", param -> { param.result = PRO_END_MS }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getPRO_END_TIME : " + t) }
    try { hookMethodAfter(dbu, "getNEED_SUBSCRIBE", param -> { param.result = false }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getNEED_SUBSCRIBE : " + t) }
    try { hookMethodAfter(dbu, "getACTIVE_TEAM_USER", param -> { param.result = true }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getACTIVE_TEAM_USER : " + t) }
    try { hookMethodAfter(dbu, "getTEAM_PRO", param -> { param.result = true }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getTEAM_PRO : " + t) }
    try { hookMethodAfter(dbu, "getTEAM_USER", param -> { param.result = true }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getTEAM_USER : " + t) }
    try { hookMethodAfter(dbu, "getGRACE_PERIOD", param -> { param.result = false }); hookOk++ } catch (Throwable t) { hookMiss++; log("    getGRACE_PERIOD : " + t) }
} else { log("[10] 未找到 sync.db.User") }

/* ==========================================================================
 * ⑩ 观测：打印服务器真实返回的 user/status 字段
 * ========================================================================== */
dbCls = clsOf("com.ticktick.task.sync.db.Database")
if (dbCls != null) {
    try {
        hookMethodBefore(dbCls, "updateUser", param -> {
            si = param.args[0]
            if (si != null) {
                log("[SERVER->APP] pro=" + getObjectField(si, "pro") + " activeTeamUser=" + getObjectField(si, "activeTeamUser") + " teamUser=" + getObjectField(si, "teamUser") + " needSubscribe=" + getObjectField(si, "needSubscribe") + " gracePeriod=" + getObjectField(si, "gracePeriod") + " proEndDate=" + getObjectField(si, "proEndDate"))
            }
        })
        hookOk++
    } catch (Throwable t) { hookMiss++; log("[11] Database.updateUser 观测 : " + t) }
} else { log("[11] 未找到 sync.db.Database") }

/* ==========================================================================
 * ⑪ 版本自审计 + 探测
 * ========================================================================== */
log("--------------------------------------------------------------")
log("[audit] data.User:")
audit("data.User", u)
log("[audit] entity.SignUserInfo:")
audit("entity.SignUserInfo", sigE)
log("--------------------------------------------------------------")
probeUser("[load] ")

if (APP != null) {
    try { new android.os.Handler(APP.getMainLooper()).postDelayed(() -> { probeUser("[t+8s] ") }, 8000L) } catch (Throwable t) { log("[12] 延时探测失败: " + t) }
}
