package com.studyagent.scheduler;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Đại diện cho một lịch trình học tập cụ thể theo khung giờ.
 * @author Thành viên 4 (Pomodoro & Scheduler)
 */
public class Schedule {
    private String id;
    private String taskId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean reminderSent;

    public Schedule() {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.reminderSent = false;
    }

    public Schedule(String taskId, LocalDateTime startTime, LocalDateTime endTime) {
        this();
        this.taskId = taskId;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public boolean isReminderSent() { return reminderSent; }
    public void setReminderSent(boolean reminderSent) { this.reminderSent = reminderSent; }

    @Override
    public String toString() {
        return String.format("📅 Lịch học: %s -> %s (Task ID: %s)", startTime, endTime, taskId);
    }
}
