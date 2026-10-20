package com.studyagent.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GeminiService {
    private static final int MAX_GEMINI_ATTEMPTS = 3;
    private final String apiKey;
    private final HttpClient httpClient;
    private final Gson gson;
    private static final String MODEL_NAME = "gemini-3.8-flash";

    public GeminiService(String apiKey) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        this.gson = new Gson();
    }

    public boolean hasApiKey() {
        return !apiKey.isEmpty() && !apiKey.equalsIgnoreCase("YOUR_GEMINI_API_KEY");
    }

    public List<TimetableEntry> extractTimetableFromText(String textInput) {
        if (textInput == null || textInput.isBlank()) {
            return new ArrayList<>();
        }

        List<TimetableEntry> fallbackEntries = parseTimetableTextFallback(textInput);
        if (!hasApiKey()) {
            System.err.println("⚠️ [GeminiService] Chưa cấu hình Gemini API key; dùng phân tích dự phòng cho thời khóa biểu.");
            return fallbackEntries;
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
            List<TimetableEntry> extractedEntries = parseTimetableJson(extractedText);
            if (extractedEntries != null && !extractedEntries.isEmpty()) {
                return extractedEntries;
            }
            System.err.println("⚠️ [GeminiService] Gemini trả về dữ liệu rỗng; dùng fallback parser cho input lịch học.");
            return fallbackEntries;
        } catch (Exception e) {
            System.err.println("⚠️ [GeminiService] Lỗi gọi Gemini: " + e.getMessage() + ". Dùng parser dự phòng.");
            return fallbackEntries;
        }
    }

    public List<TimetableEntry> extractTimetableFromImage(byte[] imageBytes, String mimeType) {
        List<byte[]> list = new ArrayList<>();
        list.add(imageBytes);
        return extractTimetableFromMultipleImages(list, mimeType);
    }

    public List<TimetableEntry> extractTimetableFromMultipleImages(List<byte[]> imageBytesList, String mimeType) {
        if (!hasApiKey() || imageBytesList == null || imageBytesList.isEmpty()) {
            if (!hasApiKey()) {
                System.err.println("⚠️ [GeminiService] Chưa cấu hình Gemini API key; bỏ qua phân tích ảnh thời khóa biểu.");
            }
            return new ArrayList<>();
        }

        String prompt = "Bạn là trợ lý học tập AI. Dưới đây là các ảnh chụp bảng thời khóa biểu của cả kỳ học (có thể gồm nhiều ảnh chụp các trang hoặc các ca khác nhau).\n" +
                "Hãy phân tích toàn bộ các ảnh và trích xuất hợp nhất tất cả các buổi học trong tuần thành một JSON Array duy nhất:\n" +
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
                "CHỈ TRẢ VỀ JSON ARRAY HỢP LỆ, KHÔNG TRÙNG LẶP CÁC BUỔI HỌC GIỐNG NHAU.";

        try {
            String jsonPayload = buildMultiImagesPayload(prompt, imageBytesList, mimeType);
            String responseBody = callGeminiApi(jsonPayload);
            String extractedText = parseGeminiResponseText(responseBody);
            return parseTimetableJson(extractedText);
        } catch (Exception e) {
            System.err.println("⚠️ [GeminiService] Lỗi Vision Gemini đa ảnh: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<TimetableEntry> extractTimetableFromPdf(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return new ArrayList<>();
        }

        String extractedText = com.studyagent.parser.DocumentParser.extractTextFromPdf(pdfBytes);
        List<TimetableEntry> fallbackEntries = parseTimetablePdfFallback(pdfBytes, extractedText);

        if (!hasApiKey()) {
            if (extractedText == null || extractedText.isBlank()) {
                throw new IllegalStateException("PDF thời khóa biểu này là ảnh scan, không có văn bản để đọc trực tiếp. Cần Gemini API key hợp lệ để nhận dạng ảnh; chưa lưu lịch thiếu dữ liệu.");
            }
            validateSemesterDateRanges(fallbackEntries, extractedText);
            System.err.println("⚠️ [GeminiService] Chưa cấu hình Gemini API key; dùng phân tích dự phòng cho file PDF thời khóa biểu.");
            return fallbackEntries;
        }

        String prompt = "Bạn là trợ lý học tập AI chuyên sâu. Dưới đây là nội dung/ảnh chụp từ file PDF Thời khóa biểu dạng học kỳ (cổng quản lý đào tạo đại học như PTIT, Bách Khoa, v.v.).\n" +
                "Bảng thường gồm các cột: Mã MH, Tên môn học, Nhóm tổ, Số tín chỉ, Lớp, Thứ, Tiết bắt đầu, Số tiết, Phòng, Giảng viên, Thời gian học.\n" +
                "Quy tắc chuyển đổi:\n" +
                "1. 'dayOfWeek': Quy đổi 'Thứ' thành 'Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7', hoặc 'Chủ Nhật' (nếu là CN).\n" +
                "2. 'startTime' & 'endTime': Dựa vào 'Tiết bắt đầu' và 'Số tiết' để quy đổi sang giờ học thực tế:\n" +
                "   - Tiết 1: 07:00 (kíp 2 tiết -> 07:00 - 09:00)\n" +
                "   - Tiết 2: 08:00 (kíp 3 tiết -> 08:00 - 11:00)\n" +
                "   - Tiết 3: 09:00 (kíp 1 tiết -> 09:00 - 10:00, 2 tiết -> 09:00 - 11:00)\n" +
                "   - Tiết 4: 10:00\n" +
                "   - Tiết 6: 12:00 (kíp 4 tiết -> 12:00 - 16:00)\n" +
                "   - Tiết 7: 13:00 (kíp 1 tiết -> 13:00 - 14:00, 2 tiết -> 13:00 - 15:00)\n" +
                "   - Tiết 8: 14:00\n" +
                "   - Tiết 10: 16:00 (kíp 2 tiết -> 16:00 - 18:00)\n" +
                "   - Tiết 12/13 (ca tối): 18:00 hoặc 19:00 (kíp 4 tiết -> 18:00 - 21:30)\n" +
                "   - Tiết 18 (học trực tuyến LMS): 18:00 - 19:00 hoặc 19:00 - 20:00\n" +
                "3. 'subjectName': Tên môn học chính xác, đầy đủ (loại bỏ mã môn).\n" +
                "4. 'room': Phòng học ghi trên bảng (VD: 302-A2, LMS, DB-LAB, G05-A2, CODE.PTIT.EDU.VN, 405-A2).\n" +
                "5. 'note': Bắt buộc giữ nguyên khoảng ngày học trên dòng (VD: 17/10/2026 đến 21/11/2026), cùng giảng viên/mã lớp nếu có.\n\n" +
                "Mỗi dòng/lần học có khoảng ngày riêng phải là một phần tử riêng. Không gộp hai dòng cùng môn, thứ, giờ nếu khoảng ngày khác nhau; nếu gộp sẽ làm mất lịch cả kỳ.\n" +
                "Các trang dọc có thể được gửi thêm một bản xoay ngang; đó vẫn là cùng một trang, chỉ trích xuất mỗi dòng một lần.\n" +
                "Không biến lịch học kỳ thành một mẫu tuần. Hãy đọc tất cả các trang và trích xuất đầy đủ mọi dòng, kể cả các môn có nhiều đợt ngày học.\n" +
                "Hãy trả về lịch theo từng dòng trong một JSON Array duy nhất:\n" +
                "[\n" +
                "  {\n" +
                "    \"dayOfWeek\": \"Thứ 2\",\n" +
                "    \"startTime\": \"08:00\",\n" +
                "    \"endTime\": \"11:00\",\n" +
                "    \"subjectName\": \"Tên môn học\",\n" +
                "    \"room\": \"Phòng học\",\n" +
                "    \"note\": \"GV: Nguyễn Văn A | 17/10/2026 đến 21/11/2026\"\n" +
                "  }\n" +
                "]\n" +
                "CHỈ TRẢ VỀ DUY NHẤT JSON ARRAY HỢP LỆ. Chỉ loại bỏ dòng trùng hoàn toàn cả môn, thứ, giờ, phòng VÀ khoảng ngày.";

        // Chiến lược 1: Render các trang PDF thành ảnh và dùng Gemini Multimodal Vision
        List<byte[]> pageImages = com.studyagent.parser.DocumentParser.renderPdfToImages(pdfBytes);
        if (pageImages != null && !pageImages.isEmpty()) {
            try {
                String jsonPayload = buildMultiImagesPayload(prompt, pageImages, "image/jpeg");
                String responseBody = callGeminiApi(jsonPayload);
                String extracted = parseGeminiResponseText(responseBody);
                List<TimetableEntry> entries = parseTimetableJson(extracted);
                if (entries != null && !entries.isEmpty()) {
                    validateSemesterDateRanges(entries, extractedText);
                    return entries;
                }
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                System.err.println("⚠️ [GeminiService] Lỗi Vision PDF qua ảnh: " + e.getMessage());
            }
        }

        // Chiến lược 2: Nếu có văn bản trích xuất từ PDF, gửi qua Gemini Text API
        if (extractedText != null && !extractedText.isBlank()) {
            try {
                String textPrompt = prompt + "\n\nNội dung văn bản bóc tách từ file PDF:\n" + extractedText;
                String jsonPayload = buildTextPayload(textPrompt);
                String responseBody = callGeminiApi(jsonPayload);
                String extracted = parseGeminiResponseText(responseBody);
                List<TimetableEntry> entries = parseTimetableJson(extracted);
                if (entries != null && !entries.isEmpty()) {
                    validateSemesterDateRanges(entries, extractedText);
                    return entries;
                }
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                System.err.println("⚠️ [GeminiService] Lỗi Gemini phân tích text PDF: " + e.getMessage());
            }
        }

        return fallbackEntries;
    }

    private static void validateSemesterDateRanges(List<TimetableEntry> entries, String extractedText) {
        boolean semesterDocument = extractedText == null || extractedText.isBlank()
                || extractedText.toLowerCase(java.util.Locale.ROOT).contains("học kỳ");
        if (semesterDocument && entries != null
                && entries.stream().anyMatch(entry -> !containsDateRange(entry.getNote()))) {
            throw new IllegalStateException("AI chưa đọc được khoảng ngày cho tất cả các dòng trong PDF thời khóa biểu học kỳ; chưa lưu lịch có nguy cơ thiếu hoặc lặp tuần. Hãy dùng PDF rõ nét hơn hoặc nhập lịch có kèm ngày bắt đầu/kết thúc.");
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
                "Với mỗi buổi học trên lớp, hãy gợi ý nội dung ôn tập ngắn gọn cho môn đó.\n" +
                "Chỉ trả về gợi ý nội dung; chương trình sẽ tự xếp thời gian học để đảm bảo không trùng lịch.\n" +
                "Trả về JSON Array gồm các gợi ý:\n" +
                "[\n" +
                "  {\n" +
                "    \"dayOfWeek\": \"Thứ 2\",\n" +
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
        String[] candidateModels = {MODEL_NAME, "gemini-2.5-flash", "gemini-1.5-flash"};
        Exception lastException = null;

        for (String model : candidateModels) {
            String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s", model, apiKey);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            for (int attempt = 1; attempt <= MAX_GEMINI_ATTEMPTS; attempt++) {
                try {
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        return response.body();
                    }
                    if (response.statusCode() == 404) {
                        System.err.println("⚠️ [GeminiService] Model " + model + " không tồn tại (404); thử model khác.");
                        break;
                    }
                    if (!isRetryableGeminiStatus(response.statusCode()) || attempt == MAX_GEMINI_ATTEMPTS) {
                        lastException = new RuntimeException("Gemini (" + model + ") HTTP " + response.statusCode() + ": " + response.body());
                        break;
                    }

                    long delayMillis = 1000L * attempt;
                    System.err.println("⚠️ [GeminiService] Gemini HTTP " + response.statusCode()
                            + "; thử lại lần " + (attempt + 1) + "/" + MAX_GEMINI_ATTEMPTS
                            + " sau " + delayMillis / 1000 + " giây.");
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw e;
                } catch (Exception e) {
                    lastException = e;
                }
            }
        }
        if (lastException != null) throw lastException;
        throw new IllegalStateException("Gemini không trả về kết quả sau các lần thử.");
    }

    private boolean isRetryableGeminiStatus(int statusCode) {
        return statusCode == 429 || statusCode == 500 || statusCode == 502
                || statusCode == 503 || statusCode == 504;
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

    private String buildMultiImagesPayload(String prompt, List<byte[]> imageBytesList, String mimeType) {
        JsonArray parts = new JsonArray();
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", prompt);
        parts.add(textPart);

        String type = mimeType != null ? mimeType : "image/jpeg";
        for (byte[] imageBytes : imageBytesList) {
            JsonObject inlineData = new JsonObject();
            inlineData.addProperty("mimeType", type);
            inlineData.addProperty("data", Base64.getEncoder().encodeToString(imageBytes));

            JsonObject imagePart = new JsonObject();
            imagePart.add("inlineData", inlineData);
            parts.add(imagePart);
        }

        JsonObject content = new JsonObject();
        content.add("parts", parts);

        JsonArray contents = new JsonArray();
        contents.add(content);

        JsonObject root = new JsonObject();
        root.add("contents", contents);

        return gson.toJson(root);
    }

    public List<com.studyagent.model.WeeklyCurriculum> generateCurriculumForSessions(
            String subjectName, String textbookContent, int sessionCount) {
        if (sessionCount <= 0) {
            throw new IllegalArgumentException("Số buổi học thêm phải lớn hơn 0.");
        }
        if (!hasApiKey()) {
            throw new IllegalStateException("Chưa cấu hình Gemini API key hợp lệ. Giáo trình đã được lưu nhưng chưa thể tạo bài giảng AI.");
        }
        if (textbookContent == null || textbookContent.isBlank()) {
            throw new IllegalArgumentException("Không có văn bản giáo trình để phân tích.");
        }

        // Tăng giới hạn đọc giáo trình lên 80,000 ký tự để bao quát mục lục và các chương chính
        String sampleText = textbookContent.length() > 80000 ? textbookContent.substring(0, 80000) : textbookContent;

        try {
            // TỐI ƯU TỐC ĐỘ: Gom nhóm 6 buổi / 1 lần gọi Gemini (Batching) thay vì gọi 15-20 lần tuần tự
            List<com.studyagent.model.WeeklyCurriculum> allLessons = new ArrayList<>();
            int batchSize = 6;
            int totalBatches = (int) Math.ceil((double) sessionCount / batchSize);

            for (int batch = 0; batch < totalBatches; batch++) {
                int startSession = batch * batchSize + 1;
                int endSession = Math.min((batch + 1) * batchSize, sessionCount);
                int countInBatch = endSession - startSession + 1;

                String batchPrompt = String.format(
                        "Bạn là giảng viên đại học môn \"%s\". Dưới đây là nội dung giáo trình.\n" +
                        "Hãy phân tích và viết bài giảng chi tiết chia đều cho các Buổi từ Buổi %d đến Buổi %d (tổng cộng %d buổi trong kế hoạch %d buổi cả kỳ).\n\n" +
                        "YÊU CẦU BẮT BUỘC CHO TỪNG BUỔI:\n" +
                        "- Phân bổ kiến thức đều, bám sát giáo trình, không lặp lại giữa các buổi.\n" +
                        "- Mỗi buổi học phải có tên chương/chủ đề cụ thể và bài giảng giải thích cặn kẽ.\n" +
                        "- Trong 'detailedSyllabus' của mỗi buổi BẮT BUỘC trình bày đầy đủ 6 phần với các tiêu đề in hoa sau:\n" +
                        "MỤC TIÊU BUỔI HỌC\n" +
                        "KIẾN THỨC TIÊN QUYẾT\n" +
                        "BÀI GIẢNG CHI TIẾT\n" +
                        "VÍ DỤ MINH HỌA\n" +
                        "BÀI TẬP THỰC HÀNH VÀ LỜI GIẢI\n" +
                        "TÓM TẮT VÀ GHI NHỚ\n\n" +
                        "Trả về duy nhất một JSON Array hợp lệ gồm đúng %d phần tử (đánh số weekNumber từ %d đến %d):\n" +
                        "[\n" +
                        "  {\n" +
                        "    \"weekNumber\": %d,\n" +
                        "    \"chapterTitle\": \"Chương / Chủ đề buổi này\",\n" +
                        "    \"detailedSyllabus\": \"<nội dung chi tiết 6 phần ở trên>\"\n" +
                        "  }\n" +
                        "]\n" +
                        "CHỈ TRẢ VỀ JSON ARRAY HỢP LỆ, KHÔNG DÙNG CODE BLOCK VÀ KHÔNG KÈM LỜI DẪN NGOÀI JSON.\n\n" +
                        "GIÁO TRÌNH THAM KHẢO:\n--------------------\n%s\n--------------------\n",
                        subjectName, startSession, endSession, countInBatch, sessionCount,
                        countInBatch, startSession, endSession, startSession, sampleText);

                List<com.studyagent.model.WeeklyCurriculum> batchResults = null;
                try {
                    batchResults = requestCurriculum(batchPrompt, subjectName);
                } catch (Exception e) {
                    System.err.println("⚠️ [GeminiService] Lỗi batch " + (batch + 1) + ": " + e.getMessage() + "; tự động dùng soạn dự phòng cho batch này.");
                }

                if (batchResults == null || batchResults.isEmpty()) {
                    for (int s = startSession; s <= endSession; s++) {
                        allLessons.add(createFallbackLessonForSession(subjectName, s, "Chương " + s, "Nội dung trọng tâm buổi " + s));
                    }
                } else {
                    for (int s = startSession; s <= endSession; s++) {
                        final int expectedWeek = s;
                        com.studyagent.model.WeeklyCurriculum found = batchResults.stream()
                                .filter(c -> c.getWeekNumber() == expectedWeek)
                                .findFirst()
                                .orElse(null);
                        if (found == null) {
                            int fallbackIdx = s - startSession;
                            if (fallbackIdx < batchResults.size()) found = batchResults.get(fallbackIdx);
                        }
                        if (found == null) {
                            found = createFallbackLessonForSession(subjectName, s, "Chương " + s, "Nội dung trọng tâm buổi " + s);
                        } else {
                            found.setWeekNumber(s);
                            found.setSubjectName(subjectName);
                            found.setDetailedSyllabus(ensureStandardLessonSections(found.getDetailedSyllabus(), found.getChapterTitle(), ""));
                        }
                        allLessons.add(found);
                    }
                }
            }
            return allLessons;
        } catch (Exception e) {
            System.err.println("⚠️ [GeminiService] Lỗi tạo tiến trình giáo trình: " + e.getMessage());
            List<com.studyagent.model.WeeklyCurriculum> fallback = generateFallbackCurriculum(subjectName, textbookContent, sessionCount);
            if (!fallback.isEmpty()) {
                System.out.println("✅ [GeminiService] Đã áp dụng phân tích dự phòng thành công cho " + fallback.size() + " buổi môn " + subjectName);
                return fallback;
            }
            throw new IllegalStateException("Không thể tạo bài giảng chi tiết từ giáo trình: " + e.getMessage(), e);
        }
    }

    private List<com.studyagent.model.WeeklyCurriculum> adjustOutlineToExactCount(
            List<com.studyagent.model.WeeklyCurriculum> outline, int targetCount, String subjectName) {
        if (outline == null || outline.isEmpty()) {
            return generateMockWeeklyCurriculum(subjectName, targetCount);
        }
        outline.sort(java.util.Comparator.comparingInt(com.studyagent.model.WeeklyCurriculum::getWeekNumber));
        if (outline.size() == targetCount) {
            for (int i = 0; i < targetCount; i++) {
                com.studyagent.model.WeeklyCurriculum item = outline.get(i);
                item.setWeekNumber(i + 1);
                item.setSubjectName(subjectName);
            }
            return outline;
        }

        List<com.studyagent.model.WeeklyCurriculum> result = new ArrayList<>();
        int originalSize = outline.size();

        if (originalSize < targetCount) {
            for (int i = 0; i < targetCount; i++) {
                int origIdx = (i * originalSize) / targetCount;
                com.studyagent.model.WeeklyCurriculum orig = outline.get(Math.min(origIdx, originalSize - 1));

                int partInChapter = 0;
                int totalPartsInChapter = 0;
                for (int k = 0; k < targetCount; k++) {
                    if ((k * originalSize) / targetCount == origIdx) {
                        totalPartsInChapter++;
                        if (k <= i) partInChapter++;
                    }
                }

                String title = orig.getChapterTitle();
                if (totalPartsInChapter > 1) {
                    title = title + " (Phần " + partInChapter + "/" + totalPartsInChapter + ")";
                }

                com.studyagent.model.WeeklyCurriculum item = new com.studyagent.model.WeeklyCurriculum();
                item.setSubjectName(subjectName);
                item.setWeekNumber(i + 1);
                item.setChapterTitle(title);
                item.setDetailedSyllabus(orig.getDetailedSyllabus());
                result.add(item);
            }
        } else {
            for (int i = 0; i < targetCount; i++) {
                int startIdx = (i * originalSize) / targetCount;
                int endIdx = ((i + 1) * originalSize) / targetCount;
                if (endIdx <= startIdx) endIdx = startIdx + 1;
                if (endIdx > originalSize) endIdx = originalSize;

                StringBuilder combinedTitle = new StringBuilder();
                StringBuilder combinedScope = new StringBuilder();
                for (int k = startIdx; k < endIdx; k++) {
                    com.studyagent.model.WeeklyCurriculum curr = outline.get(k);
                    if (combinedTitle.length() > 0) combinedTitle.append(" & ");
                    combinedTitle.append(curr.getChapterTitle());
                    if (combinedScope.length() > 0) combinedScope.append("\n");
                    combinedScope.append(curr.getDetailedSyllabus());
                }

                com.studyagent.model.WeeklyCurriculum item = new com.studyagent.model.WeeklyCurriculum();
                item.setSubjectName(subjectName);
                item.setWeekNumber(i + 1);
                item.setChapterTitle(combinedTitle.toString());
                item.setDetailedSyllabus(combinedScope.toString());
                result.add(item);
            }
        }
        return result;
    }

    private String ensureStandardLessonSections(String content, String chapterTitle, String scope) {
        if (content == null || content.isBlank()) {
            content = scope != null ? scope : "";
        }
        String normalized = java.text.Normalizer.normalize(content, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(java.util.Locale.ROOT);

        StringBuilder sb = new StringBuilder();
        if (!normalized.contains("MUC TIEU")) {
            sb.append("MỤC TIÊU BUỔI HỌC\n")
              .append("- Nắm vững toàn diện kiến thức trọng tâm của ").append(chapterTitle).append(".\n")
              .append("- Hiểu rõ các nguyên lý cốt lõi và vận dụng thành thạo vào giải quyết bài tập thực tế.\n")
              .append("- Rèn luyện tư duy phân tích và kỹ năng thực hành theo yêu cầu của giáo trình.\n\n");
        }
        if (!normalized.contains("KIEN THUC TIEN QUYET") && !normalized.contains("KIEN THUC TRONG TAM")) {
            sb.append("KIẾN THỨC TIÊN QUYẾT\n")
              .append("- Ôn tập lại các khái niệm cơ bản đã học ở các phần trước.\n")
              .append("- Chuẩn bị đầy đủ tài liệu giáo trình và môi trường thực hành liên quan đến ").append(chapterTitle).append(".\n\n");
        }
        if (!normalized.contains("BAI GIANG CHI TIET") && !normalized.contains("NOI DUNG BAI GIANG")) {
            sb.append("BÀI GIẢNG CHI TIẾT\n");
        }

        sb.append(content).append("\n\n");

        if (!normalized.contains("VI DU")) {
            sb.append("VÍ DỤ MINH HỌA\n")
              .append("- Ví dụ áp dụng thực tiễn cho ").append(chapterTitle).append(":\n")
              .append("  + Tình huống bài toán: Phân tích và xử lý trường hợp điển hình trong giáo trình.\n")
              .append("  + Hướng dẫn từng bước: Xác định yêu cầu, áp dụng phương pháp chuẩn và giải thích kết quả chi tiết.\n\n");
        }
        if (!normalized.contains("BAI TAP")) {
            sb.append("BÀI TẬP THỰC HÀNH VÀ LỜI GIẢI\n")
              .append("- Bài tập 1: Trình bày và giải thích nguyên lý hoạt động của các thành phần trong ").append(chapterTitle).append(".\n")
              .append("  * Lời giải chi tiết: Dựa trên giáo trình, phân tích cấu trúc và quy trình thực hiện theo chuẩn lý thuyết.\n")
              .append("- Bài tập 2: Vận dụng kiến thức để giải bài tập tổng hợp và kiểm tra kết quả.\n")
              .append("  * Lời giải chi tiết: Thực hiện các bước tính toán/lập trình cụ thể để đạt đáp án chính xác.\n\n");
        }
        if (!normalized.contains("TOM TAT") && !normalized.contains("GHI NHO") && !normalized.contains("TONG KET")) {
            sb.append("TÓM TẮT VÀ GHI NHỚ\n")
              .append("- Ghi nhớ định nghĩa, công thức và quy trình cốt lõi của ").append(chapterTitle).append(".\n")
              .append("- Thường xuyên thực hành và tự kiểm tra kiến thức để khắc sâu bài học.\n");
        }

        return sb.toString().trim();
    }

    private com.studyagent.model.WeeklyCurriculum createFallbackLessonForSession(
            String subjectName, int sessionNumber, String chapterTitle, String scope) {
        StringBuilder sb = new StringBuilder();
        sb.append("MỤC TIÊU BUỔI HỌC\n")
          .append("- Nắm chắc toàn bộ kiến thức lý thuyết trọng tâm của ").append(chapterTitle).append(".\n")
          .append("- Hiểu sâu các khái niệm định nghĩa, cấu trúc và phương pháp giải quyết vấn đề.\n")
          .append("- Vận dụng kiến thức giải quyết thành thạo các dạng bài tập thực hành trong giáo trình.\n\n");

        sb.append("KIẾN THỨC TIÊN QUYẾT\n")
          .append("- Đọc trước nội dung ").append(chapterTitle).append(" trong tài liệu giáo trình chính thức.\n")
          .append("- Rà soát các bài tập và kiến thức liên quan từ các buổi học trước để liên kết mạch lạc.\n\n");

        sb.append("BÀI GIẢNG CHI TIẾT\n")
          .append("1. Khái quát chung và bản chất lý thuyết:\n")
          .append(scope != null && !scope.isBlank() ? scope : "Nghiên cứu chi tiết các định nghĩa, nguyên tắc và mô hình hoạt động trong chương.").append("\n")
          .append("2. Phân tích các thành phần cốt lõi và quy trình xử lý:\n")
          .append("Mỗi thành phần trong chương đóng vai trò quan trọng trong việc xây dựng hệ thống kiến thức hoàn chỉnh. Sinh viên cần chú ý các bước thao tác, quy tắc ràng buộc và các trường hợp đặc biệt thường gặp trong thực tế.\n")
          .append("3. Phương pháp tiếp cận và kỹ thuật thực hành:\n")
          .append("Áp dụng trực tiếp các phương pháp chuẩn mực được trình bày trong giáo trình để giải quyết các bài toán môn học.\n\n");

        sb.append("VÍ DỤ MINH HỌA\n")
          .append("- Ví dụ mẫu 1: Phân tích tình huống cơ bản của ").append(chapterTitle).append(".\n")
          .append("  * Cách giải: Phân tích đề bài, xác định phương pháp áp dụng và trình bày lời giải từng bước rõ ràng.\n")
          .append("- Ví dụ mẫu 2: Bài toán nâng cao mở rộng khái niệm để sinh viên hiểu sâu bản chất lý thuyết.\n\n");

        sb.append("BÀI TẬP THỰC HÀNH VÀ LỜI GIẢI\n")
          .append("- Bài tập 1: Tự luận ôn tập củng cố các định lý và định nghĩa của ").append(chapterTitle).append(".\n")
          .append("  * Lời giải chi tiết: Trình bày đầy đủ luận điểm, công thức và giải thích cặn kẽ.\n")
          .append("- Bài tập 2: Bài toán thực hành tính toán/cài đặt tương ứng với nội dung buổi học.\n")
          .append("  * Hướng dẫn giải: Làm theo quy trình 3 bước: Xác định biến/dữ liệu -> Áp dụng công thức/thuật toán -> Đối chiếu kết quả.\n\n");

        sb.append("TÓM TẮT VÀ GHI NHỚ\n")
          .append("- Ghi nhớ 3 điểm cốt lõi nhất của ").append(chapterTitle).append(".\n")
          .append("- Hoàn thành toàn bộ bài tập tự luyện và chuẩn bị câu hỏi thảo luận cho buổi tiếp theo.\n");

        com.studyagent.model.WeeklyCurriculum lesson = new com.studyagent.model.WeeklyCurriculum();
        lesson.setSubjectName(subjectName);
        lesson.setWeekNumber(sessionNumber);
        lesson.setChapterTitle(chapterTitle);
        lesson.setDetailedSyllabus(sb.toString());
        return lesson;
    }

    public List<com.studyagent.model.WeeklyCurriculum> generateFallbackCurriculum(
            String subjectName, String textbookContent, int totalSessions) {
        List<com.studyagent.model.WeeklyCurriculum> list = new ArrayList<>();
        if (totalSessions <= 0) totalSessions = 10;

        List<String> detectedChapters = new ArrayList<>();
        if (textbookContent != null && !textbookContent.isBlank()) {
            Pattern chapterPattern = Pattern.compile(
                    "^\\s*(?:chương|chuong|chapter|phần|phan|bài|bai)\\s*(\\d+|[ivxlcdm]+)[:.]?\\s*([^\\n\\r]{2,100})",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE);
            Matcher m = chapterPattern.matcher(textbookContent);
            while (m.find()) {
                String title = m.group().trim().replaceAll("\\s+", " ");
                if (!detectedChapters.contains(title)) {
                    detectedChapters.add(title);
                }
            }
        }

        if (detectedChapters.isEmpty()) {
            return generateMockWeeklyCurriculum(subjectName, totalSessions);
        }

        List<com.studyagent.model.WeeklyCurriculum> outline = new ArrayList<>();
        for (int i = 0; i < detectedChapters.size(); i++) {
            com.studyagent.model.WeeklyCurriculum item = new com.studyagent.model.WeeklyCurriculum();
            item.setWeekNumber(i + 1);
            item.setChapterTitle(detectedChapters.get(i));
            item.setDetailedSyllabus("Phạm vi kiến thức trọng tâm của " + detectedChapters.get(i));
            outline.add(item);
        }

        List<com.studyagent.model.WeeklyCurriculum> adjusted = adjustOutlineToExactCount(outline, totalSessions, subjectName);
        for (int i = 0; i < adjusted.size(); i++) {
            com.studyagent.model.WeeklyCurriculum c = adjusted.get(i);
            list.add(createFallbackLessonForSession(subjectName, i + 1, c.getChapterTitle(), c.getDetailedSyllabus()));
        }
        return list;
    }

    private String buildDetailedLessonPrompt(String subjectName, int sessionCount, int sessionNumber,
                                             String chapterTitle, String scope, String textbookContent) {
        return String.format("Bạn là giảng viên đại học môn \"%s\". Hãy soạn bài giảng tự học chi tiết cho Buổi %d/%d.\n" +
                "Chủ đề / Chương: %s\n" +
                "Phạm vi kiến thức cần dạy: %s\n\n" +
                "NỘI DUNG GIÁO TRÌNH THAM KHẢO:\n" +
                "--------------------\n%s\n--------------------\n\n" +
                "YÊU CẦU BẮT BUỘC:\n" +
                "Bài giảng phải đầy đủ và chi tiết (tối thiểu 600-900 từ tiếng Việt), giúp sinh viên tự học nắm chắc kiến thức.\n" +
                "BẮT BUỘC TRÌNH BÀY ĐẦY ĐỦ 6 PHẦN VỚI CÁC TIÊU ĐỀ IN HOA SAU ĐÂY:\n" +
                "MỤC TIÊU BUỔI HỌC\n" +
                "(Nêu rõ các mục tiêu kiến thức và kỹ năng cần đạt sau buổi học)\n\n" +
                "KIẾN THỨC TIÊN QUYẾT\n" +
                "(Ôn tập ngắn gọn các khái niệm nền tảng cần biết trước khi học bài này)\n\n" +
                "BÀI GIẢNG CHI TIẾT\n" +
                "(Giải thích cặn kẽ từng khái niệm, định lý, công thức, quy trình hoặc thuật toán có trong giáo trình)\n\n" +
                "VÍ DỤ MINH HỌA\n" +
                "(Đưa ra ít nhất 1-2 ví dụ thực tế kèm cách giải từng bước và giải thích kết quả)\n\n" +
                "BÀI TẬP THỰC HÀNH VÀ LỜI GIẢI\n" +
                "(Cung cấp ít nhất 2 bài tập áp dụng kèm đáp án và hướng dẫn giải chi tiết)\n\n" +
                "TÓM TẮT VÀ GHI NHỚ\n" +
                "(Tổng hợp các điểm then chốt nhất cần nhớ sau buổi học)\n\n" +
                "Trả về duy nhất định dạng JSON Array chứa đúng 1 phần tử:\n" +
                "[{\"weekNumber\": %d, \"chapterTitle\": \"%s\", \"detailedSyllabus\": \"<toàn bộ nội dung bài giảng 6 phần ở trên>\"}]\n" +
                "CHỈ TRẢ VỀ JSON THUẦN, KHÔNG DÙNG CODE BLOCK VÀ KHÔNG THÊM LỜI GIẢI THÍCH NGOÀI JSON.",
                subjectName, sessionNumber, sessionCount, chapterTitle, scope, textbookContent, sessionNumber, chapterTitle.replace("\"", "\\\""));
    }

    private List<com.studyagent.model.WeeklyCurriculum> requestCurriculum(String prompt, String subjectName) throws Exception {
        String responseBody = callGeminiApi(buildTextPayload(prompt));
        String responseText = parseGeminiResponseText(responseBody);
        List<com.studyagent.model.WeeklyCurriculum> result = parseWeeklyCurriculumJson(subjectName, responseText);
        if (result.isEmpty()) {
            throw new IllegalStateException("Gemini không trả về JSON bài giảng có thể đọc được.");
        }
        return result;
    }

    public List<com.studyagent.model.WeeklyCurriculum> generateWeeklyCurriculumFromTextbook(
            String subjectName, String textbookContent, int totalSessions) {
        return generateCurriculumForSessions(subjectName, textbookContent, totalSessions);
    }

    public List<com.studyagent.model.QuizQuestion> generateQuizQuestions(
            String subjectName, int weekNumber, String phase, String topicContext) {
        if (!hasApiKey()) {
            return generateMockQuiz(subjectName, weekNumber, phase);
        }

        String phaseDesc = phase.equalsIgnoreCase("START_SESSION")
                ? "Bắt đầu ca học: Hãy tạo 3 câu hỏi trắc nghiệm ôn tập nhanh các kiến thức cốt lõi của tuần trước/buổi trước để khởi động."
                : (phase.equalsIgnoreCase("CONTEST")
                ? "Mini Contest kiểm tra: Hãy tạo 5 câu hỏi trắc nghiệm từ cơ bản đến nâng cao bao quát chủ đề này kèm giải thích cặn kẽ."
                : "Cuối ca học: Hãy tạo 3 câu hỏi trắc nghiệm kiểm tra và củng cố ngay kiến thức của bài học vừa học hôm nay.");

        String prompt = String.format("Bạn là trợ lý khảo thí giáo dục. Môn học: \"%s\". Tuần học số: %d.\n" +
                "Chủ đề trọng tâm: \"%s\".\n" +
                "Yêu cầu: %s\n" +
                "Mỗi câu hỏi có đúng 4 lựa chọn A, B, C, D, chỉ có 1 đáp án đúng duy nhất, có phần giải thích đáp án chi tiết.\n" +
                "Trả về JSON Array:\n" +
                "[\n" +
                "  {\n" +
                "    \"question\": \"Nội dung câu hỏi?\",\n" +
                "    \"options\": [\"A. ...\", \"B. ...\", \"C. ...\", \"D. ...\"],\n" +
                "    \"correctAnswer\": \"A\",\n" +
                "    \"explanation\": \"Giải thích vì sao chọn đáp án này\"\n" +
                "  }\n" +
                "]\n" +
                "CHỈ TRẢ VỀ JSON ARRAY HỢP LỆ.", subjectName, weekNumber, topicContext, phaseDesc);

        try {
            String jsonPayload = buildTextPayload(prompt);
            String responseBody = callGeminiApi(jsonPayload);
            String extractedText = parseGeminiResponseText(responseBody);
            List<com.studyagent.model.QuizQuestion> list = parseQuizQuestionsJson(subjectName, weekNumber, phase, extractedText);
            if (list != null && !list.isEmpty()) return list;
        } catch (Exception e) {
            System.err.println("⚠️ [GeminiService] Lỗi tạo câu hỏi ôn tập: " + e.getMessage());
        }
        return generateMockQuiz(subjectName, weekNumber, phase);
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
        if (s.contains("```json")) {
            int start = s.indexOf("```json") + 7;
            int end = s.indexOf("```", start);
            if (end > start) {
                return s.substring(start, end).trim();
            }
        } else if (s.contains("```")) {
            int start = s.indexOf("```") + 3;
            int end = s.indexOf("```", start);
            if (end > start) {
                return s.substring(start, end).trim();
            }
        }
        int firstBracket = s.indexOf('[');
        int lastBracket = s.lastIndexOf(']');
        if (firstBracket >= 0 && lastBracket > firstBracket) {
            return s.substring(firstBracket, lastBracket + 1).trim();
        }
        int firstBrace = s.indexOf('{');
        int lastBrace = s.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return s.substring(firstBrace, lastBrace + 1).trim();
        }
        return s.trim();
    }

    private List<TimetableEntry> parseTimetableTextFallback(String input) {
        List<TimetableEntry> entries = new ArrayList<>();
        String normalized = input.replace("\n", " ").replace("\r", " ").replace("\t", " ");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return entries;
        }

        Pattern dayLabelPattern = Pattern.compile("(?i)(?:thứ|thu)\\s*(\\d+)\\s*:");
        Matcher dayLabelMatcher = dayLabelPattern.matcher(normalized);

        List<Integer> dayStarts = new ArrayList<>();
        List<Integer> dayEnds = new ArrayList<>();
        List<String> dayNames = new ArrayList<>();
        while (dayLabelMatcher.find()) {
            dayStarts.add(dayLabelMatcher.start());
            dayEnds.add(dayLabelMatcher.end());
            dayNames.add("Thứ " + dayLabelMatcher.group(1).trim());
        }

        if (dayStarts.isEmpty()) {
            String[] dayChunks = normalized.split("(?i)\\s*;\\s*");
            for (String chunk : dayChunks) {
                String trimmedChunk = chunk.trim();
                if (trimmedChunk.isEmpty()) {
                    continue;
                }
                parseSessionsFromChunk(trimmedChunk, "Thứ 2", entries);
            }
            return entries;
        }

        for (int i = 0; i < dayStarts.size(); i++) {
            int blockStart = dayEnds.get(i);
            int blockEnd = (i + 1 < dayStarts.size()) ? dayStarts.get(i + 1) : normalized.length();
            String chunk = normalized.substring(blockStart, blockEnd).trim();
            if (!chunk.isEmpty()) {
                parseSessionsFromChunk(chunk, dayNames.get(i), entries);
            }
        }

        return entries;
    }

    private void parseSessionsFromChunk(String chunk, String dayOfWeek, List<TimetableEntry> entries) {
        Pattern sessionPattern = Pattern.compile("(?i)(?:sáng|chiều|tối|buổi)?\\s*(\\d{1,2})\\s*h\\s*(?:-|đến|to)\\s*(\\d{1,2})\\s*h\\s*([^,;]+)");
        Matcher sessionMatcher = sessionPattern.matcher(chunk);

        while (sessionMatcher.find()) {
            String startText = sessionMatcher.group(1).trim();
            String endText = sessionMatcher.group(2).trim();
            String subject = sessionMatcher.group(3).trim();
            subject = subject.replaceAll("(?i)^(?:sáng|chiều|tối|buổi)\\s*", "").trim();
            subject = subject.replaceAll("(?i)(?:phòng.*$|room.*$|p\\..*$)", "").trim();
            subject = subject.replaceAll("[;,]$", "").trim();
            if (subject.isEmpty()) {
                subject = "Chưa rõ môn";
            }

            TimetableEntry entry = new TimetableEntry();
            entry.setDayOfWeek(dayOfWeek);
            entry.setStartTime(String.format("%02d:00", Integer.parseInt(startText)));
            entry.setEndTime(String.format("%02d:00", Integer.parseInt(endText)));
            entry.setSubjectName(capitalizeSubject(subject));
            entry.setRoom("N/A");
            entry.setNote("");
            entries.add(entry);
        }
    }

    private String normalizeHour(String hourText) {
        int hour = Integer.parseInt(hourText.trim());
        return String.format("%02d:00", Math.min(Math.max(hour, 0), 23));
    }

    private String capitalizeSubject(String subject) {
        if (subject == null) {
            return "Chưa rõ môn";
        }
        String text = subject.trim();
        if (text.isEmpty()) {
            return "Chưa rõ môn";
        }
        text = text.replaceAll("\\s+", " ");
        String[] words = text.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (word.equalsIgnoreCase("và") || word.equalsIgnoreCase("của") || word.equalsIgnoreCase("với")) {
                result.append(word.toLowerCase()).append(" ");
            } else {
                result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase()).append(" ");
            }
        }
        return result.toString().trim();
    }

    public List<TimetableEntry> parseTimetablePdfFallback(byte[] pdfBytes, String extractedText) {
        if (extractedText == null || extractedText.isBlank()) {
            return new ArrayList<>();
        }

        // Thử parse theo dạng bảng đào tạo đại học (PTIT / QLĐT)
        List<TimetableEntry> tableEntries = parseUniversityTableText(extractedText);
        if (!tableEntries.isEmpty()) {
            return tableEntries;
        }

        // Nếu không trúng bảng, thử bộ fallback dạng text thông thường
        return parseTimetableTextFallback(extractedText);
    }

    public List<TimetableEntry> parseUniversityTableText(String text) {
        if (text == null || text.isBlank()) return new ArrayList<>();
        Map<String, TimetableEntry> entryMap = new LinkedHashMap<>();

        String[] lines = text.split("\\r?\\n");
        Pattern rowPattern = Pattern.compile(
                "(?i)(?:^|\\s)(?:([A-Z0-9]{4,10})\\s+)?([\\p{L}0-9\\s()+-]+?)\\s+(?:(\\d{1,3}(?:-\\d{1,3})?)\\s+)?(?:(\\d{1,2})\\s+)?(?:([A-Za-z0-9_,-]+(?:\\s*,\\s*[A-Za-z0-9_,-]+)*)\\s+)?(CN|[2-7])\\s+(\\d{1,2})\\s+(\\d{1,2})\\s+([A-Za-z0-9_.-]+)(?:\\s+([^\\d\\n]+?))?(?:\\s+(\\d{1,2}/\\d{1,2}/\\d{2,4}\\s*(?:đến|-)\\s*\\d{1,2}/\\d{1,2}/\\d{2,4}))?(?:\\s|$)"
        );

        Pattern flexPattern = Pattern.compile(
                "(?i)\\b(CN|[2-7])\\s+(\\d{1,2})\\s+(\\d{1,2})\\s+([A-Za-z0-9_.-]{2,15})\\b"
        );

        String currentSubject = "";
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("Mã MH") || trimmed.startsWith("Thời khóa biểu")
                    || trimmed.startsWith("Cổng thông tin") || trimmed.startsWith("Học kỳ")
                    || trimmed.startsWith("Họ tên:") || trimmed.startsWith("Lớp:")) {
                continue;
            }

            Matcher m = rowPattern.matcher(trimmed);
            if (m.find()) {
                String sub = m.group(2) != null ? m.group(2).trim() : "";
                sub = sub.replaceAll("(?i)^(?:BAS|INT|IT|PHY|MATH|SKD|MUL)\\d+\\s*", "").trim();
                if (sub.length() >= 3 && !sub.matches(".*\\b(?i)(?:nhóm|tín chỉ|tiết|phòng|giảng viên)\\b.*")) {
                    currentSubject = sub;
                }

                String dayRaw = m.group(6);
                int startPeriod = Integer.parseInt(m.group(7));
                int numPeriods = Integer.parseInt(m.group(8));
                String room = m.group(9);
                String lecturer = m.group(10) != null ? m.group(10).trim() : "";
                String dates = m.group(11) != null ? m.group(11).trim() : "";

                String dayOfWeek = normalizeDayOfWeek(dayRaw);
                String[] times = convertPeriodsToTime(startPeriod, numPeriods);

                String finalSubject = !currentSubject.isEmpty() ? currentSubject : "Môn học";
                String note = "";
                if (!lecturer.isEmpty()) note += "GV: " + lecturer;
                if (!dates.isEmpty()) note += (note.isEmpty() ? "" : " | ") + dates;

                String key = dayOfWeek + "|" + times[0] + "|" + times[1] + "|" + finalSubject.toLowerCase()
                        + "|" + dates.toLowerCase();
                if (!entryMap.containsKey(key)) {
                    entryMap.put(key, new TimetableEntry(dayOfWeek, times[0], times[1], capitalizeSubject(finalSubject), room, note));
                }
            } else {
                Matcher flexM = flexPattern.matcher(trimmed);
                if (flexM.find()) {
                    String dayRaw = flexM.group(1);
                    int startPeriod = Integer.parseInt(flexM.group(2));
                    int numPeriods = Integer.parseInt(flexM.group(3));
                    String room = flexM.group(4);

                    String beforeMatch = trimmed.substring(0, flexM.start()).trim();
                    String possibleSubject = beforeMatch
                            .replaceAll("^[A-Z0-9]{4,10}\\s+", "")
                            .replaceAll("\\s+\\d{1,3}(?:-\\d{1,3})?\\s+\\d+\\s+.*$", "")
                            .trim();
                    if (!possibleSubject.isEmpty() && possibleSubject.length() >= 3
                            && !possibleSubject.matches(".*\\b(?i)(?:nhóm|tín chỉ|tiết|phòng|giảng viên)\\b.*")) {
                        currentSubject = possibleSubject;
                    }

                    String dayOfWeek = normalizeDayOfWeek(dayRaw);
                    String[] times = convertPeriodsToTime(startPeriod, numPeriods);
                    String finalSubject = !currentSubject.isEmpty() ? currentSubject : "Môn học";

                    String key = dayOfWeek + "|" + times[0] + "|" + times[1] + "|" + finalSubject.toLowerCase()
                            + "|" + room.toLowerCase();
                    if (!entryMap.containsKey(key)) {
                        entryMap.put(key, new TimetableEntry(dayOfWeek, times[0], times[1], capitalizeSubject(finalSubject), room, ""));
                    }
                }
            }
        }
        return new ArrayList<>(entryMap.values());
    }

    public static String normalizeDayOfWeek(String dayStr) {
        if (dayStr == null) return "Thứ 2";
        String s = dayStr.trim().toLowerCase();
        if (s.equals("cn") || s.contains("chủ nhật") || s.contains("chu nhat")) return "Chủ Nhật";
        if (s.equals("2") || s.contains("thứ 2") || s.contains("thu 2") || s.equals("t2")) return "Thứ 2";
        if (s.equals("3") || s.contains("thứ 3") || s.contains("thu 3") || s.equals("t3")) return "Thứ 3";
        if (s.equals("4") || s.contains("thứ 4") || s.contains("thu 4") || s.equals("t4")) return "Thứ 4";
        if (s.equals("5") || s.contains("thứ 5") || s.contains("thu 5") || s.equals("t5")) return "Thứ 5";
        if (s.equals("6") || s.contains("thứ 6") || s.contains("thu 6") || s.equals("t6")) return "Thứ 6";
        if (s.equals("7") || s.contains("thứ 7") || s.contains("thu 7") || s.equals("t7")) return "Thứ 7";
        return "Thứ 2";
    }

    public static String[] convertPeriodsToTime(int startPeriod, int numPeriods) {
        String startTime;
        switch (startPeriod) {
            case 1: startTime = "07:00"; break;
            case 2: startTime = "08:00"; break;
            case 3: startTime = "09:00"; break;
            case 4: startTime = "10:00"; break;
            case 5: startTime = "11:00"; break;
            case 6: startTime = "12:30"; break;
            case 7: startTime = "13:30"; break;
            case 8: startTime = "14:30"; break;
            case 9: startTime = "15:30"; break;
            case 10: startTime = "16:30"; break;
            case 11: startTime = "17:30"; break;
            case 12: startTime = "18:00"; break;
            case 13: startTime = "18:30"; break;
            case 14: startTime = "19:30"; break;
            case 18: startTime = "18:00"; break;
            default:
                if (startPeriod >= 15) startTime = "19:00";
                else startTime = String.format("%02d:00", Math.min(23, Math.max(7, startPeriod + 6)));
                break;
        }

        int endPeriod = startPeriod + Math.max(1, numPeriods) - 1;
        String endTime;
        switch (endPeriod) {
            case 1: endTime = "08:00"; break;
            case 2: endTime = "09:00"; break;
            case 3: endTime = "10:00"; break;
            case 4: endTime = "11:00"; break;
            case 5: endTime = "12:00"; break;
            case 6: endTime = "13:30"; break;
            case 7: endTime = "14:30"; break;
            case 8: endTime = "15:30"; break;
            case 9: endTime = "16:30"; break;
            case 10: endTime = "17:30"; break;
            case 11: endTime = "18:30"; break;
            case 12: endTime = "19:00"; break;
            case 13: endTime = "19:30"; break;
            case 14: endTime = "20:30"; break;
            case 15: endTime = "21:00"; break;
            case 16: endTime = "21:30"; break;
            case 18: endTime = "19:00"; break;
            default:
                if (endPeriod >= 17) endTime = "21:30";
                else endTime = String.format("%02d:00", Math.min(23, Math.max(8, endPeriod + 7)));
                break;
        }

        return new String[]{startTime, endTime};
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

    private static boolean containsDateRange(String note) {
        if (note == null || note.isBlank()) return false;
        return Pattern.compile("\\d{1,2}/\\d{1,2}/\\d{2,4}\\s*(?:đến|-)\\s*\\d{1,2}/\\d{1,2}/\\d{2,4}",
                Pattern.CASE_INSENSITIVE).matcher(note).find();
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

    private List<com.studyagent.model.WeeklyCurriculum> parseWeeklyCurriculumJson(String subjectName, String jsonText) {
        List<com.studyagent.model.WeeklyCurriculum> list = new ArrayList<>();
        String cleaned = cleanJsonString(jsonText);
        if (cleaned.isEmpty()) return list;

        try {
            JsonElement rootEl = JsonParser.parseString(cleaned);
            if (rootEl.isJsonArray()) {
                JsonArray array = rootEl.getAsJsonArray();
                for (JsonElement el : array) {
                    if (el.isJsonObject()) {
                        list.add(mapWeeklyCurriculumObject(subjectName, el.getAsJsonObject(), list.size() + 1));
                    }
                }
            } else if (rootEl.isJsonObject()) {
                JsonObject obj = rootEl.getAsJsonObject();
                String[] arrayKeys = {"curriculum", "lessons", "weeklyCurriculum", "plans", "data", "sessions", "topics", "outline"};
                boolean foundArray = false;
                for (String key : arrayKeys) {
                    if (obj.has(key) && obj.get(key).isJsonArray()) {
                        for (JsonElement el : obj.getAsJsonArray(key)) {
                            if (el.isJsonObject()) {
                                list.add(mapWeeklyCurriculumObject(subjectName, el.getAsJsonObject(), list.size() + 1));
                            }
                        }
                        foundArray = true;
                        break;
                    }
                }
                if (!foundArray) {
                    list.add(mapWeeklyCurriculumObject(subjectName, obj, 1));
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi parse WeeklyCurriculum JSON: " + e.getMessage());
        }
        return list;
    }

    private com.studyagent.model.WeeklyCurriculum mapWeeklyCurriculumObject(String subjectName, JsonObject obj, int defaultWeek) {
        com.studyagent.model.WeeklyCurriculum c = new com.studyagent.model.WeeklyCurriculum();
        c.setSubjectName(subjectName);

        int week = defaultWeek;
        if (obj.has("weekNumber") && !obj.get("weekNumber").isJsonNull()) {
            try { week = obj.get("weekNumber").getAsInt(); } catch (Exception ignored) {}
        } else if (obj.has("week") && !obj.get("week").isJsonNull()) {
            try { week = obj.get("week").getAsInt(); } catch (Exception ignored) {}
        } else if (obj.has("sessionNumber") && !obj.get("sessionNumber").isJsonNull()) {
            try { week = obj.get("sessionNumber").getAsInt(); } catch (Exception ignored) {}
        }
        c.setWeekNumber(week);

        String title = "Chương " + week;
        if (obj.has("chapterTitle") && !obj.get("chapterTitle").isJsonNull()) {
            title = obj.get("chapterTitle").getAsString();
        } else if (obj.has("title") && !obj.get("title").isJsonNull()) {
            title = obj.get("title").getAsString();
        } else if (obj.has("chapter") && !obj.get("chapter").isJsonNull()) {
            title = obj.get("chapter").getAsString();
        } else if (obj.has("name") && !obj.get("name").isJsonNull()) {
            title = obj.get("name").getAsString();
        }
        c.setChapterTitle(title);

        String detail = "Nội dung ôn tập theo tiến độ.";
        if (obj.has("detailedSyllabus") && !obj.get("detailedSyllabus").isJsonNull()) {
            detail = obj.get("detailedSyllabus").getAsString();
        } else if (obj.has("content") && !obj.get("content").isJsonNull()) {
            detail = obj.get("content").getAsString();
        } else if (obj.has("syllabus") && !obj.get("syllabus").isJsonNull()) {
            detail = obj.get("syllabus").getAsString();
        } else if (obj.has("description") && !obj.get("description").isJsonNull()) {
            detail = obj.get("description").getAsString();
        }
        c.setDetailedSyllabus(detail);

        return c;
    }

    public List<com.studyagent.model.WeeklyCurriculum> generateMockWeeklyCurriculum(String subjectName, int totalWeeks) {
        List<com.studyagent.model.WeeklyCurriculum> list = new ArrayList<>();
        int chapters = Math.max(1, totalWeeks / 2); // Ví dụ 10 tuần -> 5 chương
        for (int w = 1; w <= totalWeeks; w++) {
            int chapterIndex = (w - 1) / 2 + 1;
            String part = (w % 2 != 0) ? "Nửa đầu (Lý thuyết & Khái niệm nền tảng)" : "Nửa sau (Phương pháp & Bài tập áp dụng)";
            String title = String.format("Chương %d (%s)", chapterIndex, part);
            String detail = String.format("Nắm vững kiến thức trọng tâm của %s môn %s; giải bài tập mẫu và chuẩn bị câu hỏi thảo luận.", title, subjectName);
            list.add(createFallbackLessonForSession(subjectName, w, title, detail));
        }
        return list;
    }

    private List<com.studyagent.model.QuizQuestion> parseQuizQuestionsJson(String subjectName, int weekNumber, String phase, String jsonText) {
        List<com.studyagent.model.QuizQuestion> list = new ArrayList<>();
        String cleaned = cleanJsonString(jsonText);
        try {
            JsonArray array = gson.fromJson(cleaned, JsonArray.class);
            for (JsonElement el : array) {
                JsonObject obj = el.getAsJsonObject();
                com.studyagent.model.QuizQuestion q = new com.studyagent.model.QuizQuestion();
                q.setSubjectName(subjectName);
                q.setWeekNumber(weekNumber);
                q.setPhase(phase);
                q.setQuestion(obj.has("question") ? obj.get("question").getAsString() : "Câu hỏi ôn tập?");
                List<String> options = new ArrayList<>();
                if (obj.has("options")) {
                    for (JsonElement opt : obj.getAsJsonArray("options")) {
                        options.add(opt.getAsString());
                    }
                }
                q.setOptions(options);
                q.setCorrectAnswer(obj.has("correctAnswer") ? obj.get("correctAnswer").getAsString() : "A");
                q.setExplanation(obj.has("explanation") ? obj.get("explanation").getAsString() : "Đọc lại bài giảng để nắm chắc kiến thức.");
                list.add(q);
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi parse QuizQuestions JSON: " + e.getMessage());
        }
        return list;
    }

    public List<com.studyagent.model.QuizQuestion> generateMockQuiz(String subjectName, int weekNumber, String phase) {
        List<com.studyagent.model.QuizQuestion> list = new ArrayList<>();
        if (phase.equalsIgnoreCase("START_SESSION")) {
            list.add(new com.studyagent.model.QuizQuestion(
                    String.format("[ĐẦU CA - Ôn bài cũ] Trong môn %s (tuần %d), khái niệm cốt lõi nào đã học ở buổi trước?", subjectName, Math.max(1, weekNumber - 1)),
                    List.of("A. Định nghĩa và kiến thức nền tảng", "B. Cấu trúc dữ liệu chưa học", "C. Thư viện ngoài lề", "D. Không có khái niệm nào"),
                    "A", "Đáp án A: Cần nắm chắc định nghĩa và kiến thức nền tảng của buổi trước."
            ));
            list.add(new com.studyagent.model.QuizQuestion(
                    String.format("[ĐẦU CA - Ôn bài cũ] Mục tiêu chính cần chuẩn bị trước khi vào ca học %s là gì?", subjectName),
                    List.of("A. Rà soát bài tập về nhà và các thắc mắc", "B. Đợi giảng viên giảng xong mới đọc", "C. Bỏ qua lý thuyết", "D. Chỉ học khi sắp thi"),
                    "A", "Đáp án A: Rà soát bài tập giúp kết nối kiến thức cũ và mới."
            ));
        } else if (phase.equalsIgnoreCase("CONTEST")) {
            for (int i = 1; i <= 5; i++) {
                list.add(new com.studyagent.model.QuizQuestion(
                        String.format("[MINI CONTEST #%d] Câu hỏi kiểm tra chuyên sâu môn %s (Tuần %d) - Mức độ %d?", i, subjectName, weekNumber, i),
                        List.of("A. Phương án chuẩn xác A", "B. Phương án gây nhiễu B", "C. Phương án gây nhiễu C", "D. Phương án sai D"),
                        "A", "Đáp án đúng là A theo định lý và nguyên lý chuẩn mực trong giáo trình."
                ));
            }
        } else {
            // END_SESSION
            list.add(new com.studyagent.model.QuizQuestion(
                    String.format("[CUỐI CA - Củng cố bài mới] Kết luận quan trọng nhất vừa học trong ca học %s hôm nay là gì?", subjectName),
                    List.of("A. Nắm vững phương pháp và cách giải quyết bài toán của bài hôm nay", "B. Ghi nhớ hình ảnh minh họa", "C. Chỉ cần chép code mẫu", "D. Không cần ghi chép"),
                    "A", "Đáp án A: Hiểu bản chất phương pháp là mục tiêu quan trọng nhất."
            ));
            list.add(new com.studyagent.model.QuizQuestion(
                    String.format("[CUỐI CA - Củng cố bài mới] Bước tiếp theo sau khi kết thúc buổi học %s là gì?", subjectName),
                    List.of("A. Làm bài tập tự luyện và đọc trước phần của tuần sau", "B. Quên bài học ngay", "C. Chờ đến tuần thi mới xem", "D. Không làm bài tập"),
                    "A", "Đáp án A: Làm bài tập tự luyện giúp khắc sâu kiến thức vào trí nhớ dài hạn."
            ));
        }
        for (com.studyagent.model.QuizQuestion q : list) {
            q.setSubjectName(subjectName);
            q.setWeekNumber(weekNumber);
            q.setPhase(phase);
        }
        return list;
    }
}
