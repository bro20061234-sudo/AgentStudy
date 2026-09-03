package com.studyagent.parser;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bộ bóc tách ngôn ngữ dự phòng (Fallback) sử dụng Regular Expressions khi không có mạng/AI.
 * @author Thành viên 2 (AI/NLP)
 */
public class RegexAIParser implements AIParser {

    @Override
    public StudyRequest parse(String input) {
        System.out.println("🔍 [RegexAIParser] Bóc tách dữ liệu bằng Regex...");
        StudyRequest request = new StudyRequest();
        request.setRawInput(input);

        // TODO (TV 2):
        // 1. Viết Regex nhận diện số giờ/phút: "(\d+)\s*(tiếng|giờ|h|phút|p)"
        //    Nếu là tiếng/giờ -> nhân 60 thành phút.
        // 2. Viết Regex nhận diện deadline: "(trước|vào lúc)\s*(\d{1,2})h?\s*(hôm nay|ngày mai)?"
        // 3. Trích xuất tên môn học hoặc đặt mặc định nếu không tìm thấy.

        int minutes = 60; // Mặc định 60 phút
        Pattern timePattern = Pattern.compile("(\\d+)\\s*(tiếng|giờ|h|phút|p)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = timePattern.matcher(input);
        if (matcher.find()) {
            int val = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2).toLowerCase();
            if (unit.startsWith("t") || unit.startsWith("g") || unit.equals("h")) {
                minutes = val * 60;
            } else {
                minutes = val;
            }
        }

        request.setTitle(input.length() > 30 ? input.substring(0, 30) + "..." : input);
        request.setSubject("Chung");
        request.setEstimatedMinutes(minutes);
        request.setDeadline(LocalDateTime.now().plusDays(1).withHour(21).withMinute(0));
        request.setPriority("MEDIUM");

        return request;
    }
}
