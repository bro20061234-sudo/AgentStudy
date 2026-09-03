package com.studyagent.pomodoro;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Đại diện cho một phiên học Pomodoro đơn lẻ.
 * @author Thành viên 4 (Pomodoro & Scheduler)
 */
public class PomodoroSession {
    private String id;
    private String taskId;
    private int sessionIndex;
    private int durationMinutes;
    private int breakMinutes;
    private boolean isCompleted;
    private LocalDateTime completedAt;

    public PomodoroSession() {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.durationMinutes = 25;
        this.breakMinutes = 5;
        this.isCompleted = false;
    }

    public PomodoroSession(String taskId, int sessionIndex, int durationMinutes, int breakMinutes) {
        this();
        this.taskId = taskId;
        this.sessionIndex = sessionIndex;
        this.durationMinutes = durationMinutes;
        this.breakMinutes = breakMinutes;
    }

    public void completeSession() {
        this.isCompleted = true;
        this.completedAt = LocalDateTime.now();
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public int getSessionIndex() { return sessionIndex; }
    public void setSessionIndex(int sessionIndex) { this.sessionIndex = sessionIndex; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    public int getBreakMinutes() { return breakMinutes; }
    public void setBreakMinutes(int breakMinutes) { this.breakMinutes = breakMinutes; }

    public boolean isCompleted() { return isCompleted; }
    public void setCompleted(boolean completed) { isCompleted = completed; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    @Override
    public String toString() {
        return String.format("  Phiên %d: %d phút tập trung + %d phút nghỉ | [%s]",
                sessionIndex, durationMinutes, breakMinutes, (isCompleted ? "ĐÃ XONG" : "CHƯA HỌC"));
    }
}
