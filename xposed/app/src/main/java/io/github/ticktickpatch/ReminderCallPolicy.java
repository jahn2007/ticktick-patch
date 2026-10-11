package io.github.ticktickpatch;

/** Experimental guard based on the reminder stacks captured on TickTick 8.2.2.0. */
final class ReminderCallPolicy {
    private ReminderCallPolicy() {}

    static boolean isReminder(StackTraceElement[] stack) {
        for (StackTraceElement frame : stack) {
            String owner = frame.getClassName();
            if (owner.equals("com.ticktick.task.compat.service.job.TaskAlertJobService")
                    || owner.startsWith("com.ticktick.task.compat.service.job.TaskAlertJobService$")
                    // The captured AlertServiceHandler class is obfuscated to jd.d.
                    || "AlertServiceHandler.java".equals(frame.getFileName())) {
                return true;
            }
        }
        return false;
    }
}
