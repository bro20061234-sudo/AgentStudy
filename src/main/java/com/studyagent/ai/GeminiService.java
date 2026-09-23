package com.studyagent.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.studyagent.model.StudySession;
import com.studyagent.model.SubjectSyllabus;
import com.studyagent.model.TimetableEntry;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class GeminiService {
    private final String apiKey;
    private final HttpClient httpClient;
    private final Gson gson;
    private static final String MODEL_NAME = "gemini-2.5-flash";

    public GeminiService(String apiKey) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        this.gson = new Gson();
    }

    public boolean hasApiKey() {
        return !apiKey.isEmpty() && !apiKey.equalsIgnoreCase("YOUR_GEMINI_API_KEY");
    }

    public List<TimetableEntry> extractTimetableFromText(String textInput) {
        if (!hasApiKey()) {
            return generateMockTimetable("Mock Mode (chưa có Gemini API Key)");
        }

        String prompt = "Bạn là trợ lý học tập thông minh. Hãy trích xuất toàn bộ thời khóa biểu từ văn bản sau thành một JSON Array hợp lệ.\n" +
                "Mỗi phần tử JSON có cấu trúc đúng định dạng sau:\n" +
                "[\n" +
                "  {\n" +
                "    \"dayOfWeek\": \"Thứ 2\",\n" +
                "    \"startTime\": \"07:00\",\n" +
                "    \"endTime\": \"09:00\",\n" +
                "    \"subjectName\": \"Tên môn học\",\n" +
                "    \"room\": \"Phòng học hoặc N/A\",\n" +
                "    \"note\": \"Ghi chú hoặc rỗng\"\n" +
                "  }\n" +
                "]\n" +
                "CHỈ TRẢ VỀ DUY NHẤT ĐỊNH DẠNG JSON ARRAY, không thêm bất kỳ văn bản giải thích nào ngoài JSON.\n\n" +
                "Văn bản thời khóa biểu:\n" + textInput;

        try {
            String jsonPayload = buildTextPayload(prompt);
            String responseBody = callGeminiApi(jsonPayload);
            String extractedText = parseGeminiResponseText(responseBody);
            return parseTimetableJson(extractedText);
        } catch (Exception e) {
            System.err.println("⚠️ [GeminiService] Lỗi gọi Gemini: " + e.getMessage());
            return generateMockTimetable("Fallback: Lỗi kết nối AI");
        }
    }

    public List<TimetableEntry> extractTimetableFromImage(byte[] imageBytes, String mimeType) {
        if (!hasApiKey()) {
            return generateMockTimetable("Ảnh TKB (Mock Mode do chưa có Gemini API Key)");
        }

        String prompt = "Bạn là trợ lý học tập AI. Hãy đọc kỹ ảnh chụp bảng thời khóa biểu này và trích xuất tất cả các buổi học.\n" +
                "Chuyển đổi thành một JSON Array duy nhất với cấu trúc:\n" +
                "[\n" +
                "  {\n" +
                "    \"dayOfWeek\": \"Thứ 2\",\n" +
                "    \"startTime\": \"07:00\",\n" +
                "    \"endTime\": \"09:00\",\n" +
                "    \"subjectName\": \"Tên môn học chuẩn\",\n" +
                "    \"room\": \"Phòng học\",\n" +
                "    \"note\": \"Tiết học hoặc ghi chú\"\n" +
                "  }\n" +
                "]\n" +
                "CHỈ TRẢ VỀ JSON ARRAY HỢP LỆ.";

        try {
            String jsonPayload = buildMultimodalPayload(prompt, imageBytes, mimeType);
            String responseBody = callGeminiApi(jsonPayload);
            String extractedText = parseGeminiResponseText(responseBody);
            return parseTimetableJson(extractedText);
        } catch (Exception e) {
            System.err.println("⚠️ [GeminiService] Lỗi Vision Gemini: " + e.getMessage());
            return generateMockTimetable("Fallback: Lỗi phân tích ảnh");
        }
    }

    public SubjectSyllabus generateSyllabus(String subjectName) {
        if (!hasApiKey()) {
            return createMockSyllabus(subjectName);
        }

        String prompt = String.format("Bạn là giáo sư đại học. Hãy xây dựng đề cương học tập chi tiết cho môn: \"%s\".\n" +
                "Trả về định dạng JSON duy nhất:\n" +
                "{\n" +
                "  \"overview\": \"Tổng quan môn học và mục tiêu\",\n" +
                "  \"syllabusContent\": \"Nội dung chi tiết theo từng chương (Chương 1: ..., Chương 2: ...), các khái niệm lý thuyết và kỹ năng thực hành\",\n" +
                "  \"examTips\": \"Chiến lược ôn thi và trọng tâm điểm số\",\n" +
                "  \"recommendedMaterials\": \"Giáo trình chính thức, sách tham khảo\"\n" +
                "}\n" +
                "Chỉ trả về JSON thuần.", subjectName);

        try {
            String jsonPayload = buildTextPayload(prompt);
            String responseBody = callGeminiApi(jsonPayload);
            String extractedText = parseGeminiResponseText(responseBody);
            return parseSyllabusJson(subjectName, extractedText);
        } catch (Exception e) {
            System.err.println("⚠️ [GeminiService] Lỗi tạo đề cương: " + e.getMessage());
            return createMockSyllabus(subjectName);
        }
    }

    public List<StudySession> optimizeStudyScheduleWithAI(List<TimetableEntry> schoolSchedule) {
        if (!hasApiKey() || schoolSchedule.isEmpty()) {
            return generateHeuristicStudySessions(schoolSchedule);
        }

        String scheduleContext = gson.toJson(schoolSchedule);
        String prompt = "Dưới đây là danh sách các buổi học trên lớp của sinh viên:\n" +
                scheduleContext + "\n\n" +
                "Hãy phân tích và đưa ra LỊCH TỰ HỌC TỐI ƯU vào các buổi tối hoặc khoảng trống trong tuần.\n" +
                "Nguyên tắc:\n" +
                "1. Spaced Repetition: Sau khi học môn nào vào ban ngày (ví dụ thứ 2 học Python), hãy xếp 1 buổi tự học môn đó vào tối cùng ngày (19:30 - 21:00).\n" +
                "2. Không xếp trùng giờ học trên lớp.\n" +
                "Trả về JSON Array gồm các phiên tự học:\n" +
                "[\n" +
                "  {\n" +
                "    \"dayOfWeek\": \"Thứ 2\",\n" +
                "    \"startTime\": \"19:30\",\n" +
                "    \"endTime\": \"21:00\",\n" +
                "    \"subjectName\": \"Python\",\n" +
                "    \"topicOverview\": \"Ôn lại cú pháp biến, giải bài tập vòng lặp\",\n" +
                "    \"preparationMaterials\": \"Xem lại slide và làm bài lab trên LeetCode\"\n" +
                "  }\n" +
                "]\n" +
                "CHỈ TRẢ VỀ JSON ARRAY.";

        try {
            String jsonPayload = buildTextPayload(prompt);
            String responseBody = callGeminiApi(jsonPayload);
            String extractedText = parseGeminiResponseText(responseBody);
            return parseStudySessionsJson(extractedText);
        } catch (Exception e) {
            System.err.println("⚠️ [GeminiService] Lỗi tối ưu lịch: " + e.getMessage());
            return generateHeuristicStudySessions(schoolSchedule);
        }
    }

    private String callGeminiApi(String jsonPayload) throws Exception {
        String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s", MODEL_NAME, apiKey);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Gemini HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private String buildTextPayload(String prompt) {
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", prompt);

        JsonArray parts = new JsonArray();
        parts.add(textPart);

        JsonObject content = new JsonObject();
        content.add("parts", parts);

        JsonArray contents = new JsonArray();
        contents.add(content);

        JsonObject root = new JsonObject();
        root.add("contents", contents);

        return gson.toJson(root);
    }

    private String buildMultimodalPayload(String prompt, byte[] imageBytes, String mimeType) {
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", prompt);

        JsonObject inlineData = new JsonObject();
        inlineData.addProperty("mimeType", mimeType != null ? mimeType : "image/jpeg");
        inlineData.addProperty("data", Base64.getEncoder().encodeToString(imageBytes));

        JsonObject imagePart = new JsonObject();
        imagePart.add("inlineData", inlineData);

        JsonArray parts = new JsonArray();
        parts.add(textPart);
        parts.add(imagePart);

        JsonObject content = new JsonObject();
        content.add("parts", parts);

        JsonArray contents = new JsonArray();
        contents.add(content);

        JsonObject root = new JsonObject();
        root.add("contents", contents);

        return gson.toJson(root);
    }

    private String parseGeminiResponseText(String responseBody) {
        JsonObject root = gson.fromJson(responseBody, JsonObject.class);
        if (root.has("candidates")) {
            JsonArray candidates = root.getAsJsonArray("candidates");
            if (candidates.size() > 0) {
                JsonObject firstCandidate = candidates.get(0).getAsJsonObject();
                JsonObject content = firstCandidate.getAsJsonObject("content");
                JsonArray parts = content.getAsJsonArray("parts");
                if (parts.size() > 0) {
                    return parts.get(0).getAsJsonObject().get("text").getAsString();
                }
            }
        }
        return "";
    }

    private String cleanJsonString(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.startsWith("```json")) s = s.substring(7);
        else if (s.startsWith("```")) s = s.substring(3);
        if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        return s.trim();
    }

    private List<TimetableEntry> parseTimetableJson(String jsonText) {
        List<TimetableEntry> entries = new ArrayList<>();
        String cleaned = cleanJsonString(jsonText);
        try {
            JsonArray array = gson.fromJson(cleaned, JsonArray.class);
            for (JsonElement el : array) {
                JsonObject obj = el.getAsJsonObject();
                TimetableEntry entry = new TimetableEntry();
                entry.setDayOfWeek(obj.has("dayOfWeek") ? obj.get("dayOfWeek").getAsString() : "Thứ 2");
                entry.setStartTime(obj.has("startTime") ? obj.get("startTime").getAsString() : "07:00");
                entry.setEndTime(obj.has("endTime") ? obj.get("endTime").getAsString() : "09:00");
                entry.setSubjectName(obj.has("subjectName") ? obj.get("subjectName").getAsString() : "Chưa rõ môn");
                entry.setRoom(obj.has("room") ? obj.get("room").getAsString() : "N/A");
                entry.setNote(obj.has("note") ? obj.get("note").getAsString() : "");
                entries.add(entry);
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi parse JSON Timetable: " + e.getMessage());
        }
        return entries;
    }

    private SubjectSyllabus parseSyllabusJson(String subjectName, String jsonText) {
        String cleaned = cleanJsonString(jsonText);
        try {
            JsonObject obj = gson.fromJson(cleaned, JsonObject.class);
            SubjectSyllabus s = new SubjectSyllabus();
            s.setSubjectName(subjectName);
            s.setOverview(obj.has("overview") ? obj.get("overview").getAsString() : "Môn học nền tảng.");
            s.setSyllabusContent(obj.has("syllabusContent") ? obj.get("syllabusContent").getAsString() : "Chi tiết theo đề cương chuẩn.");
            s.setExamTips(obj.has("examTips") ? obj.get("examTips").getAsString() : "Luyện tập bài tập thường xuyên.");
            s.setRecommendedMaterials(obj.has("recommendedMaterials") ? obj.get("recommendedMaterials").getAsString() : "Giáo trình chính thức của trường.");
            return s;
        } catch (Exception e) {
            return createMockSyllabus(subjectName);
        }
    }

    private List<StudySession> parseStudySessionsJson(String jsonText) {
        List<StudySession> sessions = new ArrayList<>();
        String cleaned = cleanJsonString(jsonText);
        try {
            JsonArray array = gson.fromJson(cleaned, JsonArray.class);
            for (JsonElement el : array) {
                JsonObject obj = el.getAsJsonObject();
                StudySession s = new StudySession();
                s.setDayOfWeek(obj.has("dayOfWeek") ? obj.get("dayOfWeek").getAsString() : "Thứ 2");
                s.setStartTime(obj.has("startTime") ? obj.get("startTime").getAsString() : "19:30");
                s.setEndTime(obj.has("endTime") ? obj.get("endTime").getAsString() : "21:00");
                s.setSubjectName(obj.has("subjectName") ? obj.get("subjectName").getAsString() : "Tự học");
                s.setTopicOverview(obj.has("topicOverview") ? obj.get("topicOverview").getAsString() : "Củng cố kiến thức");
                s.setPreparationMaterials(obj.has("preparationMaterials") ? obj.get("preparationMaterials").getAsString() : "Đọc giáo trình");
                sessions.add(s);
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi parse StudySessions: " + e.getMessage());
        }
        return sessions;
    }

    public List<TimetableEntry> generateMockTimetable(String note) {
        List<TimetableEntry> mock = new ArrayList<>();
        mock.add(new TimetableEntry("Thứ 2", "07:00", "09:00", "Lập trình Python", "A2-301", note));
        mock.add(new TimetableEntry("Thứ 2", "09:15", "11:45", "Mạng máy tính", "B1-204", note));
        mock.add(new TimetableEntry("Thứ 3", "18:00", "20:30", "Thực hành Lập trình Mạng", "Lab-IT", note));
        mock.add(new TimetableEntry("Thứ 5", "13:00", "16:30", "Hệ quản trị CSDL", "C3-102", note));
        return mock;
    }

    public List<StudySession> generateHeuristicStudySessions(List<TimetableEntry> schoolSchedule) {
        List<StudySession> sessions = new ArrayList<>();
        for (TimetableEntry entry : schoolSchedule) {
            StudySession session = new StudySession();
            session.setDayOfWeek(entry.getDayOfWeek());
            session.setStartTime("19:30");
            session.setEndTime("21:00");
            session.setSubjectName(entry.getSubjectName());
            session.setTopicOverview("Ôn tập lại các kiến thức trọng tâm đã học trên lớp môn " + entry.getSubjectName());
            session.setPreparationMaterials("Xem lại slide bài giảng, giải lại các bài tập ví dụ trên lớp và làm bài tập về nhà.");
            sessions.add(session);
        }
        return sessions;
    }

    public SubjectSyllabus createMockSyllabus(String subjectName) {
        return new SubjectSyllabus(
                subjectName,
                "Môn học " + subjectName + " cung cấp kiến thức nền tảng và kỹ năng áp dụng thực tiễn trong ngành Công nghệ Thông tin.",
                "• Tuần 1-3: Các khái niệm cơ bản & Môi trường thực thi.\n• Tuần 4-7: Cấu trúc điều khiển, thuật toán và module cốt lõi.\n• Tuần 8-12: Kỹ thuật nâng cao, tích hợp hệ thống.\n• Tuần 13-15: Đồ án môn học & Ôn tập tổng kết.",
                "Ôn chắc các dạng bài tập thực hành trên lớp; nắm vững lý thuyết và các trường hợp biên.",
                "Giáo trình chính thức của trường + Tài liệu thực hành Lab."
        );
    }
}
