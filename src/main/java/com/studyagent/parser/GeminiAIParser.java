package com.studyagent.parser;

import java.time.LocalDateTime;

/**
 * Triển khai kết nối Google Gemini API để phân tích câu tự nhiên bằng AI.
 * @author Thành viên 2 (AI/NLP)
 */
public class GeminiAIParser implements AIParser {
    private String apiKey;
    private RegexAIParser fallbackParser;

    public GeminiAIParser(String apiKey) {
        this.apiKey = apiKey;
        this.fallbackParser = new RegexAIParser();
    }

    @Override
    public StudyRequest parse(String input) {
        System.out.println("🤖 [GeminiAIParser] Đang gửi câu lệnh đến Gemini AI...");

        // TODO (TV 2):
        // Bước 1: Tạo Prompt hướng dẫn AI trả về JSON chuẩn:
        //    Format: {"title": "...", "subject": "...", "estimatedMinutes": 120, "deadline": "2026-09-04T20:00:00", "priority": "HIGH"}
        // Bước 2: Dùng java.net.http.HttpClient để gửi HTTP POST request đến Gemini API Endpoint:
        //    https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=...
        // Bước 3: Dùng Gson để parse kết quả JSON thành đối tượng StudyRequest.
        // Bước 4: Nếu gặp lỗi mạng / mất API Key -> gọi fallbackParser.parse(input) để đảm bảo app không crash.

        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equals("YOUR_GEMINI_API_KEY")) {
            System.out.println("⚠️ [GeminiAIParser] Chưa có API Key hợp lệ. Chuyển sang dùng RegexAIParser.");
            return fallbackParser.parse(input);
        }

        // Mẫu giả lập tạm thời (Mock data) để hệ thống chạy thử được ngay
        StudyRequest request = new StudyRequest();
        request.setTitle("Làm bài tập Java OOP");
        request.setSubject("Lập trình Hướng đối tượng");
        request.setEstimatedMinutes(120);
        request.setDeadline(LocalDateTime.now().plusDays(1).withHour(20).withMinute(0));
        request.setPriority("HIGH");
        request.setRawInput(input);
        return request;
    }
}
