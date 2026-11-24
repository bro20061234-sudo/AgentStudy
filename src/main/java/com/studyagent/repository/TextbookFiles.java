package com.studyagent.repository;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class TextbookFiles {
    private TextbookFiles() {}
    public static Path archive(Path source) throws IOException { return archive(source, com.studyagent.AppPaths.resolve("data", "textbooks")); }
    public static Path archive(Path source, Path directory) throws IOException {
        String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
        String extension = name.endsWith(".pdf") ? ".pdf" : name.endsWith(".docx") ? ".docx" : null;
        if (extension == null) throw new IOException("Chỉ hỗ trợ file PDF hoặc DOCX.");
        Path root = directory.toAbsolutePath().normalize(); Files.createDirectories(root);
        Path target = root.resolve(UUID.randomUUID() + extension);
        Files.copy(source, target); return target;
    }
}
