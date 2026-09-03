package com.studyagent.task;

import com.studyagent.pomodoro.PomodoroSession;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Thực thể chính đại diện cho một nhiệm vụ học tập.
 * @author Thành viên 3 (Task Management)
 */
public class Task {
    private String id;
    private String title;
    private String subject;
    private int estimatedMinutes;
    private LocalDateTime deadline;
    private Priority priority;
    private TaskStatus status;
    private LocalDateTime createdAt;
    private List<PomodoroSession> sessions;

    public Task() {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.status = TaskStatus.TODO;
        this.priority = Priority.MEDIUM;
        this.createdAt = LocalDateTime.now();
        this.sessions = new ArrayList<>();
    }

    public Task(String title, String subject, int estimatedMinutes, LocalDateTime deadline, Priority priority) {
        this();
        this.title = title;
        this.subject = subject;
        this.estimatedMinutes = estimatedMinutes;
        this.deadline = deadline;
        this.priority = priority != null ? priority : Priority.MEDIUM;
    }

    // Nghiệp vụ Task
    public boolean isOverdue() {
        return deadline != null && LocalDateTime.now().isAfter(deadline) && status != TaskStatus.COMPLETED;
    }

    public void markCompleted() {
        this.status = TaskStatus.COMPLETED;
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public int getEstimatedMinutes() { return estimatedMinutes; }
    public void setEstimatedMinutes(int estimatedMinutes) { this.estimatedMinutes = estimatedMinutes; }

    public LocalDateTime getDeadline() { return deadline; }
    public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }

    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<PomodoroSession> getSessions() { return sessions; }
    public void setSessions(List<PomodoroSession> sessions) { this.sessions = sessions; }

    @Override
    public String toString() {
        return String.format("[%s] %s (Môn: %s | Hạn: %s | Thời lượng: %dp | Trạng thái: %s)",
                id, title, subject, (deadline != null ? deadline.toString() : "Không có"), estimatedMinutes, status.getDisplayName());
    }
}
