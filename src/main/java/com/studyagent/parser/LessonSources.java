package com.studyagent.parser;

import java.util.*;
import java.util.regex.*;

/** Deterministic source allocation. Page numbers are physical PDF pages, never invented. */
public final class LessonSources {
    private LessonSources() {}
    public static final Pattern PAGE = Pattern.compile("(?m)^\\[Trang PDF (\\d+)\\]\\s*$");
    public static String section(String text, int lesson, int count) {
        if (count < 1 || lesson < 1 || lesson > count) throw new IllegalArgumentException("Số buổi không hợp lệ");
        text = Objects.toString(text, "");
        List<Integer> starts = new ArrayList<>(); Matcher m = PAGE.matcher(text);
        while (m.find()) starts.add(m.start());
        if (starts.isEmpty()) {
            String[] paragraphs = text.split("\\n\\s*\\n|\\r?\\n");
            int from = Math.min(paragraphs.length - 1, (lesson - 1) * paragraphs.length / count);
            int to = Math.min(paragraphs.length, Math.max(from + 1, lesson * paragraphs.length / count));
            return String.join("\n", Arrays.copyOfRange(paragraphs, from, to));
        }
        int from = Math.min(starts.size() - 1, (lesson - 1) * starts.size() / count);
        int to = Math.min(starts.size(), Math.max(from + 1, lesson * starts.size() / count));
        return text.substring(starts.get(from), to == starts.size() ? text.length() : starts.get(to));
    }
    public static String reference(String source) {
        Matcher m = PAGE.matcher(Objects.toString(source, "")); List<Integer> pages = new ArrayList<>();
        while (m.find()) pages.add(Integer.parseInt(m.group(1)));
        if (pages.isEmpty()) return "Chưa có số trang xác thực. Với Word hoặc giáo trình đã nạp trước đây, hãy đối chiếu theo tên mục; nạp lại PDF để có số trang.";
        return "Trang PDF " + pages.get(0) + (pages.size() > 1 ? "–" + pages.get(pages.size() - 1) : "")
                + " (thứ tự trang trong file, có thể khác số in trên sách).";
    }
    public static String sample(String text, int limit) {
        if (text.length() <= limit) return text;
        // Keep excerpts from beginning, middle and end instead of dropping the rest of the book.
        int part = Math.max(1, (limit - 100) / 3);
        return text.substring(0, part) + "\n[...trích...]\n" + text.substring(text.length()/2, text.length()/2 + part)
                + "\n[...trích...]\n" + text.substring(text.length()-part);
    }
    public static String withReference(String body, String source) {
        return body + "\n\n## Tài liệu cần đọc\n- " + reference(source);
    }
}
