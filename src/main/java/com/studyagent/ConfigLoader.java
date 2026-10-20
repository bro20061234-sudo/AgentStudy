package com.studyagent;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ConfigLoader {
    private static final String CONFIG_FILE = "config.properties";
    private static final Properties properties = new Properties();

    static {
        loadConfig();
    }

    private static void loadConfig() {
        Path configPath = findConfigFile();
        if (configPath == null) {
            System.err.println("⚠️ Không tìm thấy " + CONFIG_FILE
                    + " trong thư mục chạy hoặc thư mục ứng dụng.");
            return;
        }

        try (InputStream in = Files.newInputStream(configPath)) {
            properties.load(in);
            System.out.println("🔍 Đã nạp cấu hình từ " + configPath);
        } catch (IOException e) {
            System.err.println("⚠️ Không thể đọc file " + configPath + ": " + e.getMessage());
        }
    }

    private static Path findConfigFile() {
        Path workingDirectoryConfig = Path.of("").toAbsolutePath().normalize().resolve(CONFIG_FILE);
        if (Files.isRegularFile(workingDirectoryConfig)) {
            return workingDirectoryConfig;
        }

        try {
            Path codeLocation = Path.of(ConfigLoader.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            Path directory = Files.isDirectory(codeLocation) ? codeLocation : codeLocation.getParent();
            while (directory != null) {
                Path candidate = directory.resolve(CONFIG_FILE);
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
                directory = directory.getParent();
            }
        } catch (URISyntaxException e) {
            System.err.println("⚠️ Không thể xác định thư mục ứng dụng để tìm " + CONFIG_FILE + ": "
                    + e.getMessage());
        }
        return null;
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
