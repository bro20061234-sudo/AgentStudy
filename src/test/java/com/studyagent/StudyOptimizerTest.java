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

    @Test
    public void testTimetableExtractionDoesNotReturnMockWithoutApiKey() {
        GeminiService geminiService = new GeminiService("");

        List<TimetableEntry> textEntries = geminiService.extractTimetableFromText("Thứ 2 7h-9h Python");
        assertFalse(textEntries.isEmpty());
        assertEquals("Python", textEntries.get(0).getSubjectName());
        assertTrue(geminiService.extractTimetableFromImage(new byte[0], "image/jpeg").isEmpty());
    }

    @Test
    public void testFallbackParsesTimetableTextWithMultipleDaysAndSubjects() {
        GeminiService geminiService = new GeminiService("valid-key");
        String input = "thứ 2: sáng 7h-9h lập trình hướng đối tượng, 9h-12h cơ sở dữ liệu, chiều 16h-19h python; thứ 3: sáng 7h-9h hệ điều hành, 9h-12h mạng máy tính";

        List<TimetableEntry> entries = geminiService.extractTimetableFromText(input);

        assertNotNull(entries);
        assertEquals(5, entries.size());
        assertEquals("Thứ 2", entries.get(0).getDayOfWeek());
        assertTrue(entries.get(0).getSubjectName().toLowerCase().contains("lập trình"));
        assertTrue(entries.get(2).getSubjectName().toLowerCase().contains("python"));
        assertEquals("Thứ 3", entries.get(3).getDayOfWeek());
        assertTrue(entries.get(3).getSubjectName().toLowerCase().contains("hệ điều hành"));
    }

    @Test
    public void testFallbackParsesNewlineSeparatedDaysWithoutForcingThứ2() {
        GeminiService geminiService = new GeminiService("valid-key");
        String input = "thứ 2: sáng 7h-9h lập trình hướng đối tượng\n" +
                "thứ 3: sáng 7h-9h hệ điều hành\n" +
                "thứ 4: sáng 7h-9h tiếng anh";

        List<TimetableEntry> entries = geminiService.extractTimetableFromText(input);

        assertNotNull(entries);
        assertEquals(3, entries.size());
        assertEquals("Thứ 2", entries.get(0).getDayOfWeek());
        assertEquals("Thứ 3", entries.get(1).getDayOfWeek());
        assertEquals("Thứ 4", entries.get(2).getDayOfWeek());
    }
}
