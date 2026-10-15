package com.studyagent.model;

public class StudySession {
    private String id;
    private long chatId;
    private String dayOfWeek;
    private String scheduledDate;
    private String startTime;
    private String endTime;
    private String subjectName;
    private String topicOverview;
    private String preparationMaterials;
    private boolean isReminded;
    private boolean isCompleted;
    private int sessionIndex;

    public StudySession() {}

    public StudySession(String dayOfWeek, String startTime, String endTime, String subjectName, String topicOverview, String preparationMaterials) {
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.subjectName = subjectName;
        this.topicOverview = topicOverview;
        this.preparationMaterials = preparationMaterials;
        this.isReminded = false;
        this.isCompleted = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public long getChatId() { return chatId; }
    public void setChatId(long chatId) { this.chatId = chatId; }

    public String getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(String dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public String getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(String scheduledDate) { this.scheduledDate = scheduledDate; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public String getTopicOverview() { return topicOverview; }
    public void setTopicOverview(String topicOverview) { this.topicOverview = topicOverview; }

    public String getPreparationMaterials() { return preparationMaterials; }
    public void setPreparationMaterials(String preparationMaterials) { this.preparationMaterials = preparationMaterials; }

    public boolean isReminded() { return isReminded; }
    public void setReminded(boolean reminded) { isReminded = reminded; }

    public boolean isCompleted() { return isCompleted; }
    public void setCompleted(boolean completed) { isCompleted = completed; }

    public int getSessionIndex() { return sessionIndex; }
    public void setSessionIndex(int sessionIndex) { this.sessionIndex = sessionIndex; }

    @Override
    public String toString() {
        return String.format("[%s %s - %s] Tự học: %s | Nội dung: %s", dayOfWeek, startTime, endTime, subjectName, topicOverview);
    }
}
