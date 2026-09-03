package com.studyagent.task;

/**
 * Trạng thái của một nhiệm vụ học tập.
 * @author Thành viên 3 (Task Management)
 */
public enum TaskStatus {
    TODO("Chưa bắt đầu"),
    IN_PROGRESS("Đang thực hiện"),
    COMPLETED("Đã hoàn thành"),
    OVERDUE("Quá hạn");

    private final String displayName;

    TaskStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
