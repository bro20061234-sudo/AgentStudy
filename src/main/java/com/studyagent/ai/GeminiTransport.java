package com.studyagent.ai;

import com.google.gson.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Serializes requests; only unavailable models trigger model discovery. */
public final class GeminiTransport {
    public record Response(int status, String body, String retryAfter) {}
    @FunctionalInterface public interface Exchange { Response send(String model, String payload) throws Exception; }
    private final Exchange exchange;
    private final List<String> models;
    private final Set<String> unavailable = new HashSet<>();
    private String preferred;
    private long cooldownUntil;
    private String quotaMessage;
    public GeminiTransport(Exchange exchange, List<String> models) {
        this.exchange = exchange; this.models = List.copyOf(models);
    }
    public synchronized String send(String payload) throws Exception {
        if (System.currentTimeMillis() < cooldownUntil)
            throw new IllegalStateException(quotaMessage + " Chờ khoảng " + ((cooldownUntil - System.currentTimeMillis()) / 1000 + 1) + " giây trước khi thử lại. Bài đã lưu vẫn dùng được.");
        List<String> order = new ArrayList<>(models);
        if (preferred != null) { order.remove(preferred); order.add(0, preferred); }
        for (String model : order) {
            if (unavailable.contains(model)) continue;
            for (int attempt = 0; attempt < 2; attempt++) {
                Response response;
                try { response = exchange.send(model, payload); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw e; }
                if (response.status() == 200) { preferred = model; return response.body(); }
                if (response.status() == 404) { unavailable.add(model); break; }
                if (response.status() == 429) {
                    boolean daily = isDailyQuota(response.body());
                    long delay = retrySeconds(response);
                    cooldownUntil = System.currentTimeMillis() + Math.min(86400, delay) * 1000;
                    quotaMessage = daily ? "Gemini báo hết quota theo ngày. Kiểm tra hạn mức trong Google AI Studio; thử lại liên tục không tăng quota."
                            : "Gemini đang giới hạn tốc độ hoặc token. Hãy chờ theo thời gian Google yêu cầu.";
                    throw new IllegalStateException(quotaMessage + " Tạm ngừng gửi trong " + delay + " giây. Bạn vẫn có thể làm lại các bộ đã lưu.");
                }
                if ((response.status() == 503 || response.status() == 500 || response.status() == 502 || response.status() == 504) && attempt == 0) {
                    Thread.sleep(1000); continue;
                }
                String reason = response.status() == 400 ? "Yêu cầu không hợp lệ hoặc API key sai; kiểm tra cấu hình."
                        : response.status() == 401 || response.status() == 403 ? "API key không hợp lệ hoặc chưa có quyền truy cập model."
                        : "Máy chủ AI chưa xử lý được yêu cầu. Hãy thử lại sau.";
                throw new IllegalStateException("Gemini HTTP " + response.status() + ": " + reason);
            }
        }
        throw new IllegalStateException("Không tìm thấy model khả dụng. Kiểm tra model được cấp quyền trong Google AI Studio; có thể đặt biến môi trường GEMINI_MODEL.");
    }
    public static boolean isDailyQuota(String body) {
        String lower = Objects.toString(body, "").toLowerCase(Locale.ROOT);
        return lower.contains("perday") || lower.contains("per_day") || lower.contains("daily") || lower.contains("per day");
    }
    public static long retrySeconds(Response response) {
        double seconds = 60;
        try {
            if (response.retryAfter() != null && !response.retryAfter().isBlank()) {
                try { seconds = Double.parseDouble(response.retryAfter()); }
                catch (NumberFormatException e) { seconds = Duration.between(Instant.now(), ZonedDateTime.parse(response.retryAfter(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds(); }
            } else {
                JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                for (JsonElement detail : root.getAsJsonObject("error").getAsJsonArray("details")) {
                    JsonObject obj = detail.getAsJsonObject();
                    if (obj.has("retryDelay")) seconds = Double.parseDouble(obj.get("retryDelay").getAsString().replace("s", ""));
                }
            }
        } catch (RuntimeException ignored) { /* Default cooldown if no valid retry hint. */ }
        return Math.max(1, Math.min(86400, (long)Math.ceil(seconds)));
    }
}
