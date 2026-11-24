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
        File file = AppPaths.resolve(CONFIG_FILE).toFile();
        if (file.exists()) {
            try (InputStream in = new FileInputStream(file)) {
                properties.load(in);
            } catch (Exception e) {
                System.err.println("⚠️ Không thể đọc file " + CONFIG_FILE + ": " + e.getMessage());
            }
        }
    }

    public static String getGeminiApiKey() {
        String env = System.getenv("GEMINI_API_KEY");
        if (env != null && !env.isBlank()) return sanitizeKey(env);
        return sanitizeKey(properties.getProperty("gemini.api.key", "YOUR_GEMINI_API_KEY"));
    }

    /** Bỏ BOM, dấu ngoặc, khoảng trắng thừa khi copy key từ AI Studio. */
    private static String sanitizeKey(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        // UTF-8 BOM
        if (!s.isEmpty() && s.charAt(0) == '\uFEFF') {
            s = s.substring(1).trim();
        }
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }
}
