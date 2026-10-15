package com.studyagent;

import com.studyagent.ai.GeminiService;
import com.studyagent.model.StudySession;
import com.studyagent.model.TimetableEntry;
import com.studyagent.parser.DocumentParser;
import com.studyagent.scheduler.StudyOptimizer;
import com.studyagent.repository.StudyRepository;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
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
        assertEquals("20:30", first.getEndTime());
        assertEquals("Lập trình Python", first.getSubjectName());
        assertTrue(first.getTopicOverview().contains("Lập trình Python"));
    }

    @Test
    public void testMultipleSubjectsAreScheduledBackToBack() {
        GeminiService geminiService = new GeminiService("");
        StudyOptimizer optimizer = new StudyOptimizer(geminiService);

        List<TimetableEntry> entries = List.of(
                new TimetableEntry("Thứ 2", "07:00", "09:00", "Cơ sở dữ liệu", "A1", ""),
                new TimetableEntry("Thứ 2", "09:00", "11:00", "Lập trình", "A2", ""));

        List<StudySession> sessions = optimizer.generateOptimalSchedule(entries);

        assertEquals(2, sessions.size());
        assertEquals("19:30", sessions.get(0).getStartTime());
        assertEquals("20:30", sessions.get(0).getEndTime());
        assertEquals("20:30", sessions.get(1).getStartTime());
        assertEquals("21:30", sessions.get(1).getEndTime());
    }

    @Test
    public void testStudyStartsThirtyMinutesAfterEveningClass() {
        GeminiService geminiService = new GeminiService("");
        StudyOptimizer optimizer = new StudyOptimizer(geminiService);

        List<TimetableEntry> entries = List.of(
                new TimetableEntry("Thứ 2", "07:00", "09:00", "Cơ sở dữ liệu", "A1", ""),
                new TimetableEntry("Thứ 2", "19:00", "20:30", "Thực hành code", "Lab", ""));

        List<StudySession> sessions = optimizer.generateOptimalSchedule(entries);

        assertEquals(1, sessions.size());
        assertEquals("21:00", sessions.get(0).getStartTime());
        assertEquals("22:00", sessions.get(0).getEndTime());
    }

    @Test
    public void testOverflowIsMovedToNextDayAndScheduledBeforeThatDaysSubjects() {
        GeminiService geminiService = new GeminiService("");
        StudyOptimizer optimizer = new StudyOptimizer(geminiService);

        List<TimetableEntry> entries = List.of(
                new TimetableEntry("Thứ 2", "07:00", "09:00", "Môn 1", "", ""),
                new TimetableEntry("Thứ 2", "09:00", "11:00", "Môn 2", "", ""),
                new TimetableEntry("Thứ 2", "13:00", "15:00", "Môn 3", "", ""),
                new TimetableEntry("Thứ 2", "15:00", "17:00", "Môn 4", "", ""),
                new TimetableEntry("Thứ 3", "07:00", "09:00", "Môn thứ 3", "", ""));

        List<StudySession> sessions = optimizer.generateOptimalSchedule(entries);

        assertEquals(5, sessions.size());
        assertEquals("Môn 1", sessions.get(0).getSubjectName());
        assertEquals("Môn 2", sessions.get(1).getSubjectName());
        assertEquals("Môn 3", sessions.get(2).getSubjectName());
        assertEquals("Môn 4", sessions.get(3).getSubjectName());
        assertEquals("Thứ 3", sessions.get(3).getDayOfWeek());
        assertEquals("19:30", sessions.get(3).getStartTime());
        assertEquals("Môn thứ 3", sessions.get(4).getSubjectName());
        assertEquals("20:30", sessions.get(4).getStartTime());
    }

    @Test
    public void testSundayOverflowIsMovedToNextMonday() {
        GeminiService geminiService = new GeminiService("");
        StudyOptimizer optimizer = new StudyOptimizer(geminiService);
        List<TimetableEntry> entries = List.of(
                new TimetableEntry("Chủ Nhật", "07:00", "08:00", "Ôn bài", "", ""),
                new TimetableEntry("Chủ Nhật", "19:00", "22:30", "Thực hành tối", "", ""));

        List<StudySession> sessions = optimizer.generateOptimalSchedule(entries);

        assertEquals(1, sessions.size());
        assertEquals("Thứ 2", sessions.get(0).getDayOfWeek());
        assertEquals("19:30", sessions.get(0).getStartTime());
        assertEquals("20:00", sessions.get(0).getEndTime());
    }

    @Test
    public void testAIProvidedOverlappingTimesAreReplacedWithAvailableSlots() {
        GeminiService geminiService = new GeminiService("valid-key") {
            @Override
            public List<StudySession> optimizeStudyScheduleWithAI(List<TimetableEntry> schoolSchedule) {
                StudySession first = new StudySession("Thứ 2", "19:30", "21:00", "Cơ sở dữ liệu", "", "");
                StudySession second = new StudySession("Thứ 2", "19:30", "21:00", "Lập trình", "", "");
                return List.of(first, second);
            }
        };
        StudyOptimizer optimizer = new StudyOptimizer(geminiService);
        List<TimetableEntry> entries = List.of(
                new TimetableEntry("Thứ 2", "07:00", "09:00", "Cơ sở dữ liệu", "", ""),
                new TimetableEntry("Thứ 2", "09:00", "11:00", "Lập trình", "", ""));

        List<StudySession> sessions = optimizer.generateOptimalSchedule(entries);

        assertEquals(2, sessions.size());
        assertEquals("19:30", sessions.get(0).getStartTime());
        assertEquals("20:30", sessions.get(0).getEndTime());
        assertEquals("20:30", sessions.get(1).getStartTime());
        assertEquals("21:30", sessions.get(1).getEndTime());
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

    @Test
    public void testConvertPeriodsToTime() {
        String[] t1 = GeminiService.convertPeriodsToTime(1, 2);
        assertEquals("07:00", t1[0]);
        assertEquals("09:00", t1[1]);

        String[] t2 = GeminiService.convertPeriodsToTime(2, 3);
        assertEquals("08:00", t2[0]);
        assertEquals("11:00", t2[1]);

        String[] t6 = GeminiService.convertPeriodsToTime(6, 4);
        assertEquals("12:30", t6[0]);
        assertEquals("16:30", t6[1]);

        String[] t10 = GeminiService.convertPeriodsToTime(10, 2);
        assertEquals("16:30", t10[0]);
        assertEquals("18:30", t10[1]);

        String[] t18 = GeminiService.convertPeriodsToTime(18, 1);
        assertEquals("18:00", t18[0]);
        assertEquals("19:00", t18[1]);
    }

    @Test
    public void testUniversityTableParsingFromPtitFormat() {
        GeminiService geminiService = new GeminiService("");
        String ptitSample = "Cổng thông tin quản lý đào tạo\n" +
                "Thời khóa biểu dạng học kỳ\n" +
                "Học kỳ 1 - Năm học 2026 - 2027\n" +
                "Mã MH Tên môn học Nhóm tổ Số tín chỉ Lớp Thứ Tiết bắt đầu Số tiết Phòng Giảng viên Thời gian học\n" +
                "BAS1153 Lịch sử Đảng cộng sản Việt Nam 13 2 D24CQCN01-B 7 7 1 302-A2 Đ.C.Thành 17/10/26 đến 21/11/26\n" +
                "BAS1160 Tiếng Anh (Course 3 Plus) 36 2 D24-036 4 10 2 G05-A2 T.P.Thảo 19/08/26 đến 26/08/26\n" +
                "INT1313 Cơ sở dữ liệu 06-01 3 D24CQCN01-B 6 6 4 DB-LAB N.V.Dũng 30/10/26 đến 30/10/26\n" +
                "INT1319 Hệ điều hành 01-01 3 D24CQCN01-B 2 2 3 509-A3 T.Q.Dương 28/09/26 đến 28/09/26";

        List<TimetableEntry> entries = geminiService.parseUniversityTableText(ptitSample);
        assertNotNull(entries);
        assertEquals(4, entries.size());

        // Môn 1: Lịch sử Đảng (Thứ 7, tiết 7, 1 tiết, phòng 302-A2)
        assertEquals("Thứ 7", entries.get(0).getDayOfWeek());
        assertTrue(entries.get(0).getSubjectName().toLowerCase().contains("lịch sử"));
        assertEquals("302-A2", entries.get(0).getRoom());

        // Môn 2: Tiếng Anh (Thứ 4, tiết 10, 2 tiết, phòng G05-A2)
        assertEquals("Thứ 4", entries.get(1).getDayOfWeek());
        assertTrue(entries.get(1).getSubjectName().toLowerCase().contains("tiếng anh"));
        assertEquals("16:30", entries.get(1).getStartTime());
        assertEquals("18:30", entries.get(1).getEndTime());
        assertEquals("G05-A2", entries.get(1).getRoom());

        // Môn 3: Cơ sở dữ liệu (Thứ 6, tiết 6, 4 tiết, phòng DB-LAB)
        assertEquals("Thứ 6", entries.get(2).getDayOfWeek());
        assertTrue(entries.get(2).getSubjectName().toLowerCase().contains("cơ sở dữ liệu"));
        assertEquals("12:30", entries.get(2).getStartTime());
        assertEquals("16:30", entries.get(2).getEndTime());
        assertEquals("DB-LAB", entries.get(2).getRoom());

        // Môn 4: Hệ điều hành (Thứ 2, tiết 2, 3 tiết, phòng 509-A3)
        assertEquals("Thứ 2", entries.get(3).getDayOfWeek());
        assertTrue(entries.get(3).getSubjectName().toLowerCase().contains("hệ điều hành"));
        assertEquals("08:00", entries.get(3).getStartTime());
        assertEquals("11:00", entries.get(3).getEndTime());
        assertEquals("509-A3", entries.get(3).getRoom());
    }

    @Test
    public void testIsTimetableDocumentDetection() {
        com.studyagent.bot.TelegramBotClient botClient = new com.studyagent.bot.TelegramBotClient("");
        com.studyagent.repository.StudyRepository repo = new com.studyagent.repository.StudyRepository();
        com.studyagent.bot.StudyBotController controller = new com.studyagent.bot.StudyBotController(
                botClient, new GeminiService(""), repo, new StudyOptimizer(new GeminiService(""))
        );

        assertTrue(controller.isTimetableDocument("TKB_Ky1_2026.pdf", new byte[0]));
        assertTrue(controller.isTimetableDocument("thoikhoabieu.pdf", new byte[0]));
        assertTrue(controller.isTimetableDocument("my_schedule.docx", new byte[0]));
        assertFalse(controller.isTimetableDocument("Giao_trinh_Co_so_du_lieu.pdf", new byte[0]));
    }

    @Test
    public void testWeeklyCapMax2HoursPerSubject() {
        StudyOptimizer optimizer = new StudyOptimizer(new GeminiService(""));
        // Môn "OOP" học 2 buổi trong tuần, mỗi buổi 3 tiết (tổng 6 tiết = 180p nếu không giới hạn)
        List<TimetableEntry> entries = List.of(
                new TimetableEntry("Thứ 2", "08:00", "11:00", "OOP", "304-A2", "3 tiết"),
                new TimetableEntry("Thứ 4", "08:00", "11:00", "OOP", "304-A2", "3 tiết")
        );

        List<StudySession> sessions = optimizer.generateOptimalSchedule(entries);

        // Tổng thời gian tự học của môn OOP trên tuần không được vượt quá 120 phút (2 tiếng)
        int totalStudyMinutes = 0;
        for (StudySession s : sessions) {
            if (s.getSubjectName().equalsIgnoreCase("OOP")) {
                int start = StudyOptimizer.parseMinutes(s.getStartTime());
                int end = StudyOptimizer.parseMinutes(s.getEndTime());
                totalStudyMinutes += (end - start);
            }
        }
        assertEquals(120, totalStudyMinutes, "Môn học nhiều tiết phải bị giới hạn tối đa 120 phút (2 tiếng)/tuần");
    }

    @Test
    public void testSubjectStudyMinutesProportionalToPeriods() {
        StudyOptimizer optimizer = new StudyOptimizer(new GeminiService(""));
        // Môn 1 tiết (30 phút tự học) và Môn 3 tiết (90 phút tự học)
        List<TimetableEntry> entries = List.of(
                new TimetableEntry("Thứ 2", "07:00", "08:00", "Môn 1 Tiết", "A1", "1 tiết"),
                new TimetableEntry("Thứ 3", "08:00", "11:00", "Môn 3 Tiết", "A2", "3 tiết")
        );

        List<StudySession> sessions = optimizer.generateOptimalSchedule(entries);
        assertEquals(2, sessions.size());

        int duration1 = StudyOptimizer.parseMinutes(sessions.get(0).getEndTime()) - StudyOptimizer.parseMinutes(sessions.get(0).getStartTime());
        int duration2 = StudyOptimizer.parseMinutes(sessions.get(1).getEndTime()) - StudyOptimizer.parseMinutes(sessions.get(1).getStartTime());

        assertEquals(30, duration1, "Môn 1 tiết phải tự học 30 phút");
        assertEquals(90, duration2, "Môn 3 tiết phải tự học 90 phút");
    }

    @Test
    public void testWeekActiveFilteringOnlySchedulesActiveSubjects() {
        StudyOptimizer optimizer = new StudyOptimizer(new GeminiService(""));
        // Lịch sử Đảng: học từ 17/10/26 đến 21/11/26
        // Tiếng Anh: chỉ học từ 19/08/26 đến 26/08/26 (tháng 8)
        List<TimetableEntry> entries = List.of(
                new TimetableEntry("Thứ 7", "07:00", "09:00", "Lịch sử Đảng", "302-A2", "17/10/26 đến 21/11/26"),
                new TimetableEntry("Thứ 4", "16:30", "18:30", "Tiếng Anh", "G05-A2", "19/08/26 đến 26/08/26")
        );

        // Tuần 20/10/2026: Chỉ có Lịch sử Đảng active, Tiếng Anh đã kết thúc
        java.time.LocalDate targetDate = java.time.LocalDate.of(2026, 10, 20);
        List<StudySession> sessions = optimizer.generateOptimalScheduleForWeek(entries, targetDate);

        assertEquals(1, sessions.size());
        assertEquals("Lịch sử Đảng", sessions.get(0).getSubjectName());
    }

    @Test
    public void testSemesterScheduleCreatesDatedSessionsForEachActiveWeek() {
        StudyOptimizer optimizer = new StudyOptimizer(new GeminiService(""));
        List<TimetableEntry> entries = List.of(
                new TimetableEntry("Thứ 2", "08:00", "11:00", "Lập trình", "A1", "3 tiết | 01/10/26 đến 22/10/26"),
                new TimetableEntry("Thứ 3", "08:00", "10:00", "Cơ sở dữ liệu", "A2", "2 tiết | 13/10/26 đến 27/10/26"));

        List<StudySession> sessions = optimizer.generateSemesterSchedule(entries);

        assertEquals(6, sessions.size());
        assertEquals(List.of("2026-10-05", "2026-10-12", "2026-10-19"),
                sessions.stream().filter(s -> s.getSubjectName().equals("Lập trình"))
                        .map(StudySession::getScheduledDate).toList());
        assertEquals(List.of("2026-10-13", "2026-10-20", "2026-10-27"),
                sessions.stream().filter(s -> s.getSubjectName().equals("Cơ sở dữ liệu"))
                        .map(StudySession::getScheduledDate).toList());
        assertTrue(sessions.stream().allMatch(s -> s.getScheduledDate() != null));
    }

    @Test
    public void testUniversityTableParserKeepsSeparateDateRangesForSameWeeklySlot() {
        GeminiService geminiService = new GeminiService("");
        String timetable = "BAS1153 Lịch sử Đảng cộng sản Việt Nam 13 2 D24CQCN01-B 7 7 1 302-A2 Đ.C.Thành 17/10/26 đến 21/11/26\n" +
                "BAS1153 Lịch sử Đảng cộng sản Việt Nam 13 2 D24CQCN01-B 7 7 1 302-A2 Đ.C.Thành 22/11/26 đến 29/11/26";

        List<TimetableEntry> entries = geminiService.parseUniversityTableText(timetable);

        assertEquals(2, entries.size());
        assertTrue(entries.get(0).getNote().contains("17/10/26 đến 21/11/26"));
        assertTrue(entries.get(1).getNote().contains("22/11/26 đến 29/11/26"));
    }

    @Test
    public void testTextbookStorageAndRetrieval() {
        StudyRepository repository = new StudyRepository();
        long testChatId = 99999L;
        String subject = "Hệ Quản Trị Cơ Sở Dữ Liệu";
        String fileName = "giaotrinh_csdl.pdf";
        String content = "Chương 1: Tổng quan CSDL. Chương 2: Mô hình thực thể E-R.";

        repository.deleteTextbook(testChatId, subject);
        assertTrue(repository.saveTextbook(testChatId, subject, fileName, content));

        StudyRepository.TextbookItem item = repository.getTextbook(testChatId, subject);
        assertNotNull(item);
        assertEquals(subject, item.getSubjectName());
        assertEquals(fileName, item.getFileName());
        assertEquals(content, item.getContent());

        List<StudyRepository.TextbookItem> all = repository.getAllTextbooks(testChatId);
        assertFalse(all.isEmpty());
        assertTrue(all.stream().anyMatch(t -> t.getSubjectName().equalsIgnoreCase(subject)));

        com.studyagent.model.WeeklyCurriculum lesson =
                new com.studyagent.model.WeeklyCurriculum(testChatId, subject, 1, "Chương 1", "Bài giảng cũ");
        assertTrue(repository.saveWeeklyCurricula(testChatId, subject, List.of(lesson)));

        assertTrue(repository.saveTextbook(testChatId, subject, "same_content.docx", content));
        assertEquals(1, repository.getWeeklyCurricula(testChatId, subject).size(),
                "Nạp lại cùng nội dung không được xóa đề cương hợp lệ.");

        assertTrue(repository.saveTextbook(testChatId, subject, "updated.docx", content + " Nội dung mới."));
        assertTrue(repository.getWeeklyCurricula(testChatId, subject).isEmpty(),
                "Thay đổi giáo trình phải xóa đề cương cũ để tránh dùng nhầm.");

        assertTrue(repository.saveWeeklyCurricula(testChatId, subject, List.of(lesson)));
        assertTrue(repository.deleteTextbook(testChatId, subject));
        assertNull(repository.getTextbook(testChatId, subject));
        assertTrue(repository.getWeeklyCurricula(testChatId, subject).isEmpty(),
                "Xóa giáo trình cũng phải xóa đề cương được tạo từ giáo trình đó.");
    }

    @Test
    public void testCurriculumUsesExactStudySessionCount() {
        GeminiService geminiService = new GeminiService("");

        assertThrows(IllegalStateException.class,
                () -> geminiService.generateCurriculumForSessions("Toán", "Chương 1; Chương 2", 3));
        assertThrows(IllegalArgumentException.class,
                () -> geminiService.generateCurriculumForSessions("Toán", "Giáo trình", 0));
    }

    @Test
    public void testDetailedLessonQualityRejectsShortOutlines() {
        com.studyagent.model.WeeklyCurriculum lesson = new com.studyagent.model.WeeklyCurriculum();
        lesson.setDetailedSyllabus("Mục tiêu: hiểu khái niệm và làm bài.");
        assertNotNull(lesson.getDetailedLessonIssue());

        lesson.setDetailedSyllabus(
                "MỤC TIÊU BUỔI HỌC\n" + "Nắm được kiến thức và vận dụng vào bài tập. ".repeat(3) +
                "KIẾN THỨC TIÊN QUYẾT\n" + "Ôn lại các khái niệm nền tảng trước khi học. ".repeat(3) +
                "BÀI GIẢNG CHI TIẾT\n" + "Giải thích nội dung theo từng bước. ".repeat(45) +
                "VÍ DỤ MINH HỌA\n" + "Phân tích ví dụ và trình bày cách giải cụ thể. ".repeat(6) +
                "BÀI TẬP THỰC HÀNH VÀ LỜI GIẢI\n" + "Bài tập có đáp án, lời giải và giải thích từng bước. ".repeat(6) +
                "TÓM TẮT VÀ GHI NHỚ\n" + "Ghi nhớ các nguyên tắc chính và cách vận dụng. ".repeat(3));
        assertNull(lesson.getDetailedLessonIssue());
    }

    @Test
    public void testDocxParserExtractsTableContent() throws Exception {
        byte[] docxBytes;
        try (org.apache.poi.xwpf.usermodel.XWPFDocument document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Bài giảng hệ điều hành");
            org.apache.poi.xwpf.usermodel.XWPFTable table = document.createTable(1, 2);
            table.getRow(0).getCell(0).setText("Tiến trình");
            table.getRow(0).getCell(1).setText("PCB lưu trạng thái");
            document.write(output);
            docxBytes = output.toByteArray();
        }

        String extractedText = DocumentParser.extractTextFromDocx(docxBytes);

        assertTrue(extractedText.contains("Bài giảng hệ điều hành"));
        assertTrue(extractedText.contains("Tiến trình | PCB lưu trạng thái"));
    }

    @Test
    public void testStudySessionCompletionAndEdit() {
        StudyRepository repository = new StudyRepository();
        long testChatId = 88888L;

        StudySession session = new StudySession();
        String id = "test-session-123";
        session.setId(id);
        session.setChatId(testChatId);
        session.setDayOfWeek("Thứ 2");
        session.setStartTime("19:00");
        session.setEndTime("20:30");
        session.setSubjectName("Lập Trình Web");
        session.setTopicOverview("HTML/CSS/JS");
        session.setCompleted(false);

        StudySession secondSession = new StudySession("Thứ 5", "21:00", "22:00",
                "Lập Trình Web", "DOM", "Bài tập");
        secondSession.setId("test-session-456");
        repository.saveStudySessions(testChatId, List.of(session, secondSession));

        List<StudySession> loaded = repository.getStudySessions(testChatId);
        assertEquals(2, loaded.size());
        assertFalse(loaded.get(0).isCompleted());
        assertEquals(1, loaded.get(0).getSessionIndex());
        assertEquals(2, loaded.get(1).getSessionIndex());

        // Toggle completed = true
        repository.toggleStudySessionCompleted(id, true);
        loaded = repository.getStudySessions(testChatId);
        assertTrue(loaded.get(0).isCompleted());

        // Update schedule time
        session.setDayOfWeek("Thứ 5");
        session.setStartTime("20:00");
        session.setEndTime("21:30");
        repository.updateStudySession(session);

        loaded = repository.getStudySessions(testChatId);
        assertEquals("Thứ 5", loaded.get(0).getDayOfWeek());
        assertEquals("20:00", loaded.get(0).getStartTime());
        assertEquals("21:30", loaded.get(0).getEndTime());
    }

    @Test
    public void testGetDistinctSubjectsFromTimetable() {
        StudyRepository repository = new StudyRepository();
        long testChatId = 77777L;

        List<TimetableEntry> entries = List.of(
                new TimetableEntry("Thứ 2", "07:00", "09:00", "Toán rời rạc", "101-A2", "2 tiết"),
                new TimetableEntry("Thứ 4", "07:00", "09:00", "Toán rời rạc", "101-A2", "2 tiết"),
                new TimetableEntry("Thứ 6", "13:00", "16:00", "Lập trình C++", "202-A2", "3 tiết")
        );
        repository.saveTimetableEntries(testChatId, entries);

        List<String> subjects = repository.getDistinctSubjects(testChatId);
        assertEquals(2, subjects.size());
        assertTrue(subjects.contains("Toán rời rạc"));
        assertTrue(subjects.contains("Lập trình C++"));
    }

    @Test
    public void testContestSetsArePreservedAndGroupedBySubject() {
        StudyRepository repository = new StudyRepository();
        long testChatId = System.nanoTime();
        List<com.studyagent.model.QuizQuestion> firstContest = List.of(
                new com.studyagent.model.QuizQuestion("Question 1", List.of("A. one", "B. two", "C. three", "D. four"), "A", "Explanation"));
        List<com.studyagent.model.QuizQuestion> secondContest = List.of(
                new com.studyagent.model.QuizQuestion("Question 2", List.of("A. one", "B. two", "C. three", "D. four"), "B", "Explanation"));

        repository.saveQuizQuestions(testChatId, "Cơ sở dữ liệu", "CONTEST", 1, firstContest);
        repository.saveQuizQuestions(testChatId, "Cơ sở dữ liệu", "CONTEST", 1, secondContest);

        var grouped = repository.getAllContestsGroupedBySubject(testChatId);
        assertEquals(1, grouped.size());
        assertEquals(List.of(1, 2), grouped.get("Cơ sở dữ liệu").keySet().stream().toList());
        assertEquals("Question 1", grouped.get("Cơ sở dữ liệu").get(1).get(0).getQuestion());
        assertEquals("Question 2", grouped.get("Cơ sở dữ liệu").get(2).get(0).getQuestion());
    }
}
