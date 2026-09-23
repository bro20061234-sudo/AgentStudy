package com.studyagent;

import com.studyagent.ai.GeminiService;
import com.studyagent.model.StudySession;
import com.studyagent.model.TimetableEntry;
import com.studyagent.scheduler.StudyOptimizer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StudyOptimizerTest {

    @Test
    public void testOptimizerSchedulesSelfStudy() {
        GeminiService geminiService = new GeminiService("");
        StudyOptimizer optimizer = new StudyOptimizer(geminiService);

        List<TimetableEntry> entries = new ArrayList<>();
        entries.add(new TimetableEntry("Thứ 2", "07:00", "09:00", "Lập trình Python", "A2-301", ""));
        entries.add(new TimetableEntry("Thứ 3", "13:00", "16:00", "Mạng máy tính", "B1-204", ""));

        List<StudySession> sessions = optimizer.generateOptimalSchedule(entries);

        assertNotNull(sessions);
        assertEquals(2, sessions.size());

        StudySession first = sessions.get(0);
        assertEquals("Thứ 2", first.getDayOfWeek());
        assertEquals("19:30", first.getStartTime());
        assertEquals("21:00", first.getEndTime());
        assertEquals("Lập trình Python", first.getSubjectName());
        assertTrue(first.getTopicOverview().contains("Lập trình Python"));
    }

    @Test
    public void testConflictAvoidance() {
        GeminiService geminiService = new GeminiService("");
        StudyOptimizer optimizer = new StudyOptimizer(geminiService);

        List<TimetableEntry> entries = new ArrayList<>();
        entries.add(new TimetableEntry("Thứ 2", "19:00", "21:30", "Thực hành Chuyên đề", "Lab", ""));

        List<StudySession> sessions = optimizer.generateOptimalSchedule(entries);
        assertEquals(0, sessions.size(), "Phải loại bỏ phiên tự học trùng giờ với buổi học trên trường");
    }
}
