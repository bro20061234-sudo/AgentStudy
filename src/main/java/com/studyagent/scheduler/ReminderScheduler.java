package com.studyagent.scheduler;

import com.studyagent.bot.TelegramBotClient;
import com.studyagent.model.StudySession;
import com.studyagent.repository.StudyRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ReminderScheduler {
    private final StudyRepository repository;
    private final TelegramBotClient botClient;
    private final ScheduledExecutorService worker;
    private boolean isRunning = false;

    public ReminderScheduler(StudyRepository repository, TelegramBotClient botClient) {
        this.repository = repository;
        this.botClient = botClient;
        this.worker = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;
        worker.scheduleAtFixedRate(this::checkAndSendReminders, 5, 30, TimeUnit.SECONDS);
        System.out.println("⏰ [ReminderScheduler] Đã kích hoạt hệ thống nhắc việc tự động (mỗi 30s).");
    }

    public void stop() {
        isRunning = false;
        worker.shutdown();
    }

    private void checkAndSendReminders() {
        try {
            LocalDate today = LocalDate.now();
            LocalTime now = LocalTime.now();
            String currentDayVi = mapDayOfWeekToVi(today.getDayOfWeek());

            List<StudySession> unreminded = repository.getAllUnremindedSessions();
            for (StudySession session : unreminded) {
                if (matchesDay(session, currentDayVi, today)) {
                    LocalTime sessionStart = parseTime(session.getStartTime());
                    if (sessionStart != null) {
                        long diffMinutes = java.time.Duration.between(now, sessionStart).toMinutes();
                        if (diffMinutes >= -5 && diffMinutes <= 15) {
                            sendStudyReminder(session, diffMinutes);
                            repository.markSessionReminded(session.getId());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ [ReminderScheduler] Lỗi kiểm tra lịch nhắc: " + e.getMessage());
        }
    }

    private boolean matchesDay(StudySession session, String currentDayVi, LocalDate today) {
        if (session.getScheduledDate() != null && !session.getScheduledDate().isEmpty()) {
            return session.getScheduledDate().equals(today.toString());
        }
        String day = session.getDayOfWeek();
        if (day == null) return false;
        day = day.toLowerCase();
        return day.contains(currentDayVi.toLowerCase()) || day.contains(today.getDayOfWeek().name().toLowerCase());
    }

    private void sendStudyReminder(StudySession session, long minutesUntil) {
        StringBuilder sb = new StringBuilder();
        sb.append("🔔 *[NHẮC NHỞ HỌC TẬP TỰ ĐỘNG]* 🔔\n\n");
        if (minutesUntil > 0) {
            sb.append(String.format("⏳ Sắp đến giờ học môn *%s* (còn khoảng *%d phút* nữa)!\n", session.getSubjectName(), minutesUntil));
        } else {
            sb.append(String.format("🚀 Đã đến giờ vào bàn tự học môn *%s*!\n", session.getSubjectName()));
        }

        sb.append(String.format("⏰ *Thời gian:* %s - %s (%s)\n\n", session.getStartTime(), session.getEndTime(), session.getDayOfWeek()));
        sb.append("🎯 *Mục tiêu & Đề cương buổi học hôm nay:*\n");
        sb.append(session.getTopicOverview()).append("\n\n");

        if (session.getPreparationMaterials() != null && !session.getPreparationMaterials().isEmpty()) {
            sb.append("📖 *Tài liệu & Bài tập cần chuẩn bị:*\n");
            sb.append(session.getPreparationMaterials()).append("\n\n");
        }

        sb.append("🍅 *Mẹo Pomodoro:* Học tập trung 25 phút, nghỉ 5 phút. Hãy tắt thông báo và bắt đầu ngay nhé!");

        botClient.sendMessage(session.getChatId(), sb.toString());
        System.out.printf("🔔 [ReminderScheduler] Đã gửi thông báo nhắc học môn \"%s\" tới chat_id %d.%n", session.getSubjectName(), session.getChatId());
    }

    private LocalTime parseTime(String hhmm) {
        try {
            return LocalTime.parse(hhmm.trim(), DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            try {
                return LocalTime.parse(hhmm.trim(), DateTimeFormatter.ofPattern("H:mm"));
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private String mapDayOfWeekToVi(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY -> "Thứ 2";
            case TUESDAY -> "Thứ 3";
            case WEDNESDAY -> "Thứ 4";
            case THURSDAY -> "Thứ 5";
            case FRIDAY -> "Thứ 6";
            case SATURDAY -> "Thứ 7";
            case SUNDAY -> "Chủ Nhật";
        };
    }
}
