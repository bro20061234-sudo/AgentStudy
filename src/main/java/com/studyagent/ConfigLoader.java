package com.studyagent;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {
    private static final String CONFIG_FILE = "config.properties";
    private static Properties properties = new Properties();

    static {
        loadConfig();
    }

    private static void loadConfig() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (InputStream in = new FileInputStream(file)) {
                properties.load(in);
            } catch (Exception e) {
                System.err.println("⚠️ Không thể đọc file " + CONFIG_FILE + ": " + e.getMessage());
            }
        }
    }

    public static String getTelegramBotToken() {
        String env = System.getenv("TELEGRAM_BOT_TOKEN");
        if (env != null && !env.isBlank()) return env.trim();
        return properties.getProperty("telegram.bot.token", "YOUR_TELEGRAM_BOT_TOKEN").trim();
    }

    public static String getGeminiApiKey() {
        String env = System.getenv("GEMINI_API_KEY");
        if (env != null && !env.isBlank()) return env.trim();
        return properties.getProperty("gemini.api.key", "YOUR_GEMINI_API_KEY").trim();
    }
}
