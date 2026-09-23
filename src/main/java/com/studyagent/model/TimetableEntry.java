package com.studyagent.model;

public class TimetableEntry {
    private String id;
    private long chatId;
    private String dayOfWeek;
    private String startTime;
    private String endTime;
    private String subjectName;
    private String room;
    private String note;

    public TimetableEntry() {}

    public TimetableEntry(String dayOfWeek, String startTime, String endTime, String subjectName, String room, String note) {
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.subjectName = subjectName;
        this.room = room;
        this.note = note;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public long getChatId() { return chatId; }
    public void setChatId(long chatId) { this.chatId = chatId; }

    public String getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(String dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    @Override
    public String toString() {
        return String.format("[%s] %s - %s: %s (%s)", dayOfWeek, startTime, endTime, subjectName, room != null ? room : "N/A");
    }
}
