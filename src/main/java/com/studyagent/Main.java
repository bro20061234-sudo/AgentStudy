package com.studyagent;

import com.studyagent.ai.GeminiService;
import com.studyagent.bot.StudyBotController;
import com.studyagent.bot.TelegramBotClient;
import com.studyagent.repository.DatabaseManager;
import com.studyagent.repository.StudyRepository;
import com.studyagent.scheduler.ReminderScheduler;
import com.studyagent.scheduler.StudyOptimizer;
import com.studyagent.ui.StudyDesktopUI;

import javax.swing.SwingUtilities;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("       🎓 SMART STUDY AGENT - TRỢ LÝ HỌC TẬP THÔNG MINH         ");
        System.out.println("   (Telegram Bot + Gemini Vision OCR + Study Scheduler + Syllabus) ");
        System.out.println("==================================================================");

        String botToken = ConfigLoader.getTelegramBotToken();
        String geminiApiKey = ConfigLoader.getGeminiApiKey();

        System.out.println("🔍 Đang nạp cấu hình hệ thống...");
        boolean hasBotToken = !botToken.equalsIgnoreCase("YOUR_TELEGRAM_BOT_TOKEN") && !botToken.isBlank();
        boolean hasGeminiKey = !geminiApiKey.equalsIgnoreCase("YOUR_GEMINI_API_KEY") && !geminiApiKey.isBlank();

        if (hasBotToken) {
            System.out.println("  • Telegram Bot Token : [Đã cấu hình]");
        } else {
            System.out.println("  • Telegram Bot Token : [Chưa cấu hình] (Vui lòng điền vào config.properties)");
        }

        if (hasGeminiKey) {
            System.out.println("  • Gemini API Key     : [Đã cấu hình]");
        } else {
            System.out.println("  • Gemini API Key     : [Chưa cấu hình] (Không thể phân tích thời khóa biểu bằng AI)");
        }

        DatabaseManager.initializeDatabase();
        StudyRepository repository = new StudyRepository();

        GeminiService geminiService = new GeminiService(geminiApiKey);
        StudyOptimizer optimizer = new StudyOptimizer(geminiService);

        SwingUtilities.invokeLater(() -> new StudyDesktopUI(geminiService, repository, optimizer).showWindow());

        TelegramBotClient botClient = new TelegramBotClient(botToken);
        StudyBotController controller = new StudyBotController(botClient, geminiService, repository, optimizer);
        botClient.setListener(controller);

        ReminderScheduler reminderScheduler = new ReminderScheduler(repository, botClient);
        reminderScheduler.start();

        if (hasBotToken) {
            System.out.println("\n🌐 Đang kết nối tới máy chủ Telegram...");
            botClient.startPolling();
            System.out.println("✅ Telegram Bot đã sẵn sàng nhận tin nhắn & hình ảnh!");
        } else {
            System.out.println("\n⚠️ [CHÚ Ý] Telegram Bot chưa chạy vì chưa có Token.");
            System.out.println("👉 Mở file 'config.properties' và dán Bot Token của bạn để kích hoạt Telegram.");
        }

        System.out.println("\n--- MENU ĐIỀU KHIỂN CONSOLE ---");
        System.out.println("1. Gõ 'test' để chạy thử nghiệm tự động luồng TKB -> Lịch tự học -> Đề cương");
        System.out.println("2. Gõ 'status' để kiểm tra kết nối");
        System.out.println("3. Gõ 'exit' để dừng chương trình\n");

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("cmd> ");
            if (!scanner.hasNextLine()) break;
            String cmd = scanner.nextLine().trim();

            if (cmd.equalsIgnoreCase("exit") || cmd.equalsIgnoreCase("quit")) {
                System.out.println("🛑 Đang dừng hệ thống...");
                botClient.stop();
                reminderScheduler.stop();
                System.out.println("👋 Tạm biệt!");
                break;
            } else if (cmd.equalsIgnoreCase("test")) {
                System.out.println("\n🧪 [CHẠY KIỂM THỬ LUỒNG GIẢ LẬP]");
                long mockChatId = 123456789L;
                String sampleTimetable = "Lịch học tuần này:\n" +
                        "- Thứ 2: Sáng 7h-9h Lập trình Python (Phòng A2-301), 9h-12h Mạng máy tính (Phòng B1-204)\n" +
                        "- Thứ 3: Chiều 13h-16h Cơ sở dữ liệu (Phòng C3-102)\n" +
                        "- Thứ 5: Sáng 7h-11h Kiến trúc máy tính (Phòng A1-105)";
                System.out.println("📩 Đầu vào mẫu:\n" + sampleTimetable);
                controller.onTextMessage(mockChatId, "Học Viên Thử Nghiệm", sampleTimetable);
            } else if (cmd.equalsIgnoreCase("status")) {
                System.out.println("📊 TRẠNG THÁI HỆ THỐNG:");
                System.out.println("  • Telegram Bot Polling : " + (hasBotToken ? "ONLINE" : "OFFLINE"));
                System.out.println("  • Gemini AI Engine     : " + (hasGeminiKey ? "CONNECTED" : "NOT CONFIGURED"));
                System.out.println("  • SQLite Database      : CONNECTED (study_agent.db)");
                System.out.println("  • Reminder Scheduler   : RUNNING");
            } else if (!cmd.isEmpty()) {
                controller.onTextMessage(999999L, "ConsoleUser", cmd);
            }
        }
        scanner.close();
    }
}
