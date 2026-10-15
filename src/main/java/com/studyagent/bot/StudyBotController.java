package com.studyagent.bot;

import com.studyagent.ai.GeminiService;
import com.studyagent.model.StudySession;
import com.studyagent.model.SubjectSyllabus;
import com.studyagent.model.TimetableEntry;
import com.studyagent.repository.StudyRepository;
import com.studyagent.scheduler.StudyOptimizer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StudyBotController implements TelegramBotClient.TelegramUpdateListener {
    private final TelegramBotClient botClient;
    private final GeminiService geminiService;
    private final StudyRepository repository;
    private final StudyOptimizer optimizer;

    // Bộ đệm nhận nhiều ảnh liên tiếp
    private final java.util.Map<Long, java.util.List<byte[]>> photoBuffers = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<Long, java.util.concurrent.ScheduledFuture<?>> bufferTimers = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();

    // Lưu trạng thái contest đang diễn ra theo chatId
    private final java.util.Map<Long, ActiveContest> activeContests = new java.util.concurrent.ConcurrentHashMap<>();

    private static class ActiveContest {
        String subject;
        List<com.studyagent.model.QuizQuestion> questions;
        int currentIndex = 0;
        int correctCount = 0;
    }

    public StudyBotController(TelegramBotClient botClient, GeminiService geminiService, StudyRepository repository, StudyOptimizer optimizer) {
        this.botClient = botClient;
        this.geminiService = geminiService;
        this.repository = repository;
        this.optimizer = optimizer;
    }

    @Override
    public void onTextMessage(long chatId, String userName, String text) {
        String trimmed = text.trim();

        // Kiểm tra xem có đang trả lời Contest không (A, B, C, D)
        if (activeContests.containsKey(chatId) && (trimmed.equalsIgnoreCase("A") || trimmed.equalsIgnoreCase("B") || trimmed.equalsIgnoreCase("C") || trimmed.equalsIgnoreCase("D"))) {
            handleContestAnswer(chatId, trimmed.toUpperCase());
            return;
        }

        if (trimmed.equalsIgnoreCase("/start") || trimmed.equalsIgnoreCase("/help")) {
            sendWelcomeMessage(chatId, userName);
        } else if (trimmed.equalsIgnoreCase("/schedule") || trimmed.equalsIgnoreCase("/xemlich")) {
            showCurrentSchedule(chatId, null);
        } else if (trimmed.startsWith("/schedule ") || trimmed.startsWith("/xemlich ")) {
            String arg = trimmed.substring(trimmed.indexOf(" ") + 1).trim();
            showCurrentSchedule(chatId, arg);
        } else if (trimmed.equalsIgnoreCase("/syllabus") || trimmed.equalsIgnoreCase("/decuong")) {
            showAllSyllabuses(chatId);
        } else if (trimmed.startsWith("/decuong ") || trimmed.startsWith("/syllabus ")) {
            String subject = trimmed.substring(trimmed.indexOf(" ") + 1).trim();
            handleSyllabusRequest(chatId, subject);
        } else if (trimmed.equalsIgnoreCase("/contest")) {
            showSavedContests(chatId);
        } else if (trimmed.startsWith("/contest ") || trimmed.startsWith("/thithu")) {
            String subject = trimmed.contains(" ") ? trimmed.substring(trimmed.indexOf(" ") + 1).trim() : "";
            startContest(chatId, subject);
        } else if (trimmed.equalsIgnoreCase("/giaotrinh") || trimmed.equalsIgnoreCase("/textbook")) {
            showAllTextbooks(chatId, null);
        } else if (trimmed.startsWith("/giaotrinh ") || trimmed.startsWith("/textbook ")) {
            String subject = trimmed.substring(trimmed.indexOf(" ") + 1).trim();
            showAllTextbooks(chatId, subject);
        } else if (trimmed.startsWith("/done ") || trimmed.startsWith("/hoanthanh ")) {
            String arg = trimmed.substring(trimmed.indexOf(" ") + 1).trim();
            toggleSessionDone(chatId, arg);
        } else if (trimmed.startsWith("/study ") || trimmed.startsWith("/hoc ")) {
            String arg = trimmed.substring(trimmed.indexOf(" ") + 1).trim();
            studySessionEarly(chatId, arg);
        } else if (trimmed.startsWith("/quiz") || trimmed.startsWith("/ontap")) {
            String subject = trimmed.contains(" ") ? trimmed.substring(trimmed.indexOf(" ") + 1).trim() : "";
            handleQuizRequest(chatId, subject);
        } else if (looksLikeTimetable(trimmed)) {
            botClient.sendMessage(chatId, "🤖 Đang phân tích văn bản thời khóa biểu bằng AI Gemini, bạn vui lòng đợi trong giây lát...");
            List<TimetableEntry> entries = geminiService.extractTimetableFromText(trimmed);
            processTimetableEntries(chatId, entries);
        } else {
            botClient.sendMessage(chatId, "💡 Bạn có thể:\n" +
                    "1. 📸 *Gửi ảnh chụp thời khóa biểu* (có thể gửi 1 hoặc nhiều ảnh cùng lúc).\n" +
                    "2. 📄 *Gửi file giáo trình* (.pdf hoặc .docx) để AI chia đều các chương vào các buổi học thêm.\n" +
                    "3. ✍️ *Dán văn bản thời khóa biểu* (VD: Thứ 2 sáng 7h-9h Python, 9h-12h CSDL).\n" +
                    "4. 📅 Gõ `/schedule` để xem lịch tự học tối ưu kèm trạng thái hoàn thành.\n" +
                    "5. 📖 Gõ `/syllabus` để xem tiến độ tuần và đề cương.\n" +
                    "6. 📚 Gõ `/giaotrinh` để xem lại các giáo trình đã nạp.\n" +
                    "7. ▶️ Gõ `/study [Số thứ tự]` để vào học trước và xem đề cương buổi đó.\n" +
                    "8. ✅ Gõ `/done [Số thứ tự]` để đánh dấu buổi học đã xong / chưa xong.\n" +
                    "9. 🏆 Gõ `/contest [Tên môn]` để bắt đầu bài thi trắc nghiệm (Contest) có chấm điểm.\n" +
                    "10. 📝 Gõ `/quiz [Tên môn]` để làm câu hỏi ôn tập đầu ca / cuối ca.");
        }
    }

    @Override
    public void onPhotoMessage(long chatId, String userName, byte[] photoBytes, String caption) {
        photoBuffers.computeIfAbsent(chatId, k -> new java.util.ArrayList<>()).add(photoBytes);

        // Hủy timer cũ nếu người dùng gửi nhiều ảnh dồn dập trong album
        var oldTimer = bufferTimers.get(chatId);
        if (oldTimer != null) oldTimer.cancel(false);

        botClient.sendMessage(chatId, "📸 *Đã nhận ảnh TKB!* (Đang đợi nốt các ảnh khác nếu bạn gửi dạng album...)");

        // Sau 2.5 giây không nhận thêm ảnh mới thì tiến hành gom tất cả ảnh phân tích 1 lượt
        var task = scheduler.schedule(() -> {
            List<byte[]> allPhotos = photoBuffers.remove(chatId);
            bufferTimers.remove(chatId);
            if (allPhotos != null && !allPhotos.isEmpty()) {
                botClient.sendMessage(chatId, String.format("🤖 Đang phân tích tổng hợp *%d ảnh thời khóa biểu* bằng Gemini Vision...", allPhotos.size()));
                List<TimetableEntry> entries = geminiService.extractTimetableFromMultipleImages(allPhotos, "image/jpeg");
                processTimetableEntries(chatId, entries);
            }
        }, 2500, java.util.concurrent.TimeUnit.MILLISECONDS);

        bufferTimers.put(chatId, task);
    }

    @Override
    public void onDocumentMessage(long chatId, String userName, String fileName, byte[] docBytes) {
        String lowerName = fileName.toLowerCase();
        if (!lowerName.endsWith(".pdf") && !lowerName.endsWith(".docx") && !lowerName.endsWith(".txt")) {
            botClient.sendMessage(chatId, "⚠️ Hiện tại bot hỗ trợ file định dạng *.pdf*, *.docx* hoặc *.txt*.");
            return;
        }

        if (isTimetableDocument(fileName, docBytes)) {
            botClient.sendMessage(chatId, "📅 *Đã nhận file PDF Thời khóa biểu:* " + fileName + "\n🤖 Đang bóc tách và phân tích các môn học bằng AI...");
            List<TimetableEntry> entries = geminiService.extractTimetableFromPdf(docBytes);
            processTimetableEntries(chatId, entries);
            return;
        }

        botClient.sendMessage(chatId, "📄 *Đã nhận file giáo trình:* " + fileName + "\n🤖 Đang trích xuất nội dung văn bản giáo trình...");

        String textContent = "";
        if (lowerName.endsWith(".pdf")) {
            textContent = com.studyagent.parser.DocumentParser.extractTextFromPdf(docBytes);
        } else if (lowerName.endsWith(".docx")) {
            textContent = com.studyagent.parser.DocumentParser.extractTextFromDocx(docBytes);
        } else {
            textContent = new String(docBytes, java.nio.charset.StandardCharsets.UTF_8);
        }

        if (textContent.isBlank()) {
            botClient.sendMessage(chatId, "⚠️ Không thể trích xuất văn bản từ tài liệu này (có thể file scan dạng ảnh hoặc bị khóa).");
            return;
        }

        // Tự động nhận diện tên môn học từ tên file hoặc danh sách môn trong TKB
        String detectedSubject = fileName.replaceAll("(?i)\\.(pdf|docx|txt)$", "")
                .replaceAll("(?i)(giao[-_ ]?trinh|tai[-_ ]?lieu|slide|bai[-_ ]?giang)[-_ ]*", "").trim();
        List<String> distinctSubs = repository.getDistinctSubjects(chatId);
        for (String sub : distinctSubs) {
            if (fileName.toLowerCase().contains(sub.toLowerCase()) || sub.toLowerCase().contains(detectedSubject.toLowerCase())) {
                detectedSubject = sub;
                break;
            }
        }
        if (detectedSubject.isBlank()) detectedSubject = "Môn học";

        // Lưu giáo trình nguyên vẹn vào SQLite
        if (!repository.saveTextbook(chatId, detectedSubject, fileName, textContent)) {
            botClient.sendMessage(chatId, "⚠️ Không thể lưu giáo trình vào cơ sở dữ liệu. Vui lòng thử lại.");
            return;
        }

        // Đếm số buổi học thêm của môn này
        List<StudySession> allSessions = repository.getStudySessions(chatId);
        final String matchSub = detectedSubject;
        int sessionCount = (int) allSessions.stream()
                .filter(s -> s.getSubjectName() != null && s.getSubjectName().equalsIgnoreCase(matchSub))
                .count();
        if (sessionCount <= 0) {
            botClient.sendMessage(chatId, String.format("📚 Đã lưu nguyên văn giáo trình môn *%s*.\n⚠️ Môn này chưa có buổi tự học trong lịch nên chưa thể chia tiến độ. Hãy phân tích lại thời khóa biểu hoặc cập nhật lịch trước.", detectedSubject));
            return;
        }

        botClient.sendMessage(chatId, String.format("📚 Đã lưu giáo trình môn *%s*!\n🤖 Đang soạn bài giảng chi tiết cho *%d buổi học thêm*...", detectedSubject, sessionCount));

        List<com.studyagent.model.WeeklyCurriculum> weeklyPlans;
        try {
            weeklyPlans = geminiService.generateCurriculumForSessions(detectedSubject, textContent, sessionCount);
        } catch (IllegalStateException e) {
            botClient.sendMessage(chatId, "⚠️ Giáo trình đã được lưu, nhưng chưa tạo được bài giảng AI.\n" +
                    e.getMessage() + "\nHãy kiểm tra API key/kết nối rồi gửi lại giáo trình để thử lại.");
            return;
        }
        if (!repository.saveWeeklyCurricula(chatId, detectedSubject, weeklyPlans)) {
            botClient.sendMessage(chatId, "⚠️ AI đã tạo bài giảng nhưng không thể cập nhật đề cương vào cơ sở dữ liệu. Vui lòng nạp lại giáo trình để thử lại.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("✅ *ĐÃ TẠO BÀI GIẢNG CHI TIẾT CHO %d BUỔI MÔN: %s*\n\n", sessionCount, detectedSubject.toUpperCase()));
        for (com.studyagent.model.WeeklyCurriculum wp : weeklyPlans) {
            sb.append(String.format("• Buổi %d: %s\n", wp.getWeekNumber(), wp.getChapterTitle()));
        }
        sb.append("\nGõ `/study <số buổi>` để nhận toàn bộ bài giảng của buổi đó.");
        botClient.sendMessage(chatId, sb.toString());
    }

    private void processTimetableEntries(long chatId, List<TimetableEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            if (!geminiService.hasApiKey()) {
                botClient.sendMessage(chatId, "⚠️ Chưa cấu hình Gemini API key. Hãy thêm key vào config.properties (gemini.api.key) hoặc biến môi trường GEMINI_API_KEY rồi thử lại.");
            } else {
                botClient.sendMessage(chatId, "⚠️ Không tìm thấy môn học nào trong thời khóa biểu. Vui lòng gửi lại ảnh rõ nét hơn hoặc nhập văn bản rõ ràng nhé!");
            }
            return;
        }

        repository.saveTimetableEntries(chatId, entries);

        List<StudySession> studySessions = optimizer.generateSemesterSchedule(entries);
        repository.saveStudySessions(chatId, studySessions);

        Set<String> uniqueSubjects = new HashSet<>();
        for (TimetableEntry entry : entries) {
            if (entry.getSubjectName() != null && !entry.getSubjectName().isBlank()) {
                uniqueSubjects.add(entry.getSubjectName().trim());
            }
        }

        for (String subject : uniqueSubjects) {
            SubjectSyllabus syllabus = geminiService.generateSyllabus(subject);
            syllabus.setChatId(chatId);
            repository.saveSyllabus(syllabus);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🎉 *PHÂN TÍCH THỜI KHÓA BIỂU THÀNH CÔNG!* 🎉\n\n");
        sb.append("🏫 *Lịch học trên lớp đã nhận diện (").append(entries.size()).append(" buổi):*\n");
        for (TimetableEntry e : entries) {
            sb.append(String.format("• *%s* (%s - %s): *%s* [%s]\n", e.getDayOfWeek(), e.getStartTime(), e.getEndTime(), e.getSubjectName(), e.getRoom()));
        }

        sb.append("\n━━━━━━━━━━━━━━━━━━━━\n");
        sb.append(String.format("🎯 *KẾ HOẠCH TỰ HỌC CẢ KỲ:* %d phiên đã được lên lịch.\n", studySessions.size()));
        int previewCount = Math.min(10, studySessions.size());
        for (int i = 0; i < previewCount; i++) {
            StudySession s = studySessions.get(i);
            String dateLabel = s.getScheduledDate() == null ? s.getDayOfWeek() : s.getScheduledDate();
            sb.append(String.format("📅 *%s* [%s - %s]: Tự học *%s*\n",
                    dateLabel, s.getStartTime(), s.getEndTime(), s.getSubjectName()));
        }
        if (studySessions.size() > previewCount) {
            sb.append(String.format("... và %d phiên khác trong kỳ. Gõ `/schedule` để xem lịch đã lưu.\n",
                    studySessions.size() - previewCount));
        }

        sb.append("\n━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("📚 *ĐỀ CƯƠNG HỌC TẬP ĐÃ SOẠN SẴN:*\n");
        sb.append(String.format("Đã biên soạn đề cương cho *%d môn học*: %s.\n", uniqueSubjects.size(), String.join(", ", uniqueSubjects)));
        sb.append("👉 Gõ `/syllabus` để xem chi tiết từng chương!\n\n");
        sb.append("🔔 *Hệ thống thông báo:* Bot sẽ tự động gửi tin nhắn nhắc nhở và gửi kèm đề cương trước mỗi buổi học 15 phút!");

        botClient.sendMessage(chatId, sb.toString());
    }

    private void showCurrentSchedule(long chatId) {
        showCurrentSchedule(chatId, null);
    }

    private void showCurrentSchedule(long chatId, String dateOrWeek) {
        List<TimetableEntry> timetables = repository.getTimetableEntries(chatId);
        if (timetables.isEmpty()) {
            botClient.sendMessage(chatId, "⚠️ Bạn chưa tải lên thời khóa biểu nào. Hãy chụp ảnh hoặc gửi văn bản TKB để bot lên lịch nhé!");
            return;
        }

        java.time.LocalDate targetDate = null;
        if (dateOrWeek != null && !dateOrWeek.isBlank()) {
            targetDate = com.studyagent.scheduler.StudyOptimizer.parseDate(dateOrWeek);
        }

        List<TimetableEntry> activeTimetables = new ArrayList<>();
        for (TimetableEntry t : timetables) {
            if (targetDate == null || com.studyagent.scheduler.StudyOptimizer.isEntryActiveInWeek(t, targetDate)) {
                activeTimetables.add(t);
            }
        }

        List<StudySession> sessions = (targetDate != null)
                ? optimizer.generateOptimalScheduleForWeek(timetables, targetDate)
                : repository.getStudySessions(chatId);

        StringBuilder sb = new StringBuilder();
        if (targetDate != null) {
            sb.append(String.format("📋 *LỊCH HỌC & TỰ HỌC TUẦN (%02d/%02d/%d)*\n\n",
                    targetDate.getDayOfMonth(), targetDate.getMonthValue(), targetDate.getYear()));
        } else {
            sb.append("📋 *LỊCH TRÌNH HỌC TẬP HIỆN TẠI CỦA BẠN*\n\n");
        }

        sb.append(String.format("🏫 *Lịch học trên lớp (%d buổi):*\n", activeTimetables.size()));
        for (TimetableEntry t : activeTimetables) {
            sb.append(String.format("• %s: *%s* (%s - %s, %s)\n", t.getDayOfWeek(), t.getSubjectName(), t.getStartTime(), t.getEndTime(), t.getRoom()));
        }
        sb.append("\n");

        if (!sessions.isEmpty()) {
            sb.append("🍅 *Lịch tự học tối ưu (phân bổ theo số tiết, tối đa 2 tiếng/môn/tuần):*\n");
            int idx = 1;
            for (StudySession s : sessions) {
                String mark = s.isCompleted() ? "✅ [ĐÃ HOÀN THÀNH]" : "⏳ [CHƯA XONG]";
                sb.append(String.format("• *%d.* %s %s (%s - %s): *%s*\n  _Trọng tâm:_ %s\n",
                        idx++, mark, s.getDayOfWeek(), s.getStartTime(), s.getEndTime(), s.getSubjectName(), s.getTopicOverview()));
            }
            sb.append("\n👉 Gõ `/study [Số]` để vào học trước & xem đề cương buổi đó.\n");
            sb.append("👉 Gõ `/done [Số]` để đổi trạng thái hoàn thành.");
        }

        botClient.sendMessage(chatId, sb.toString());
    }

    private void showAllTextbooks(long chatId, String subject) {
        if (subject != null && !subject.isBlank()) {
            StudyRepository.TextbookItem item = repository.getTextbook(chatId, subject);
            if (item == null) {
                botClient.sendMessage(chatId, "⚠️ Chưa có giáo trình môn *" + subject + "* được lưu trữ.");
                return;
            }
            StringBuilder msg = new StringBuilder(String.format("📚 GIÁO TRÌNH MÔN: %s\nFile: %s | Ngày nạp: %s\n\n",
                    item.getSubjectName().toUpperCase(), item.getFileName(), item.getCreatedAt()));
            List<com.studyagent.model.WeeklyCurriculum> plans = repository.getWeeklyCurricula(chatId, subject);
            if (!plans.isEmpty()) {
                msg.append("ĐỀ CƯƠNG THEO BUỔI:\n");
                for (com.studyagent.model.WeeklyCurriculum plan : plans) {
                    msg.append(String.format("Buổi %d: %s\n%s\n\n",
                            plan.getWeekNumber(), plan.getChapterTitle(), plan.getDetailedSyllabus()));
                }
                msg.append("TOÀN VĂN GIÁO TRÌNH:\n\n");
            }
            msg.append(item.getContent());
            sendLongMessage(chatId, msg.toString());
            return;
        }

        List<StudyRepository.TextbookItem> list = repository.getAllTextbooks(chatId);
        if (list.isEmpty()) {
            botClient.sendMessage(chatId, "📚 Chưa có giáo trình nào được lưu. Bạn hãy gửi file PDF/Word giáo trình cho bot nhé!");
            return;
        }

        StringBuilder sb = new StringBuilder("📚 *DANH SÁCH GIÁO TRÌNH ĐÃ LƯU TRỮ:*\n\n");
        for (StudyRepository.TextbookItem item : list) {
            sb.append(String.format("• 📖 *%s* (File: `%s`, nạp: %s)\n", item.getSubjectName(), item.getFileName(), item.getCreatedAt()));
        }
        sb.append("\n👉 Gõ `/giaotrinh <Tên môn>` để xem đề cương theo buổi và toàn văn giáo trình.");
        botClient.sendMessage(chatId, sb.toString());
    }

    private void toggleSessionDone(long chatId, String arg) {
        List<StudySession> sessions = repository.getStudySessions(chatId);
        if (sessions.isEmpty()) {
            botClient.sendMessage(chatId, "⚠️ Bạn chưa có lịch tự học nào.");
            return;
        }
        try {
            int index = Integer.parseInt(arg.trim());
            if (index < 1 || index > sessions.size()) {
                botClient.sendMessage(chatId, "⚠️ Vui lòng nhập số thứ tự từ 1 đến " + sessions.size());
                return;
            }
            StudySession session = sessions.get(index - 1);
            boolean newStatus = !session.isCompleted();
            session.setCompleted(newStatus);
            repository.toggleStudySessionCompleted(session.getId(), newStatus);

            botClient.sendMessage(chatId, String.format("%s Đã cập nhật buổi học số *%d* (*%s*): *%s*",
                    newStatus ? "✅" : "⏳", index, session.getSubjectName(), newStatus ? "ĐÃ HOÀN THÀNH" : "CHƯA HOÀN THÀNH"));
        } catch (NumberFormatException e) {
            botClient.sendMessage(chatId, "⚠️ Hãy nhập đúng định dạng, ví dụ: `/done 1`");
        }
    }

    private void studySessionEarly(long chatId, String arg) {
        List<StudySession> sessions = repository.getStudySessions(chatId);
        if (sessions.isEmpty()) {
            botClient.sendMessage(chatId, "⚠️ Bạn chưa có lịch tự học nào.");
            return;
        }
        try {
            int index = Integer.parseInt(arg.trim());
            if (index < 1 || index > sessions.size()) {
                botClient.sendMessage(chatId, "⚠️ Vui lòng nhập số thứ tự từ 1 đến " + sessions.size());
                return;
            }
            StudySession session = sessions.get(index - 1);
            String subject = session.getSubjectName();

            int subSessionCount = session.getSessionIndex();

            List<com.studyagent.model.WeeklyCurriculum> curricula = repository.getWeeklyCurricula(chatId, subject);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("BÀI GIẢNG TỰ HỌC: %s (Buổi %d)\n", subject.toUpperCase(), subSessionCount));
            sb.append(String.format("Lịch học: %s (%s - %s)\n\n", session.getDayOfWeek(), session.getStartTime(), session.getEndTime()));

            if (curricula == null || curricula.isEmpty()) {
                sb.append("Môn này chưa có giáo trình được phân tích thành bài giảng.\n\n");
                sb.append(String.format("Trọng tâm dự kiến: %s\n", session.getTopicOverview()));
                sb.append(String.format("Tài liệu cần chuẩn bị: %s\n\n", session.getPreparationMaterials()));
                sb.append("Hãy gửi file PDF/Word giáo trình để tạo bài giảng theo từng buổi.");
            } else {
                int targetWeek = Math.min(subSessionCount, curricula.size());
                com.studyagent.model.WeeklyCurriculum curr = null;
                for (com.studyagent.model.WeeklyCurriculum c : curricula) {
                    if (c.getWeekNumber() == targetWeek) { curr = c; break; }
                }
                if (curr == null) curr = curricula.get(curricula.size() - 1);

                    String lessonIssue = curr.getDetailedLessonIssue();
                    if (lessonIssue != null) {
                        sb.append("CẢNH BÁO: Bài đã lưu từ lần trước chỉ là đề cương tóm tắt, chưa phải bài giảng đầy đủ.\n");
                        sb.append(lessonIssue).append("\n");
                        sb.append("Giáo trình gốc vẫn được lưu. Kiểm tra API key Gemini rồi nạp lại giáo trình để tạo lại.\n");
                    } else {
                        sb.append("NỘI DUNG TỪ GIÁO TRÌNH:\n");
                        sb.append(String.format("Chương / Phần: %s\n\n", curr.getChapterTitle()));
                        sb.append(curr.getDetailedSyllabus()).append("\n\n");
                        sb.append(String.format("Trọng tâm theo lịch: %s\n", session.getTopicOverview()));
                    }
            }

            sb.append("\n👉 Sau khi học xong, gõ `/done ").append(index).append("` để đánh dấu hoàn thành nhé!");
            sendLongMessage(chatId, sb.toString());
        } catch (NumberFormatException e) {
            botClient.sendMessage(chatId, "⚠️ Hãy nhập đúng định dạng, ví dụ: `/study 1`");
        }
    }

    private void showAllSyllabuses(long chatId) {
        List<SubjectSyllabus> list = repository.getAllSyllabuses(chatId);
        if (list.isEmpty()) {
            botClient.sendMessage(chatId, "📚 Chưa có đề cương nào được lưu. Bạn hãy gửi thời khóa biểu hoặc gõ `/decuong <Tên môn>` nhé!");
            return;
        }

        for (SubjectSyllabus s : list) {
            String msg = String.format("📖 *ĐỀ CƯƠNG MÔN: %s*\n\n" +
                    "🎯 *Tổng quan:* %s\n\n" +
                    "📑 *Lộ trình chương mục:*\n%s\n\n" +
                    "💡 *Mẹo ôn thi:* %s\n\n" +
                    "📚 *Tài liệu đề xuất:* %s",
                    s.getSubjectName().toUpperCase(), s.getOverview(), s.getSyllabusContent(), s.getExamTips(), s.getRecommendedMaterials());
            botClient.sendMessage(chatId, msg);
        }
    }

    private void handleSyllabusRequest(long chatId, String subject) {
        botClient.sendMessage(chatId, "🤖 Đang dùng AI biên soạn đề cương môn: *" + subject + "*...");
        SubjectSyllabus s = geminiService.generateSyllabus(subject);
        s.setChatId(chatId);
        repository.saveSyllabus(s);

        String msg = String.format("📖 *ĐỀ CƯƠNG CHI TIẾT: %s*\n\n" +
                "🎯 *Mục tiêu:* %s\n\n" +
                "📑 *Nội dung đề cương theo giáo trình:*\n%s\n\n" +
                "💡 *Chiến lược thi:* %s\n\n" +
                "📚 *Tài liệu tham khảo:* %s",
                subject.toUpperCase(), s.getOverview(), s.getSyllabusContent(), s.getExamTips(), s.getRecommendedMaterials());
        botClient.sendMessage(chatId, msg);
    }

    private void sendWelcomeMessage(long chatId, String userName) {
        String msg = String.format("Xin chào *%s*! 👋 Tôi là *Smart Study Agent* 🎓\n\n" +
                "Tôi có thể giúp bạn:\n" +
                "1️⃣ *Đọc ảnh thời khóa biểu (Vision AI)*: Chỉ cần chụp hoặc gửi ảnh TKB của trường.\n" +
                "2️⃣ *Tối ưu lịch tự học*: Tự động phân tích các khoảng trống để xếp lịch ôn tập Spaced Repetition & Pomodoro.\n" +
                "3️⃣ *Soạn đề cương môn học*: AI tự động tóm tắt chương trình, tài liệu và mẹo ôn thi cho từng môn.\n" +
                "4️⃣ *Hẹn giờ nhắc nhở*: Tự động thông báo trên Telegram trước mỗi buổi tự học kèm đề cương để bạn bắt đầu!\n\n" +
                "👉 *Hãy gửi cho tôi ảnh thời khóa biểu hoặc văn bản lịch học của bạn ngay bây giờ!*", userName);
        botClient.sendMessage(chatId, msg);
    }

    private void startContest(long chatId, String subject) {
        if (subject == null || subject.isBlank()) {
            List<TimetableEntry> entries = repository.getTimetableEntries(chatId);
            if (!entries.isEmpty()) {
                subject = entries.get(0).getSubjectName();
            } else {
                subject = "Cơ sở dữ liệu";
            }
        }

        botClient.sendMessage(chatId, String.format("🏁 *BẮT ĐẦU MINI CONTEST MÔN: %s* 🏁\n🤖 AI đang chuẩn bị đề thi trắc nghiệm (5 câu hỏi)...", subject));
        List<com.studyagent.model.QuizQuestion> questions = geminiService.generateQuizQuestions(subject, 1, "CONTEST", "Kiểm tra kiến thức tổng hợp");
        repository.saveQuizQuestions(chatId, subject, "CONTEST", 1, questions);

        ActiveContest contest = new ActiveContest();
        contest.subject = subject;
        contest.questions = questions;
        contest.currentIndex = 0;
        contest.correctCount = 0;
        activeContests.put(chatId, contest);

        sendNextContestQuestion(chatId);
    }

    private void showSavedContests(long chatId) {
        java.util.Map<String, java.util.Map<Integer, List<com.studyagent.model.QuizQuestion>>> contests =
                repository.getAllContestsGroupedBySubject(chatId);
        if (contests.isEmpty()) {
            botClient.sendMessage(chatId, "🏆 Chưa có Contest nào được lưu. Gõ `/contest <Tên môn>` để tạo bộ đề mới.");
            return;
        }
        StringBuilder output = new StringBuilder("🏆 CONTEST ĐÃ TẠO - ÔN TẬP THEO MÔN\n\n");
        for (java.util.Map.Entry<String, java.util.Map<Integer, List<com.studyagent.model.QuizQuestion>>> subject : contests.entrySet()) {
            output.append("MÔN: ").append(subject.getKey()).append('\n');
            for (java.util.Map.Entry<Integer, List<com.studyagent.model.QuizQuestion>> contest : subject.getValue().entrySet()) {
                output.append(String.format("\nContest #%d (%d câu)\n", contest.getKey(), contest.getValue().size()));
                int questionIndex = 1;
                for (com.studyagent.model.QuizQuestion question : contest.getValue()) {
                    output.append(String.format("%d. %s\n", questionIndex++, question.getQuestion()));
                    if (question.getOptions() != null) {
                        for (String option : question.getOptions()) output.append("  ").append(option).append('\n');
                    }
                    output.append("Đáp án: ").append(question.getCorrectAnswer())
                            .append("\nGiải thích: ").append(question.getExplanation()).append("\n\n");
                }
            }
            output.append('\n');
        }
        sendLongMessage(chatId, output.toString());
    }

    private void sendLongMessage(long chatId, String text) {
        final int maxLength = 3500;
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxLength, text.length());
            if (end < text.length()) {
                int newline = text.lastIndexOf('\n', end);
                if (newline > start) end = newline;
            }
            botClient.sendMessage(chatId, text.substring(start, end), "");
            start = end;
            while (start < text.length() && text.charAt(start) == '\n') start++;
        }
    }

    private void sendNextContestQuestion(long chatId) {
        ActiveContest contest = activeContests.get(chatId);
        if (contest == null) return;

        if (contest.currentIndex >= contest.questions.size()) {
            // Hoàn thành contest
            int score = (int) Math.round(((double) contest.correctCount / contest.questions.size()) * 100);
            StringBuilder sb = new StringBuilder();
            sb.append("🏆 *KẾT QUẢ MINI CONTEST MÔN ").append(contest.subject.toUpperCase()).append("*\n\n");
            sb.append(String.format("🎯 Điểm số: *%d/100* (Đúng %d/%d câu)\n\n", score, contest.correctCount, contest.questions.size()));
            if (score >= 80) {
                sb.append("🌟 *Xuất sắc!* Bạn đã nắm rất chắc nội dung kiến thức môn này!\n");
            } else if (score >= 50) {
                sb.append("👍 *Khá tốt!* Hãy xem lại các câu giải thích để củng cố điểm yếu nhé.\n");
            } else {
                sb.append("💪 *Cần cố gắng thêm!* Hãy đọc lại giáo trình các chương tương ứng.\n");
            }
            sb.append("\n👉 Gõ `/contest` để làm lại hoặc thử thách môn khác!");
            botClient.sendMessage(chatId, sb.toString());
            activeContests.remove(chatId);
            return;
        }

        com.studyagent.model.QuizQuestion q = contest.questions.get(contest.currentIndex);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("❓ *Câu %d/%d:* %s\n\n", contest.currentIndex + 1, contest.questions.size(), q.getQuestion()));
        for (String opt : q.getOptions()) {
            sb.append(opt).append("\n");
        }
        sb.append("\n👉 *Trả lời:* Hãy gõ ký tự đáp án (*A*, *B*, hoặc *C*, *D*) vào khung chat!");
        botClient.sendMessage(chatId, sb.toString());
    }

    private void handleContestAnswer(long chatId, String answer) {
        ActiveContest contest = activeContests.get(chatId);
        if (contest == null || contest.currentIndex >= contest.questions.size()) return;

        com.studyagent.model.QuizQuestion q = contest.questions.get(contest.currentIndex);
        boolean isCorrect = answer.equalsIgnoreCase(q.getCorrectAnswer());
        if (isCorrect) {
            contest.correctCount++;
            botClient.sendMessage(chatId, String.format("✅ *Chính xác!* Đáp án là *%s*.\n💡 _Giải thích:_ %s", q.getCorrectAnswer(), q.getExplanation()));
        } else {
            botClient.sendMessage(chatId, String.format("❌ *Chưa đúng!* Bạn chọn %s, đáp án đúng là *%s*.\n💡 _Giải thích:_ %s", answer, q.getCorrectAnswer(), q.getExplanation()));
        }

        contest.currentIndex++;
        sendNextContestQuestion(chatId);
    }

    private void handleQuizRequest(long chatId, String subject) {
        if (subject == null || subject.isBlank()) {
            List<TimetableEntry> entries = repository.getTimetableEntries(chatId);
            subject = !entries.isEmpty() ? entries.get(0).getSubjectName() : "Lập trình Java";
        }

        botClient.sendMessage(chatId, "🤖 Đang chuẩn bị câu hỏi ôn tập đầu ca & cuối ca môn *" + subject + "*...");
        List<com.studyagent.model.QuizQuestion> startQuiz = geminiService.generateQuizQuestions(subject, 1, "START_SESSION", "Ôn tập bài tuần trước");
        List<com.studyagent.model.QuizQuestion> endQuiz = geminiService.generateQuizQuestions(subject, 1, "END_SESSION", "Kiểm tra bài học hôm nay");

        repository.saveQuizQuestions(chatId, subject, "START_SESSION", 1, startQuiz);
        repository.saveQuizQuestions(chatId, subject, "END_SESSION", 1, endQuiz);

        StringBuilder sb = new StringBuilder();
        sb.append("📝 *BỘ CÂU HỎI ÔN TẬP CA HỌC MÔN: ").append(subject.toUpperCase()).append("*\n\n");
        sb.append("🟡 *1. ĐẦU CA HỌC (Ôn bài tuần trước):*\n");
        for (int i = 0; i < startQuiz.size(); i++) {
            var q = startQuiz.get(i);
            sb.append(String.format("Câu %d: %s\n", i + 1, q.getQuestion()));
            for (String opt : q.getOptions()) sb.append("  ").append(opt).append("\n");
            sb.append("  👉 _Đáp án:_ *").append(q.getCorrectAnswer()).append("* - ").append(q.getExplanation()).append("\n\n");
        }

        sb.append("🟢 *2. CUỐI CA HỌC (Củng cố bài hôm nay):*\n");
        for (int i = 0; i < endQuiz.size(); i++) {
            var q = endQuiz.get(i);
            sb.append(String.format("Câu %d: %s\n", i + 1, q.getQuestion()));
            for (String opt : q.getOptions()) sb.append("  ").append(opt).append("\n");
            sb.append("  👉 _Đáp án:_ *").append(q.getCorrectAnswer()).append("* - ").append(q.getExplanation()).append("\n\n");
        }

        sb.append("💡 _Bạn cũng có thể gõ `/contest` để vào thi trắc nghiệm tính điểm!_");
        botClient.sendMessage(chatId, sb.toString());
    }

    private boolean looksLikeTimetable(String text) {
        String lower = text.toLowerCase();
        return lower.contains("thứ") || lower.contains("tiết") || lower.contains("sáng") ||
                lower.contains("chiều") || lower.contains("tối") || lower.contains("h-") || lower.contains(":") ||
                lower.contains("mon") || lower.contains("tue") || lower.contains("wed");
    }

    public boolean isTimetableDocument(String fileName, byte[] docBytes) {
        String lower = fileName != null ? fileName.toLowerCase() : "";
        if (lower.contains("tkb") || lower.contains("thoikhoabieu") || lower.contains("thoi-khoa-bieu")
                || lower.contains("thoi_khoa_bieu") || lower.contains("schedule")
                || lower.contains("lichhoc") || lower.contains("lich-hoc")
                || lower.contains("lich_hoc") || lower.contains("timetable")) {
            return true;
        }

        if (lower.endsWith(".pdf") && docBytes != null && docBytes.length > 0) {
            String text = com.studyagent.parser.DocumentParser.extractTextFromPdf(docBytes).toLowerCase();
            if (text.contains("thời khóa biểu") || text.contains("thời khoá biểu")
                    || text.contains("tiết bắt đầu") || text.contains("cổng thông tin quản lý đào tạo")
                    || (text.contains("mã mh") && text.contains("nhóm tổ"))
                    || (text.contains("thứ") && text.contains("tiết") && text.contains("phòng"))) {
                return true;
            }
        }
        return false;
    }
}
