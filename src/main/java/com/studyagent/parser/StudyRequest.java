package com.studyagent.parser;

import java.time.LocalDateTime;

/**
 * Data Transfer Object (DTO) chứa thông tin bài học sau khi được bóc tách từ câu tự nhiên.
 * @author Thành viên 2 (AI/NLP)
 */
public class StudyRequest {
    private String title;
    private String subject;
    private int estimatedMinutes;
    private LocalDateTime deadline;
    private String priority; // "HIGH", "MEDIUM", "LOW"
    private String rawInput;

    public StudyRequest() {}

    public StudyRequest(String title, String subject, int estimatedMinutes, LocalDateTime deadline, String priority, String rawInput) {
        this.title = title;
        this.subject = subject;
        this.estimatedMinutes = estimatedMinutes;
        this.deadline = deadline;
        this.priority = priority;
        this.rawInput = rawInput;
    }

    // Getters & Setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public int getEstimatedMinutes() { return estimatedMinutes; }
    public void setEstimatedMinutes(int estimatedMinutes) { this.estimatedMinutes = estimatedMinutes; }

    public LocalDateTime getDeadline() { return deadline; }
    public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getRawInput() { return rawInput; }
    public void setRawInput(String rawInput) { this.rawInput = rawInput; }

    @Override
    public String toString() {
        return "StudyRequest{" +
                "title='" + title + '\'' +
                ", subject='" + subject + '\'' +
                ", estimatedMinutes=" + estimatedMinutes +
                ", deadline=" + deadline +
                ", priority='" + priority + '\'' +
                '}';
    }
}
