package io.github.ticktickpatch;

import org.junit.Test;

import static org.junit.Assert.*;

public class ReminderCallPolicyTest {
    @Test
    public void capturedObfuscatedReminderStackIsProtected() {
        assertTrue(ReminderCallPolicy.isReminder(new StackTraceElement[]{
                new StackTraceElement("jd.d", "h", "AlertServiceHandler.java", 25),
                new StackTraceElement("jd.d", "e", "AlertServiceHandler.java", 5)
        }));
    }

    @Test
    public void jobServiceStillProtectsCallsWithoutSourceNames() {
        assertTrue(ReminderCallPolicy.isReminder(new StackTraceElement[]{
                new StackTraceElement("com.ticktick.task.compat.service.job.TaskAlertJobService",
                        "c", null, -1)
        }));
        assertTrue(ReminderCallPolicy.isReminder(new StackTraceElement[]{
                new StackTraceElement("com.ticktick.task.compat.service.job.TaskAlertJobService$Worker",
                        "run", null, -1)
        }));
    }

    @Test
    public void ordinaryCallsAndSimilarlyNamedClassesAreNotProtected() {
        assertFalse(ReminderCallPolicy.isReminder(new StackTraceElement[]{
                new StackTraceElement("com.ticktick.task.manager.TickTickAccountManager",
                        "saveUserStatus", "TickTickAccountManager.java", 119),
                new StackTraceElement("com.ticktick.task.compat.service.job.TaskAlertJobServiceExtra",
                        "run", null, -1)
        }));
        assertFalse(ReminderCallPolicy.isReminder(new StackTraceElement[0]));
    }
}
