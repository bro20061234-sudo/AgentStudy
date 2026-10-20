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
        if (detailedSyllabus == null || detailedSyllabus.length() < 300) {
            return "Bài giảng quá ngắn, hiện chỉ giống đề cương tóm tắt.";
        }
        String normalized = java.text.Normalizer.normalize(detailedSyllabus, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(java.util.Locale.ROOT);
        String[][] sectionAliases = {
                {"MUC TIEU BUOI HOC", "MUC TIEU BAI HOC", "MUC TIEU"},
                {"KIEN THUC TIEN QUYET", "KIEN THUC TRONG TAM", "KIEN THUC NEN TANG", "KIEN THUC"},
                {"BAI GIANG CHI TIET", "NOI DUNG BAI GIANG", "BAI GIANG", "NOI DUNG CHI TIET", "NOI DUNG"},
                {"VI DU MINH HOA", "VI DU"},
                {"BAI TAP THUC HANH VA LOI GIAI", "BAI TAP VA LOI GIAI", "BAI TAP THUC HANH", "BAI TAP"},
                {"TOM TAT VA GHI NHO", "GHI NHO VA TOM TAT", "GHI NHO", "TOM TAT", "TONG KET"}
        };
        String[] displaySections = {
                "Mục tiêu buổi học",
                "Kiến thức tiên quyết",
                "Bài giảng chi tiết",
                "Ví dụ minh họa",
                "Bài tập thực hành và lời giải",
                "Tóm tắt và ghi nhớ"
        };
        int[] minimumSectionLengths = {20, 20, 200, 50, 50, 20};
        int[] sectionPositions = new int[sectionAliases.length];
        int[] matchLengths = new int[sectionAliases.length];

        for (int i = 0; i < sectionAliases.length; i++) {
            sectionPositions[i] = -1;
            for (String alias : sectionAliases[i]) {
                int pos = normalized.indexOf(alias);
                if (pos >= 0) {
                    sectionPositions[i] = pos;
                    matchLengths[i] = alias.length();
                    break;
                }
            }
            if (sectionPositions[i] < 0) {
                return "Bài giảng thiếu mục: " + displaySections[i] + ".";
            }
        }

        // Kiểm tra thứ tự và độ dài các mục
        for (int i = 0; i < sectionPositions.length; i++) {
            int contentStart = sectionPositions[i] + matchLengths[i];
            int contentEnd = normalized.length();
            for (int j = 0; j < sectionPositions.length; j++) {
                if (sectionPositions[j] > sectionPositions[i] && sectionPositions[j] < contentEnd) {
                    contentEnd = sectionPositions[j];
                }
            }
            if (contentEnd - contentStart < minimumSectionLengths[i]) {
                return "Mục " + displaySections[i] + " chưa có nội dung giải thích đủ chi tiết.";
            }
        }
        return null;
    }
}
