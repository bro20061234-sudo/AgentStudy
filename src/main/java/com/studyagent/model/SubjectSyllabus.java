package com.studyagent.model;

public class SubjectSyllabus {
    private String id;
    private long chatId;
    private String subjectName;
    private String overview;
    private String syllabusContent;
    private String examTips;
    private String recommendedMaterials;

    public SubjectSyllabus() {}

    public SubjectSyllabus(String subjectName, String overview, String syllabusContent, String examTips, String recommendedMaterials) {
        this.subjectName = subjectName;
        this.overview = overview;
        this.syllabusContent = syllabusContent;
        this.examTips = examTips;
        this.recommendedMaterials = recommendedMaterials;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public long getChatId() { return chatId; }
    public void setChatId(long chatId) { this.chatId = chatId; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public String getOverview() { return overview; }
    public void setOverview(String overview) { this.overview = overview; }

    public String getSyllabusContent() { return syllabusContent; }
    public void setSyllabusContent(String syllabusContent) { this.syllabusContent = syllabusContent; }

    public String getExamTips() { return examTips; }
    public void setExamTips(String examTips) { this.examTips = examTips; }

    public String getRecommendedMaterials() { return recommendedMaterials; }
    public void setRecommendedMaterials(String recommendedMaterials) { this.recommendedMaterials = recommendedMaterials; }
}
