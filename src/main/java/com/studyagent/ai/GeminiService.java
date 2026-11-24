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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GeminiService {
    private final String apiKey;
    private final HttpClient httpClient;
    private final Gson gson;
    private final GeminiTransport transport;
    private volatile String lastCurriculumWarning;
    public String getLastCurriculumWarning() { return lastCurriculumWarning; }
    /**
     * Thứ tự model đã kiểm tra với key AQ. (2026):
     * - 2.5/2.0: 404 not available to new users
     * - 3.8-flash: hay 503 high demand
     * - 3.5 / 3.6 / flash-lite: generateContent OK
     */
    private static final String[] MODEL_CANDIDATES = {
            "gemini-3.5-flash-lite",
            "gemini-3.5-flash",
            "gemini-flash-lite-latest",
            "gemini-3.6-flash",
            "gemini-3.8-flash",
            "gemini-flash-latest",
            "gemini-3.7-flash"
    };

    public GeminiService(String apiKey) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        this.gson = new Gson();
        String configuredModel = System.getenv("GEMINI_MODEL");
        List<String> models = configuredModel == null || configuredModel.isBlank()
                ? java.util.Arrays.asList(MODEL_CANDIDATES) : List.of(configuredModel.trim());
        this.transport = new GeminiTransport((model, payload) -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent"))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("x-goog-api-key", this.apiKey).timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(payload, java.nio.charset.StandardCharsets.UTF_8)).build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return new GeminiTransport.Response(response.statusCode(), response.body(), response.headers().firstValue("Retry-After").orElse(null));
        }, models);
    }

    public boolean hasApiKey() {
        return !apiKey.isEmpty() && !apiKey.equalsIgnoreCase("YOUR_GEMINI_API_KEY");
    }

    /**
     * Gợi ý cấu hình key. Từ 2026 Google AI Studio cấp key dạng AQ.... (auth key) — đó là chuẩn mới, không phải lỗi.
     */
    public String apiKeyHint() {
        if (!hasApiKey()) {
            return "Chưa cấu hình gemini.api.key trong config.properties "
                    + "(lấy tại https://aistudio.google.com/apikey — key mới thường bắt đầu bằng AQ.).";
        }
        return null;
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
        if (imageBytesList == null || imageBytesList.isEmpty()) {
            return new ArrayList<>();
        }
        if (!hasApiKey()) {
            throw new IllegalStateException(
                    "Chưa cấu hình Gemini API key. Mở config.properties, điền gemini.api.key "
                            + "(lấy tại https://aistudio.google.com/apikey, thường bắt đầu bằng AIza...).");
        }
        String keyHint = apiKeyHint();
        if (keyHint != null) {
            System.err.println("⚠️ [GeminiService] " + keyHint);
        }

        String prompt = "Bạn là trợ lý đọc thời khóa biểu đại học (PTIT/HUST/...).\n"
                + "Ảnh là LƯỚI lịch: cột = các thứ trong tuần (Thứ 2…Chủ nhật), hàng = khung giờ.\n"
                + "Mỗi ô có thể có: tên môn, mã môn (INT...), nhóm, phòng, GV, giờ dạng 07:00 → 08:50.\n"
                + "Hãy đọc TẤT CẢ ô có môn học (bỏ ô trống).\n"
                + "Quy tắc:\n"
                + "- dayOfWeek: 'Thứ 2'..'Thứ 7' hoặc 'Chủ Nhật' theo cột của ô.\n"
                + "- startTime/endTime: HH:mm (ví dụ 07:00, 08:50) từ dòng giờ trong ô; nếu chỉ có mũi tên → đổi thành start/end.\n"
                + "- subjectName: tên môn đầy đủ, có thể kèm mã trong ngoặc.\n"
                + "- room: phòng (vd 405-A2).\n"
                + "- note: nhóm + giảng viên nếu có.\n"
                + "Trả về ĐÚNG một JSON Array, không markdown, không giải thích:\n"
                + "[{\"dayOfWeek\":\"Thứ 2\",\"startTime\":\"07:00\",\"endTime\":\"08:50\","
                + "\"subjectName\":\"Lập trình hướng đối tượng\",\"room\":\"405-A2\",\"note\":\"Nhóm 05 | GV: ...\"}]\n"
                + "Không bỏ sót buổi; không trùng cùng môn-thứ-giờ.";

        try {
            String jsonPayload = buildMultiImagesPayload(prompt, imageBytesList, mimeType);
            String responseBody = callGeminiApi(jsonPayload);
            String extractedText = parseGeminiResponseText(responseBody);
            if (extractedText == null || extractedText.isBlank()) {
                throw new IllegalStateException(
                        "Gemini không trả về nội dung (response rỗng). "
                                + (keyHint != null ? keyHint : "Thử ảnh đầy đủ hơn hoặc model khác."));
            }
            List<TimetableEntry> entries = parseTimetableJson(extractedText);
            if (entries.isEmpty()) {
                throw new IllegalStateException(
                        "Gemini có phản hồi nhưng không parse được JSON lịch.\n"
                                + "Đoạn trả về (rút gọn): "
                                + extractedText.substring(0, Math.min(280, extractedText.length())));
            }
            return entries;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("⚠️ [GeminiService] Lỗi Vision Gemini đa ảnh: " + e.getMessage());
            String msg = e.getMessage() != null ? e.getMessage() : e.toString();
            if (keyHint != null) {
                msg = msg + "\n\n" + keyHint;
            }
            throw new IllegalStateException("Không đọc được ảnh TKB: " + msg, e);
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
                "   - Tiết 6: 12:30 (kíp 4 tiết -> 12:30 - 16:30)\n" +
                "   - Tiết 7: 13:30 (kíp 1 tiết -> 13:30 - 14:30, 2 tiết -> 13:30 - 15:30)\n" +
                "   - Tiết 8: 14:30\n" +
                "   - Tiết 10: 16:30 (kíp 2 tiết -> 16:30 - 18:30)\n" +
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
        return transport.send(jsonPayload);
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

    /** Discover structure only when local headings cannot be recognized; all anchors must exist. */
    public List<com.studyagent.model.WeeklyCurriculum> discoverChapters(String subject, String book) {
        if (!hasApiKey()) throw new IllegalStateException("Chưa cấu hình Gemini API key để phân tích cấu trúc tài liệu.");
        StringBuilder index=new StringBuilder();
        for(String page:book.split("(?m)(?=^\\[Trang PDF \\d+\\])")) {
            index.append(page.lines().filter(s->!s.isBlank()).limit(12).collect(java.util.stream.Collectors.joining("\n"))).append('\n');
        }
        String prompt="Nhận diện các chương/bài/phần chính của giáo trình "+subject+" từ các dòng đầu trang bên dưới. Không chia theo ca học. "
                +"Bỏ mục lục, lời giới thiệu, tiêu đề lặp và đề mục con. Trả JSON Array [{\"number\":1,\"title\":\"Tên mục\",\"heading\":\"Dòng tiêu đề NGUYÊN VĂN ở phần thân tài liệu\",\"startPage\":12}]. "
                +"heading phải là dòng có thật, không tự viết lại. startPage dùng số [Trang PDF n]; nếu không có số trang, dùng 0. Chỉ JSON.\n"
                +com.studyagent.parser.LessonSources.sample(index.toString(),30000);
        try {
            JsonObject payload=gson.fromJson(buildTextPayload(prompt),JsonObject.class);
            JsonObject config=new JsonObject();config.addProperty("responseMimeType","application/json");
            config.add("responseSchema",com.google.gson.JsonParser.parseString("{\"type\":\"ARRAY\",\"items\":{\"type\":\"OBJECT\",\"properties\":{\"number\":{\"type\":\"INTEGER\"},\"title\":{\"type\":\"STRING\"},\"heading\":{\"type\":\"STRING\"},\"startPage\":{\"type\":\"INTEGER\"}},\"required\":[\"title\",\"heading\",\"startPage\"]}}"));
            payload.add("generationConfig",config);
            JsonArray array=AiJson.array(cleanJsonString(parseGeminiResponseText(callGeminiApi(gson.toJson(payload)))));
            if(array==null||array.isEmpty()||array.size()>60)throw new IllegalStateException("AI chưa nhận diện được danh sách chương đáng tin cậy.");
            List<com.studyagent.model.WeeklyCurriculum> plans=new ArrayList<>();
            List<Integer> offsets=new ArrayList<>();List<String> titles=new ArrayList<>();
            int previous=-1;
            for(JsonElement element:array) {
                JsonObject item=element.getAsJsonObject();String heading=item.get("heading").getAsString().trim();
                if(heading.length()<3)throw new IllegalStateException("Tiêu đề AI trả về không hợp lệ.");
                int from=previous+1;
                int page=item.has("startPage")?item.get("startPage").getAsInt():0;
                if(page>0) {
                    var marker=java.util.regex.Pattern.compile("(?m)^\\[Trang PDF "+page+"\\]\\s*$").matcher(book);
                    if(!marker.find())throw new IllegalStateException("AI dẫn số trang không có trong tài liệu.");from=Math.max(from,marker.end());
                }
                int offset=book.indexOf(heading,from);
                if(offset<0)throw new IllegalStateException("Không đối chiếu được tiêu đề AI với tài liệu gốc.");
                if(page>0) {var next=com.studyagent.parser.LessonSources.PAGE.matcher(book);if(next.find(from)&&offset>=next.start())throw new IllegalStateException("Tiêu đề AI không nằm ở trang được dẫn.");}
                offsets.add(offset);titles.add(item.get("title").getAsString());previous=offset;
            }
            for(int i=0;i<offsets.size();i++) {
                String source=book.substring(offsets.get(i),i+1<offsets.size()?offsets.get(i+1):book.length());
                var pages=com.studyagent.parser.LessonSources.PAGE.matcher(book);String marker="";
                while(pages.find()&&pages.start()<=offsets.get(i))marker=pages.group();
                plans.add(com.studyagent.parser.TextbookChapters.plan(subject,i+1,titles.get(i),marker+"\n"+source,false));
            }
            return plans;
        }catch(Exception e){throw new IllegalStateException("Chưa phân tích được cấu trúc giáo trình: "+e.getMessage(),e);}
    }

    /** Small batches keep import automatic without a giant request for the whole book. */
    public Map<Integer,String> generateChapterOutlines(String subject, List<com.studyagent.model.WeeklyCurriculum> chapters) {
        if(chapters.isEmpty()||chapters.size()>2)throw new IllegalArgumentException("Mỗi nhóm gồm 1–2 mục.");
        if(!hasApiKey())throw new IllegalStateException("Chưa cấu hình Gemini API key.");
        StringBuilder sources=new StringBuilder();
        for(var chapter:chapters)sources.append("\nID ").append(chapter.getWeekNumber()).append(" — ").append(chapter.getChapterTitle()).append("\n")
                .append(com.studyagent.parser.LessonSources.sample(chapter.getSourceText(),7000)).append('\n');
        String prompt="Tự động soạn đề cương tiếng Việt cho từng mục giáo trình "+subject+" dưới đây. Giữ đúng ID và phạm vi nguồn, không chia theo lịch. "
                +"Mỗi đề cương khoảng 200–300 từ, Markdown gồm ## Mục tiêu, ## Kiến thức chính (giải thích thuật ngữ và các ý quan trọng), ## Ví dụ minh họa, ## Bài tập, ## Tự kiểm tra. "
                +"Dùng gạch đầu dòng - và **từ khóa**; bám nguồn, không bịa kiến thức hoặc số trang; chỗ thiếu thông tin nói rõ cần đối chiếu sách. "
                +"Giữ đúng mốc sự kiện: phân biệt chủ trương, quyết định và việc hoàn thành; không gán kết quả của sự kiện khác vào một ngày hoặc hội nghị. "
                +"Trả JSON Array, mỗi mục {\"id\":1,\"outline\":\"## Mục tiêu\\n- ...\"}. Phải trả đủ từng ID, không code fence.\n"+sources;
        try {
            JsonObject payload=gson.fromJson(buildTextPayload(prompt),JsonObject.class);JsonObject settings=new JsonObject();settings.addProperty("maxOutputTokens",6144);payload.add("generationConfig",settings);
            settings.addProperty("responseMimeType","application/json");
            settings.add("responseSchema",com.google.gson.JsonParser.parseString("{\"type\":\"ARRAY\",\"items\":{\"type\":\"OBJECT\",\"properties\":{\"id\":{\"type\":\"INTEGER\"},\"outline\":{\"type\":\"STRING\"}},\"required\":[\"id\",\"outline\"]}}"));
            JsonArray array=AiJson.array(cleanJsonString(parseGeminiResponseText(callGeminiApi(gson.toJson(payload)))));
            Map<Integer,String> result=new LinkedHashMap<>();
            if(array!=null)for(JsonElement e:array){var item=e.getAsJsonObject();int id=item.get("id").getAsInt();String body=item.get("outline").getAsString();
                var chapter=chapters.stream().filter(p->p.getWeekNumber()==id).findFirst().orElse(null);
                if(chapter!=null&&body.length()>=180&&body.contains("##"))result.put(id,com.studyagent.parser.LessonSources.withReference(body,chapter.getSourceText()));
            }
            if(result.isEmpty())throw new IllegalStateException("AI chưa trả đề cương hợp lệ.");return result;
        }catch(Exception e){throw new IllegalStateException("Tạm dừng soạn đề cương: "+e.getMessage()+". Các mục đã xong vẫn được lưu.",e);}
    }

    /** One bounded request for the selected chapter; callers save only after success. */
    public String generateChapterOutline(String subjectName, com.studyagent.model.WeeklyCurriculum chapter) {
        if (!hasApiKey()) throw new IllegalStateException("Chưa cấu hình Gemini API key.");
        if (!chapter.isChapter()) throw new IllegalArgumentException("Chương chưa có nguồn giáo trình. Hãy nhận diện chương trước.");
        String source = com.studyagent.parser.LessonSources.sample(chapter.getSourceText(), 14000);
        String prompt = "Soạn đề cương tự học tiếng Việt cho MỘT chương môn " + subjectName + ": " + chapter.getChapterTitle()
                + ". Không chia theo ngày hoặc số ca học. Khoảng 250–400 từ, Markdown rõ ràng, có tiêu đề ##, danh sách - và **từ khóa**. "
                + "Dùng các mục: Mục tiêu; Kiến thức chính (giải thích ngắn các khái niệm quan trọng); Ví dụ minh họa (cách làm); Bài tập; Tự kiểm tra. "
                + "Chỉ dựa trên nguồn bên dưới. Đây là các trích đoạn của chương, không phải toàn bộ chương: chỗ nguồn không đủ phải nói rõ cần đối chiếu sách. "
                + "Không tự ghi số trang, hệ thống sẽ gắn số trang thật. Chỉ trả Markdown, không JSON và không code fence.\nNGUỒN:\n" + source;
        try {
            JsonObject payload = gson.fromJson(buildTextPayload(prompt), JsonObject.class);
            JsonObject generation = new JsonObject(); generation.addProperty("maxOutputTokens", 4096);
            payload.add("generationConfig", generation);
            String body=parseGeminiResponseText(callGeminiApi(gson.toJson(payload))).trim();
            if (body.startsWith("```")) body=body.replaceFirst("^```(?:markdown|md)?\\s*", "").replaceFirst("\\s*```$", "");
            if (body.length()<180 || !body.contains("##")) throw new IllegalStateException("AI trả đề cương chưa đầy đủ. Nội dung cũ được giữ lại.");
            return com.studyagent.parser.LessonSources.withReference(body, chapter.getSourceText());
        } catch(java.net.http.HttpTimeoutException e) {
            throw new IllegalStateException("AI quá thời gian chờ khi soạn chương này. Danh sách chương và đề cương đã lưu vẫn giữ nguyên. Hãy kiểm tra kết nối hoặc thử lại sau.",e);
        } catch(Exception e) {throw new IllegalStateException("Chưa soạn được chương này: "+e.getMessage()+". Bạn có thể thử lại; danh sách chương và nội dung đã lưu vẫn giữ nguyên.",e);}
    }

    public List<com.studyagent.model.WeeklyCurriculum> generateCurriculumForSessions(
            String subjectName, String textbookContent, int sessionCount) {
        lastCurriculumWarning = null;
        if (sessionCount <= 0) throw new IllegalArgumentException("Số buổi học thêm phải lớn hơn 0.");
        if (textbookContent == null || textbookContent.isBlank()) throw new IllegalArgumentException("Không có văn bản giáo trình để phân tích.");
        if (!hasApiKey()) {
            lastCurriculumWarning = "Chưa cấu hình Gemini API key.";
            return createFallbackCurriculum(subjectName, textbookContent, sessionCount);
        }
        try {
            StringBuilder sources = new StringBuilder();
            int budget = Math.max(240, Math.min(4500, 36000 / sessionCount));
            for (int i = 1; i <= sessionCount; i++) {
                String source = com.studyagent.parser.LessonSources.section(textbookContent, i, sessionCount);
                sources.append("\nBUỔI ").append(i).append(" — ").append(com.studyagent.parser.LessonSources.reference(source))
                        .append("\n").append(com.studyagent.parser.LessonSources.sample(source, budget)).append('\n');
            }
            String prompt = "Bạn soạn đề cương tự học môn " + subjectName + " cho đúng " + sessionCount + " buổi. "
                    + "Mỗi buổi chỉ sử dụng phần nguồn được phân bổ bên dưới, không lặp nội dung ngoài phạm vi. "
                    + "Mỗi detailedSyllabus là Markdown tiếng Việt khoảng 100–180 từ, có xuống dòng, tiêu đề ## và gạch đầu dòng -; "
                    + "bôi đậm **thuật ngữ chính**. KHÔNG viết một đoạn văn dài. Dùng đủ các mục: "
                    + "## Mục tiêu buổi học (2–3 ý cụ thể); ## Kiến thức chính (giải thích ngắn các ý, không chỉ liệt kê tên); "
                    + "## Ví dụ minh họa (ví dụ có cách làm); ## Bài tập (2 việc cụ thể); ## Tự kiểm tra (2 câu tự hỏi). "
                    + "Không bịa số trang, không tự viết mục tài liệu cần đọc vì hệ thống sẽ gắn trang từ nguồn. "
                    + "Nếu trích nguồn thiếu nội dung, ghi rõ cần đối chiếu sách. "
                    + "Trả JSON Array đúng " + sessionCount + " phần tử, weekNumber từ 1 đến " + sessionCount
                    + ": [{\"weekNumber\":1,\"chapterTitle\":\"Tên chủ đề\",\"detailedSyllabus\":\"## Mục tiêu buổi học\\n- ...\"}]. Chỉ JSON.\n" + sources;
            var generated = requestCurriculum(prompt, subjectName);
            Map<Integer, com.studyagent.model.WeeklyCurriculum> byNumber = new java.util.HashMap<>();
            for (var plan : generated) byNumber.putIfAbsent(plan.getWeekNumber(), plan);
            var fallback = createFallbackCurriculum(subjectName, textbookContent, sessionCount);
            List<com.studyagent.model.WeeklyCurriculum> result = new ArrayList<>();
            for (int i = 1; i <= sessionCount; i++) {
                var plan = byNumber.get(i);
                if (plan == null || plan.getDetailedSyllabus() == null || plan.getDetailedSyllabus().isBlank()) plan = fallback.get(i-1);
                else plan.setDetailedSyllabus(com.studyagent.parser.LessonSources.withReference(plan.getDetailedSyllabus(),
                        com.studyagent.parser.LessonSources.section(textbookContent, i, sessionCount)));
                result.add(plan);
            }
            return result;
        } catch (Exception e) {
            System.err.println("Không tạo được đề cương AI: " + e.getMessage());
            lastCurriculumWarning = e.getMessage();
            return createFallbackCurriculum(subjectName, textbookContent, sessionCount);
        }
    }

    private static String expandShortLesson(String subject, int sessionNumber, String title, String body) {
        return String.format(
                "Mục tiêu buổi học\n- Nắm được nội dung: %s (buổi %d môn %s).\n\n" +
                "Kiến thức tiên quyết\n- Đã đọc phần trước trong giáo trình (nếu có).\n\n" +
                "Bài giảng chi tiết\n%s\n\n" +
                "Ví dụ minh họa\n- Tự tìm 1–2 ví dụ trong giáo trình liên quan chủ đề trên.\n\n" +
                "Bài tập thực hành và lời giải\n- Làm các bài tập cuối chương tương ứng trong giáo trình.\n\n" +
                "Tóm tắt và ghi nhớ\n- Ghi lại 3–5 ý chính của chủ đề \"%s\".",
                title, sessionNumber, subject, body, title);
    }

    /**
     * Đề cương dự phòng: tách theo "Chương I/II/..." trong giáo trình, chia đều theo số buổi.
     */
    public List<com.studyagent.model.WeeklyCurriculum> createFallbackCurriculum(
            String subjectName, String textbookContent, int sessionCount) {
        if (sessionCount <= 0) throw new IllegalArgumentException("Số buổi phải lớn hơn 0.");
        List<com.studyagent.model.WeeklyCurriculum> result = new ArrayList<>();
        for (int i = 1; i <= sessionCount; i++) {
            String source = com.studyagent.parser.LessonSources.section(textbookContent, i, sessionCount);
            List<String> titles = extractChapterTitles(source);
            String title = titles.isEmpty() ? "Phần đọc " + i : titles.get(0);
            String excerpt = com.studyagent.parser.LessonSources.sample(source.replaceAll("(?m)^\\[Trang PDF \\d+\\]\\s*$", ""), 1800).trim();
            String body = "## Bản phân bổ cục bộ\n- Chưa có bài giảng do AI biên soạn. Nội dung dưới đây là trích đoạn từ phần tài liệu được chia cho buổi này.\n\n"
                    + "## Mục tiêu buổi học\n- Đọc phần tài liệu được phân bổ; ghi lại khái niệm và ý chính.\n\n"
                    + "## Trích nội dung giáo trình\n" + excerpt
                    + "\n\n## Bài tập\n- Viết lại 3 ý chính bằng lời của bạn.\n- Làm một ví dụ hoặc bài tập thuộc phần vừa đọc.\n\n"
                    + "## Tự kiểm tra\n- Bạn giải thích được khái niệm chính và đưa ra một ví dụ không?";
            var plan = new com.studyagent.model.WeeklyCurriculum();
            plan.setSubjectName(subjectName); plan.setWeekNumber(i); plan.setChapterTitle(title);
            plan.setDetailedSyllabus(com.studyagent.parser.LessonSources.withReference(body, source)); result.add(plan);
        }
        return result;
    }

    private static List<String> extractChapterTitles(String text) {
        List<String> titles = new ArrayList<>();
        if (text == null) return titles;
        // Chương I / Chương 1 / CHƯƠNG II: ... (đầu dòng hoặc giữa đoạn như lời nói đầu)
        Pattern p = Pattern.compile(
                "(?i)chương\\s+([ivxlcdm0-9]+)\\s*[:.\\-–]?\\s*([^\\n\\r.]{0,70})");
        Matcher m = p.matcher(text);
        while (m.find() && titles.size() < 20) {
            String num = m.group(1).trim();
            String rest = m.group(2) == null ? "" : m.group(2).trim();
            rest = rest.replaceAll("\\s+", " ");
            if (rest.length() > 50) rest = rest.substring(0, 50) + "…";
            // Bỏ các match quá ngắn / nhiễu
            if (num.isEmpty()) continue;
            String title = rest.isEmpty() ? ("Chương " + num.toUpperCase())
                    : ("Chương " + num.toUpperCase() + ": " + rest);
            boolean dup = false;
            for (String t : titles) {
                if (t.regionMatches(true, 0, "Chương " + num, 0, ("Chương " + num).length())) {
                    dup = true;
                    break;
                }
            }
            if (!dup) titles.add(title);
        }
        return titles;
    }

    private String buildDetailedLessonPrompt(String subjectName, int sessionCount, int sessionNumber,
                                             String chapterTitle, String scope, String textbookContent) {
        return String.format("Bạn là giảng viên đại học môn \"%s\". Hãy viết BÀI GIẢNG TỰ HỌC HOÀN CHỈNH cho buổi %d/%d.\n" +
                "Chủ đề/chương đã được phân bổ: %s\nPhạm vi kiến thức trong buổi: %s\n" +
                "NỘI DUNG GIÁO TRÌNH (nguồn chính, hãy chắt lọc đúng các phần liên quan):\n" +
                "--------------------\n%s\n--------------------\n" +
                "YÊU CẦU:\n" +
                "- Viết khoảng 500-800 từ tiếng Việt, đủ để sinh viên tự học được; đây phải là bài giảng giải thích đầy đủ, không phải bản tóm tắt hay danh sách mục tiêu.\n" +
                "- Bám sát giáo trình. Được bổ sung kiến thức nền, ví dụ thực tế hoặc giải thích để nội dung dễ hiểu hơn; ghi rõ '[Bổ sung]' ở mọi phần không có trực tiếp trong giáo trình. Không bịa hoặc gán nhầm nguồn.\n" +
                "- Trình bày nhiều đoạn ngắn và gạch đầu dòng bắt đầu bằng '- '. Dùng chính xác các tiêu đề sau:\n" +
                "MỤC TIÊU BUỔI HỌC\nKIẾN THỨC TIÊN QUYẾT\nBÀI GIẢNG CHI TIẾT\nVÍ DỤ MINH HỌA\nBÀI TẬP THỰC HÀNH VÀ LỜI GIẢI\nTÓM TẮT VÀ GHI NHỚ\n" +
                "- Giải thích thuật ngữ, nguyên lý, quy trình/công thức từng bước; ví dụ phải có cách làm và kết quả; bài tập phải có đáp án và lời giải chi tiết. " +
                "Nếu giáo trình không có bài tập, tự thêm bài phù hợp và ghi '[Bổ sung]'.\n" +
                "- Không lặp lại bài của các buổi khác; chỉ soạn buổi được chỉ định.\n" +
                "Trả về JSON Array chỉ có một phần tử, đúng cấu trúc {\"weekNumber\": %d, \"chapterTitle\": \"%s\", \"detailedSyllabus\": \"...\"}. " +
                "Trong detailedSyllabus giữ nguyên xuống dòng giữa tiêu đề và gạch đầu dòng. Chỉ JSON hợp lệ, không code fence.",
                subjectName, sessionNumber, sessionCount, chapterTitle, scope, textbookContent, sessionNumber, chapterTitle);
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
        return generateQuizQuestions(subjectName, weekNumber, phase, topicContext, "START_SESSION".equals(phase) ? 3 : 12);
    }

    public List<com.studyagent.model.QuizQuestion> generateQuizQuestions(
            String subjectName, int weekNumber, String phase, String topicContext, int count) {
        if (count < 1 || count > 30) throw new IllegalArgumentException("Mỗi lần tạo từ 1 đến 30 câu hỏi.");
        if (!hasApiKey()) {
            throw new IllegalStateException("Cần cấu hình Gemini API key để tạo câu hỏi bám sát giáo trình.");
        }

        String phaseDesc;
        if (phase.equalsIgnoreCase("START_SESSION")) {
            phaseDesc = "Bắt đầu ca học: Hãy tạo câu hỏi trắc nghiệm ôn tập nhanh các kiến thức cốt lõi của tuần trước/buổi trước để khởi động.";
        } else if (phase.equalsIgnoreCase("CONTEST")) {
            phaseDesc = "Mini Contest kiểm tra: Hãy tạo câu hỏi trắc nghiệm từ cơ bản đến nâng cao bao quát chủ đề này kèm giải thích cặn kẽ.";
        } else if (phase.equalsIgnoreCase("SESSION_REVIEW")) {
            phaseDesc = "Ôn tập buổi học: Hãy tạo câu hỏi trắc nghiệm đa dạng từ nhận biết đến vận dụng, bám sát nội dung đề cương buổi học này để học viên tự luyện tập và củng cố kiến thức.";
        } else {
            phaseDesc = "Cuối ca học: Hãy tạo câu hỏi trắc nghiệm kiểm tra và củng cố ngay kiến thức của bài học vừa học hôm nay.";
        }

        String prompt = String.format("Bạn là trợ lý khảo thí giáo dục. Môn học: \"%s\". Buổi học số: %d.\n" +
                "Nội dung trọng tâm / Đề cương buổi học:\n\"%s\"\n\n" +
                "Yêu cầu: %s\nSố lượng bắt buộc: " + count + " câu hỏi trong MỘT bộ JSON.\n" +
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
            throw new IllegalStateException(e.getMessage(), e);
        }
        throw new IllegalStateException("AI chưa trả về bộ câu hỏi hợp lệ. Chưa lưu câu hỏi nào; hãy thử lại sau.");
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

    private List<TimetableEntry> parseTimetableTextFallback(String input) {
        List<TimetableEntry> entries = new ArrayList<>();
        if (input == null || input.isBlank()) return entries;

        // Ưu tiên parse theo dạng:
        // Thứ 2:
        // - 07:00-09:00 Môn A (phòng)
        // - 09:00-12:00 Môn B
        List<TimetableEntry> structured = parseStructuredDayBulletText(input);
        if (!structured.isEmpty()) {
            return structured;
        }

        // Fallback cũ: gộp 1 dòng, tách "Thứ x:"
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

    /**
     * Parse text dạng mỗi ngày 1 mục, mỗi buổi 1 gạch đầu dòng.
     */
    private List<TimetableEntry> parseStructuredDayBulletText(String input) {
        List<TimetableEntry> entries = new ArrayList<>();
        String currentDay = null;
        Pattern dayLine = Pattern.compile(
                "(?i)^\\s*(thứ\\s*[2-7]|thu\\s*[2-7]|chủ\\s*nhật|cn)\\s*:?\\s*$");
        // - 07:00-09:00 Môn (phòng) — ghi chú
        Pattern bulletLine = Pattern.compile(
                "(?i)^\\s*[-•*]\\s*(\\d{1,2})[:hH.](\\d{2})\\s*[-–—đếnto]+\\s*(\\d{1,2})[:hH.](\\d{2})\\s+(.+)$");
        // Cũng chấp nhận dòng không có gạch: 07:00-09:00 Môn
        Pattern plainTimeLine = Pattern.compile(
                "(?i)^\\s*(\\d{1,2})[:hH.](\\d{2})\\s*[-–—đếnto]+\\s*(\\d{1,2})[:hH.](\\d{2})\\s+(.+)$");

        for (String rawLine : input.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            Matcher dayMatcher = dayLine.matcher(line);
            if (dayMatcher.matches()) {
                currentDay = normalizeDayLabel(dayMatcher.group(1));
                continue;
            }
            // "Thứ 2: - 07:00..." trên cùng dòng
            Matcher dayInline = Pattern.compile(
                    "(?i)^\\s*(thứ\\s*[2-7]|thu\\s*[2-7]|chủ\\s*nhật|cn)\\s*:\\s*(.*)$").matcher(line);
            if (dayInline.matches()) {
                currentDay = normalizeDayLabel(dayInline.group(1));
                String rest = dayInline.group(2).trim();
                if (!rest.isEmpty()) {
                    line = rest;
                } else {
                    continue;
                }
            }

            if (currentDay == null) continue;

            Matcher m = bulletLine.matcher(line);
            if (!m.matches()) {
                m = plainTimeLine.matcher(line);
            }
            if (!m.matches()) {
                // Thử pattern kiểu "7h-9h Môn"
                parseSessionsFromChunk(line, currentDay, entries);
                continue;
            }

            String start = String.format("%02d:%s", Integer.parseInt(m.group(1)), m.group(2));
            String end = String.format("%02d:%s", Integer.parseInt(m.group(3)), m.group(4));
            String rest = m.group(5).trim();
            String room = "N/A";
            String note = "";
            String subject = rest;

            Matcher roomM = Pattern.compile("\\(([^)]+)\\)").matcher(rest);
            if (roomM.find()) {
                room = roomM.group(1).trim();
                subject = rest.substring(0, roomM.start()).trim();
                String after = rest.substring(roomM.end()).trim();
                if (after.startsWith("—") || after.startsWith("-") || after.startsWith("|")) {
                    note = after.replaceFirst("^[—\\-|]+\\s*", "").trim();
                } else if (!after.isEmpty()) {
                    note = after;
                }
            } else if (rest.contains(" — ")) {
                String[] parts = rest.split("\\s+—\\s+", 2);
                subject = parts[0].trim();
                note = parts.length > 1 ? parts[1].trim() : "";
            }

            if (subject.isEmpty()) subject = "Chưa rõ môn";

            TimetableEntry entry = new TimetableEntry();
            entry.setDayOfWeek(currentDay);
            entry.setStartTime(start);
            entry.setEndTime(end);
            entry.setSubjectName(capitalizeSubject(subject));
            entry.setRoom(room);
            entry.setNote(note);
            entries.add(entry);
        }
        return entries;
    }

    private static String normalizeDayLabel(String raw) {
        if (raw == null) return "Thứ 2";
        String s = raw.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ").trim();
        if (s.contains("chủ") || s.equals("cn")) return "Chủ Nhật";
        Matcher m = Pattern.compile("(\\d)").matcher(s);
        if (m.find()) return "Thứ " + m.group(1);
        return "Thứ 2";
    }

    private void parseSessionsFromChunk(String chunk, String dayOfWeek, List<TimetableEntry> entries) {
        // HH:mm-HH:mm Môn
        Pattern colonPattern = Pattern.compile(
                "(?i)(\\d{1,2}):(\\d{2})\\s*[-–—đếnto]+\\s*(\\d{1,2}):(\\d{2})\\s+([^,;]+)");
        Matcher colonMatcher = colonPattern.matcher(chunk);
        boolean found = false;
        while (colonMatcher.find()) {
            found = true;
            String start = String.format("%02d:%s", Integer.parseInt(colonMatcher.group(1)), colonMatcher.group(2));
            String end = String.format("%02d:%s", Integer.parseInt(colonMatcher.group(3)), colonMatcher.group(4));
            String subject = colonMatcher.group(5).trim()
                    .replaceAll("(?i)^(?:sáng|chiều|tối|buổi)\\s*", "")
                    .replaceAll("[;,]$", "").trim();
            if (subject.isEmpty()) subject = "Chưa rõ môn";
            TimetableEntry entry = new TimetableEntry();
            entry.setDayOfWeek(dayOfWeek);
            entry.setStartTime(start);
            entry.setEndTime(end);
            entry.setSubjectName(capitalizeSubject(subject));
            entry.setRoom("N/A");
            entry.setNote("");
            entries.add(entry);
        }
        if (found) return;

        Pattern sessionPattern = Pattern.compile(
                "(?i)(?:sáng|chiều|tối|buổi)?\\s*(\\d{1,2})\\s*h\\s*(?:-|đến|to)\\s*(\\d{1,2})\\s*h\\s*([^,;]+)");
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
        // Cắt lấy đoạn [...] nếu model trả thêm chữ quanh JSON
        int start = cleaned.indexOf('[');
        int end = cleaned.lastIndexOf(']');
        if (start >= 0 && end > start) {
            cleaned = cleaned.substring(start, end + 1);
        }
        try {
            JsonArray array = gson.fromJson(cleaned, JsonArray.class);
            if (array == null) return entries;
            for (JsonElement el : array) {
                if (el == null || !el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                TimetableEntry entry = new TimetableEntry();
                entry.setDayOfWeek(jsonString(obj, "dayOfWeek", "Thứ 2"));
                entry.setStartTime(normalizeTime(jsonString(obj, "startTime", "07:00")));
                entry.setEndTime(normalizeTime(jsonString(obj, "endTime", "09:00")));
                entry.setSubjectName(jsonString(obj, "subjectName", "Chưa rõ môn"));
                entry.setRoom(jsonString(obj, "room", "N/A"));
                entry.setNote(jsonString(obj, "note", ""));
                if (entry.getSubjectName() != null && !entry.getSubjectName().isBlank()) {
                    entries.add(entry);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi parse JSON Timetable: " + e.getMessage());
        }
        return entries;
    }

    private static String jsonString(JsonObject obj, String key, String fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return fallback;
        try {
            String v = obj.get(key).getAsString();
            return v == null || v.isBlank() ? fallback : v.trim();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String normalizeTime(String t) {
        if (t == null) return "07:00";
        String s = t.trim().replace('.', ':').replace('h', ':').replace('H', ':');
        // 7:00 -> 07:00
        Matcher m = Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(s);
        if (m.find()) {
            int h = Integer.parseInt(m.group(1));
            int min = Integer.parseInt(m.group(2));
            return String.format("%02d:%02d", h, min);
        }
        return s;
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
        try {
            JsonArray array = gson.fromJson(cleaned, JsonArray.class);
            for (JsonElement el : array) {
                JsonObject obj = el.getAsJsonObject();
                com.studyagent.model.WeeklyCurriculum c = new com.studyagent.model.WeeklyCurriculum();
                c.setSubjectName(subjectName);
                c.setWeekNumber(obj.has("weekNumber") ? obj.get("weekNumber").getAsInt() : list.size() + 1);
                c.setChapterTitle(obj.has("chapterTitle") ? obj.get("chapterTitle").getAsString() : "Chương " + (list.size() + 1));
                c.setDetailedSyllabus(obj.has("detailedSyllabus") ? obj.get("detailedSyllabus").getAsString() : "Nội dung ôn tập theo tiến độ.");
                list.add(c);
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi parse WeeklyCurriculum JSON: " + e.getMessage());
        }
        return list;
    }

    public List<com.studyagent.model.WeeklyCurriculum> generateMockWeeklyCurriculum(String subjectName, int totalWeeks) {
        List<com.studyagent.model.WeeklyCurriculum> list = new ArrayList<>();
        int chapters = Math.max(1, totalWeeks / 2); // Ví dụ 10 tuần -> 5 chương
        for (int w = 1; w <= totalWeeks; w++) {
            int chapterIndex = (w - 1) / 2 + 1;
            String part = (w % 2 != 0) ? "Nửa đầu (Lý thuyết & Khái niệm nền tảng)" : "Nửa sau (Phương pháp & Bài tập áp dụng)";
            String title = String.format("Chương %d (%s)", chapterIndex, part);
            String detail = String.format("Nắm vững kiến thức trọng tâm của %s môn %s; giải bài tập mẫu và chuẩn bị câu hỏi thảo luận.", title, subjectName);
            list.add(new com.studyagent.model.WeeklyCurriculum(1L, subjectName, w, title, detail));
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
        } else if (phase.equalsIgnoreCase("SESSION_REVIEW")) {
            list.add(new com.studyagent.model.QuizQuestion(
                    String.format("[ÔN TẬP BUỔI %d] Nội dung trọng tâm cốt lõi của môn %s trong buổi học này là gì?", weekNumber, subjectName),
                    List.of("A. Nắm vững lý thuyết cơ bản và vận dụng giải các bài tập thực hành",
                            "B. Chỉ cần học thuộc lòng định nghĩa mà không thực hành",
                            "C. Bỏ qua các ví dụ minh họa trong giáo trình",
                            "D. Không cần làm bài tập vận dụng"),
                    "A", "Đáp án A: Hiểu bản chất lý thuyết kết hợp luyện tập thực hành giúp nắm chắc kiến thức buổi học."
            ));
            list.add(new com.studyagent.model.QuizQuestion(
                    String.format("[ÔN TẬP BUỔI %d] Phương pháp học tập hiệu quả nhất cho kiến thức buổi %d môn %s là gì?", weekNumber, weekNumber, subjectName),
                    List.of("A. Đọc kỹ đề cương buổi học, tự làm bài tập và đối chiếu tài liệu",
                            "B. Đọc lướt qua một lần rồi thôi",
                            "C. Chỉ đọc phần bài tập, bỏ qua phần lý thuyết nền tảng",
                            "D. Chờ đến trước kỳ thi mới mở giáo trình ra đọc"),
                    "A", "Đáp án A: Đọc kỹ đề cương và tự giải bài tập là chìa khóa để ghi nhớ sâu sắc."
            ));
            list.add(new com.studyagent.model.QuizQuestion(
                    String.format("[ÔN TẬP BUỔI %d] Khi gặp bài toán phức tạp trong phần nội dung buổi này, bước đầu tiên nên làm là:", weekNumber),
                    List.of("A. Phân tích đề bài, xác định dữ kiện đã cho và mục tiêu cần giải quyết",
                            "B. Chọn ngẫu nhiên đáp án mà không phân tích",
                            "C. Tìm ngay bài giải sẵn để sao chép",
                            "D. Bỏ qua câu hỏi"),
                    "A", "Đáp án A: Phân tích kỹ yêu cầu và dữ kiện là bước then chốt nhất để tìm ra lời giải chính xác."
            ));
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
