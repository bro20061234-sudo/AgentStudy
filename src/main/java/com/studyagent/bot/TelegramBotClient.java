package com.studyagent.bot;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class TelegramBotClient {
    private final String botToken;
    private final HttpClient httpClient;
    private final Gson gson;
    private boolean isRunning = false;
    private long lastUpdateId = 0;
    private TelegramUpdateListener listener;

    public interface TelegramUpdateListener {
        void onTextMessage(long chatId, String userName, String text);
        void onPhotoMessage(long chatId, String userName, byte[] photoBytes, String caption);
    }

    public TelegramBotClient(String botToken) {
        this.botToken = botToken != null ? botToken.trim() : "";
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        this.gson = new Gson();
    }

    public boolean isConfigured() {
        return !botToken.isEmpty() && !botToken.equalsIgnoreCase("YOUR_TELEGRAM_BOT_TOKEN");
    }

    public void setListener(TelegramUpdateListener listener) {
        this.listener = listener;
    }

    public void startPolling() {
        if (!isConfigured()) {
            System.err.println("⚠️ [TelegramBotClient] Chưa cấu hình TELEGRAM_BOT_TOKEN. Bot không thể khởi động mạng.");
            return;
        }

        isRunning = true;
        Thread pollingThread = new Thread(this::pollLoop, "Telegram-Polling-Thread");
        pollingThread.setDaemon(true);
        pollingThread.start();
        System.out.println("🚀 [TelegramBotClient] Đã khởi chạy Telegram Bot Long Polling thành công!");
    }

    public void stop() {
        isRunning = false;
    }

    private void pollLoop() {
        while (isRunning) {
            try {
                String url = String.format("https://api.telegram.org/bot%s/getUpdates?offset=%d&timeout=25", botToken, lastUpdateId + 1);
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(35)).GET().build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    processUpdates(response.body());
                } else {
                    Thread.sleep(3000);
                }
            } catch (Exception e) {
                if (isRunning) {
                    try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                }
            }
        }
    }

    private void processUpdates(String jsonBody) {
        JsonObject root = gson.fromJson(jsonBody, JsonObject.class);
        if (!root.has("ok") || !root.get("ok").getAsBoolean()) return;

        JsonArray result = root.getAsJsonArray("result");
        for (JsonElement elem : result) {
            JsonObject update = elem.getAsJsonObject();
            long updateId = update.get("update_id").getAsLong();
            if (updateId > lastUpdateId) lastUpdateId = updateId;

            if (update.has("message")) {
                handleSingleMessage(update.getAsJsonObject("message"));
            }
        }
    }

    private void handleSingleMessage(JsonObject message) {
        if (!message.has("chat")) return;
        long chatId = message.getAsJsonObject("chat").get("id").getAsLong();
        String userName = "Bạn";
        if (message.getAsJsonObject("chat").has("first_name")) {
            userName = message.getAsJsonObject("chat").get("first_name").getAsString();
        }

        if (message.has("text")) {
            String text = message.get("text").getAsString();
            if (listener != null) listener.onTextMessage(chatId, userName, text);
        } else if (message.has("photo")) {
            JsonArray photoArray = message.getAsJsonArray("photo");
            if (photoArray.size() > 0) {
                JsonObject bestPhoto = photoArray.get(photoArray.size() - 1).getAsJsonObject();
                String fileId = bestPhoto.get("file_id").getAsString();
                String caption = message.has("caption") ? message.get("caption").getAsString() : "";
                byte[] photoBytes = downloadFile(fileId);
                if (photoBytes != null && listener != null) {
                    listener.onPhotoMessage(chatId, userName, photoBytes, caption);
                }
            }
        }
    }

    public byte[] downloadFile(String fileId) {
        try {
            String getFileUrl = String.format("https://api.telegram.org/bot%s/getFile?file_id=%s", botToken, fileId);
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(getFileUrl)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject obj = gson.fromJson(response.body(), JsonObject.class);
                if (obj.get("ok").getAsBoolean()) {
                    String filePath = obj.getAsJsonObject("result").get("file_path").getAsString();
                    String downloadUrl = String.format("https://api.telegram.org/file/bot%s/%s", botToken, filePath);
                    HttpRequest dlReq = HttpRequest.newBuilder().uri(URI.create(downloadUrl)).GET().build();
                    HttpResponse<byte[]> dlRes = httpClient.send(dlReq, HttpResponse.BodyHandlers.ofByteArray());
                    if (dlRes.statusCode() == 200) return dlRes.body();
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi tải ảnh: " + e.getMessage());
        }
        return null;
    }

    public void sendMessage(long chatId, String messageText) {
        sendMessage(chatId, messageText, "Markdown");
    }

    public void sendMessage(long chatId, String messageText, String parseMode) {
        if (!isConfigured()) {
            System.out.printf("[Simulated Telegram -> Chat %d]:%n%s%n", chatId, messageText);
            return;
        }

        try {
            String url = String.format("https://api.telegram.org/bot%s/sendMessage", botToken);
            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", chatId);
            payload.put("text", messageText);
            if (parseMode != null && !parseMode.isEmpty()) {
                payload.put("parse_mode", parseMode);
            }

            String json = gson.toJson(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 && parseMode != null) {
                payload.remove("parse_mode");
                HttpRequest retryReq = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json; charset=UTF-8")
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                        .build();
                httpClient.send(retryReq, HttpResponse.BodyHandlers.ofString());
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi gửi Telegram: " + e.getMessage());
        }
    }
}
