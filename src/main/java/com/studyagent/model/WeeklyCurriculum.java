package com.studyagent.model;

public class WeeklyCurriculum {
    private String id;
    private long chatId;
    private String subjectName;
    private int weekNumber;
    private String chapterTitle;
    private String detailedSyllabus;

    public WeeklyCurriculum() {}

    public WeeklyCurriculum(long chatId, String subjectName, int weekNumber, String chapterTitle, String detailedSyllabus) {
        this.chatId = chatId;
        this.subjectName = subjectName;
        this.weekNumber = weekNumber;
        this.chapterTitle = chapterTitle;
        this.detailedSyllabus = detailedSyllabus;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public long getChatId() { return chatId; }
    public void setChatId(long chatId) { this.chatId = chatId; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public int getWeekNumber() { return weekNumber; }
    public void setWeekNumber(int weekNumber) { this.weekNumber = weekNumber; }

    public String getChapterTitle() { return chapterTitle; }
    public void setChapterTitle(String chapterTitle) { this.chapterTitle = chapterTitle; }

    public String getDetailedSyllabus() { return detailedSyllabus; }
    public void setDetailedSyllabus(String detailedSyllabus) { this.detailedSyllabus = detailedSyllabus; }

    public String getDetailedLessonIssue() {
        if (detailedSyllabus == null || detailedSyllabus.length() < 1500) {
            return "Bài giảng quá ngắn, hiện chỉ giống đề cương tóm tắt.";
        }
        String normalized = java.text.Normalizer.normalize(detailedSyllabus, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(java.util.Locale.ROOT);
        String[] requiredSections = {
                "MUC TIEU BUOI HOC",
                "KIEN THUC TIEN QUYET",
                "BAI GIANG CHI TIET",
                "VI DU MINH HOA",
                "BAI TAP THUC HANH VA LOI GIAI",
                "TOM TAT VA GHI NHO"
        };
        String[] displaySections = {
                "Mục tiêu buổi học",
                "Kiến thức tiên quyết",
                "Bài giảng chi tiết",
                "Ví dụ minh họa",
                "Bài tập thực hành và lời giải",
                "Tóm tắt và ghi nhớ"
        };
        int[] minimumSectionLengths = {40, 40, 500, 150, 200, 80};
        int[] sectionPositions = new int[requiredSections.length];
        for (int i = 0; i < requiredSections.length; i++) {
            sectionPositions[i] = normalized.indexOf(requiredSections[i]);
            if (sectionPositions[i] < 0) {
                return "Bài giảng thiếu mục: " + displaySections[i] + ".";
            }
        }
        for (int i = 0; i < requiredSections.length; i++) {
            int contentStart = sectionPositions[i] + requiredSections[i].length();
            int contentEnd = i + 1 < requiredSections.length
                    ? sectionPositions[i + 1]
                    : normalized.length();
            if (contentEnd - contentStart < minimumSectionLengths[i]) {
                return "Mục " + displaySections[i] + " chưa có nội dung giải thích đủ chi tiết.";
            }
        }
        return null;
    }
}
