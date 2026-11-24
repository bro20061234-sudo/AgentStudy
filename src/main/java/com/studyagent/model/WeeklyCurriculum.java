package com.studyagent.model;

public class WeeklyCurriculum {
    private String id;
    private long chatId;
    private String subjectName;
    private int weekNumber;
    private String chapterTitle;
    private String detailedSyllabus;
    private String sourceText;
    private String contentStatus = "LEGACY";
    public String getSourceText() { return sourceText; }
    public void setSourceText(String sourceText) { this.sourceText = sourceText; }
    public String getContentStatus() { return contentStatus; }
    public void setContentStatus(String status) { this.contentStatus = status; }
    public boolean isChapter() { return sourceText != null && !sourceText.isBlank(); }

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

    /**
     * Kiểm tra mềm: chỉ cảnh báo nếu gần như trống.
     * Không bắt buộc đủ 6 mục dài (dễ fail với PDF lớn / model lite).
     */
    public String getDetailedLessonIssue() {
        if (detailedSyllabus == null || detailedSyllabus.isBlank()) {
            return "Chưa có nội dung bài học.";
        }
        if (detailedSyllabus.trim().length() < 80) {
            return "Nội dung quá ngắn (< 80 ký tự).";
        }
        return null;
    }
}
