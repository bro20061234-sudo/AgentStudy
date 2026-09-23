package com.studyagent.scheduler;

import com.studyagent.ai.GeminiService;
import com.studyagent.model.StudySession;
import com.studyagent.model.TimetableEntry;

import java.util.ArrayList;
import java.util.List;

public class StudyOptimizer {
    private final GeminiService geminiService;

    public StudyOptimizer(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    public List<StudySession> generateOptimalSchedule(List<TimetableEntry> schoolSchedule) {
        if (schoolSchedule == null || schoolSchedule.isEmpty()) {
            return new ArrayList<>();
        }

        if (geminiService.hasApiKey()) {
            List<StudySession> aiSessions = geminiService.optimizeStudyScheduleWithAI(schoolSchedule);
            if (!aiSessions.isEmpty()) {
                return filterConflicts(aiSessions, schoolSchedule);
            }
        }

        return generateHeuristicSchedule(schoolSchedule);
    }

    private List<StudySession> generateHeuristicSchedule(List<TimetableEntry> schoolSchedule) {
        List<StudySession> result = new ArrayList<>();

        for (TimetableEntry entry : schoolSchedule) {
            String day = entry.getDayOfWeek();
            String subject = entry.getSubjectName();

            StudySession session = new StudySession();
            session.setDayOfWeek(day);
            session.setStartTime("19:30");
            session.setEndTime("21:00");
            session.setSubjectName(subject);
            session.setTopicOverview(String.format("Ôn tập kiến thức vừa học trên lớp môn %s (Pomodoro: 2 phiên 40p)", subject));
            session.setPreparationMaterials(String.format("1. Đọc lại slide buổi học.\n2. Làm bài tập ứng dụng.\n3. Ghi lại các thắc mắc chuẩn bị hỏi giảng viên."));

            result.add(session);
        }

        return filterConflicts(result, schoolSchedule);
    }

    private List<StudySession> filterConflicts(List<StudySession> sessions, List<TimetableEntry> schoolSchedule) {
        List<StudySession> validSessions = new ArrayList<>();
        for (StudySession session : sessions) {
            boolean conflict = false;
            for (TimetableEntry entry : schoolSchedule) {
                if (session.getDayOfWeek().equalsIgnoreCase(entry.getDayOfWeek())) {
                    if (isTimeOverlap(session.getStartTime(), session.getEndTime(), entry.getStartTime(), entry.getEndTime())) {
                        conflict = true;
                        break;
                    }
                }
            }
            if (!conflict) {
                validSessions.add(session);
            }
        }
        return validSessions;
    }

    private boolean isTimeOverlap(String startA, String endA, String startB, String endB) {
        try {
            int a1 = parseMinutes(startA);
            int a2 = parseMinutes(endA);
            int b1 = parseMinutes(startB);
            int b2 = parseMinutes(endB);
            return Math.max(a1, b1) < Math.min(a2, b2);
        } catch (Exception e) {
            return false;
        }
    }

    private int parseMinutes(String hhmm) {
        String[] parts = hhmm.trim().split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }
}
