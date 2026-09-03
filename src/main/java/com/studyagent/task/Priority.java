package com.studyagent.task;

/**
 * Mức độ ưu tiên của bài học/nhiệm vụ.
 * @author Thành viên 3 (Task Management)
 */
public enum Priority {
    LOW("Thấp"),
    MEDIUM("Trung bình"),
    HIGH("Cao"),
    URGENT("Khẩn cấp");

    private final String displayName;

    Priority(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Priority fromString(String text) {
        if (text == null) return MEDIUM;
        for (Priority p : Priority.values()) {
            if (p.name().equalsIgnoreCase(text) || p.displayName.equalsIgnoreCase(text)) {
                return p;
            }
        }
        return MEDIUM;
    }
}
