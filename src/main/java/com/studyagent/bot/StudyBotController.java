package com.studyagent.bot;

import com.studyagent.ai.GeminiService;
import com.studyagent.model.StudySession;
import com.studyagent.model.SubjectSyllabus;
import com.studyagent.model.TimetableEntry;
import com.studyagent.repository.StudyRepository;
import com.studyagent.scheduler.StudyOptimizer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StudyBotController implements TelegramBotClient.TelegramUpdateListener {
    private final TelegramBotClient botClient;
    private final GeminiService geminiService;
    private final StudyRepository repository;
    private final StudyOptimizer optimizer;

    public StudyBotController(TelegramBotClient botClient, GeminiService geminiService, StudyRepository repository, StudyOptimizer optimizer) {
        this.botClient = botClient;
        this.geminiService = geminiService;
        this.repository = repository;
        this.optimizer = optimizer;
    }

    @Override
    public void onTextMessage(long chatId, String userName, String text) {
        String trimmed = text.trim();

        if (trimmed.equalsIgnoreCase("/start") || trimmed.equalsIgnoreCase("/help")) {
            sendWelcomeMessage(chatId, userName);
        } else if (trimmed.equalsIgnoreCase("/schedule") || trimmed.equalsIgnoreCase("/xemlich")) {
            showCurrentSchedule(chatId);
        } else if (trimmed.equalsIgnoreCase("/syllabus") || trimmed.equalsIgnoreCase("/decuong")) {
            showAllSyllabuses(chatId);
        } else if (trimmed.startsWith("/decuong ") || trimmed.startsWith("/syllabus ")) {
            String subject = trimmed.substring(trimmed.indexOf(" ") + 1).trim();
            handleSyllabusRequest(chatId, subject);
        } else if (looksLikeTimetable(trimmed)) {
            botClient.sendMessage(chatId, "🤖 Đang phân tích văn bản thời khóa biểu bằng AI Gemini, bạn vui lòng đợi trong giây lát...");
            List<TimetableEntry> entries = geminiService.extractTimetableFromText(trimmed);
            processTimetableEntries(chatId, entries);
        } else {
            botClient.sendMessage(chatId, "💡 Bạn có thể:\n" +
                    "1. 📸 *Gửi ảnh chụp thời khóa biểu* trực tiếp vào đây.\n" +
                    "2. ✍️ *Dán văn bản thời khóa biểu* (VD: Thứ 2 sáng 7h-9h Python, 9h-12h Mạng MT).\n" +
                    "3. 📅 Gõ `/schedule` để xem lịch tự học tối ưu.\n" +
                    "4. 📖 Gõ `/syllabus` để xem đề cương các môn.");
        }
    }

    @Override
    public void onPhotoMessage(long chatId, String userName, byte[] photoBytes, String caption) {
        botClient.sendMessage(chatId, "📸 *Đã nhận ảnh thời khóa biểu!*\n🤖 Đang dùng *Gemini Multimodal Vision* đọc bảng biểu và trích xuất lịch học...");
        List<TimetableEntry> entries = geminiService.extractTimetableFromImage(photoBytes, "image/jpeg");
        processTimetableEntries(chatId, entries);
    }

    private void processTimetableEntries(long chatId, List<TimetableEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            botClient.sendMessage(chatId, "⚠️ Không tìm thấy môn học nào trong thời khóa biểu. Vui lòng gửi lại ảnh rõ nét hơn hoặc nhập văn bản rõ ràng nhé!");
            return;
        }

        repository.saveTimetableEntries(chatId, entries);

        List<StudySession> studySessions = optimizer.generateOptimalSchedule(entries);
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
        sb.append("🎯 *KẾ HOẠCH TỰ HỌC TỐI ƯU (AI ĐÃ LÊN LỊCH):*\n");
        for (StudySession s : studySessions) {
            sb.append(String.format("📅 *%s* [%s - %s]: Tự học *%s*\n", s.getDayOfWeek(), s.getStartTime(), s.getEndTime(), s.getSubjectName()));
            sb.append(String.format("   📌 _Nội dung:_ %s\n", s.getTopicOverview()));
        }

        sb.append("\n━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("📚 *ĐỀ CƯƠNG HỌC TẬP ĐÃ SOẠN SẴN:*\n");
        sb.append(String.format("Đã biên soạn đề cương cho *%d môn học*: %s.\n", uniqueSubjects.size(), String.join(", ", uniqueSubjects)));
        sb.append("👉 Gõ `/syllabus` để xem chi tiết từng chương!\n\n");
        sb.append("🔔 *Hệ thống thông báo:* Bot sẽ tự động gửi tin nhắn nhắc nhở và gửi kèm đề cương trước mỗi buổi học 15 phút!");

        botClient.sendMessage(chatId, sb.toString());
    }

    private void showCurrentSchedule(long chatId) {
        List<TimetableEntry> timetables = repository.getTimetableEntries(chatId);
        List<StudySession> sessions = repository.getStudySessions(chatId);

        if (timetables.isEmpty() && sessions.isEmpty()) {
            botClient.sendMessage(chatId, "⚠️ Bạn chưa tải lên thời khóa biểu nào. Hãy chụp ảnh hoặc gửi văn bản TKB để bot lên lịch nhé!");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📋 *LỊCH TRÌNH HỌC TẬP HIỆN TẠI CỦA BẠN*\n\n");
        if (!timetables.isEmpty()) {
            sb.append("🏫 *Lịch học trên lớp:*\n");
            for (TimetableEntry t : timetables) {
                sb.append(String.format("• %s: *%s* (%s - %s, %s)\n", t.getDayOfWeek(), t.getSubjectName(), t.getStartTime(), t.getEndTime(), t.getRoom()));
            }
            sb.append("\n");
        }

        if (!sessions.isEmpty()) {
            sb.append("🍅 *Lịch tự học tối ưu (kèm thông báo tự động):*\n");
            for (StudySession s : sessions) {
                sb.append(String.format("• %s (%s - %s): *%s*\n  _Trọng tâm:_ %s\n",
                        s.getDayOfWeek(), s.getStartTime(), s.getEndTime(), s.getSubjectName(), s.getTopicOverview()));
            }
        }

        botClient.sendMessage(chatId, sb.toString());
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

    private boolean looksLikeTimetable(String text) {
        String lower = text.toLowerCase();
        return lower.contains("thứ") || lower.contains("tiết") || lower.contains("sáng") ||
                lower.contains("chiều") || lower.contains("tối") || lower.contains("h-") || lower.contains(":") ||
                lower.contains("mon") || lower.contains("tue") || lower.contains("wed");
    }
}
