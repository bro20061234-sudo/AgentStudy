package com.studyagent.ui;

import java.util.*;
import java.util.regex.*;
import javax.swing.*;

/** Small, escaped Markdown renderer: no raw HTML or remote images from generated content. */
public final class LessonHtml {
    private LessonHtml() {}
    public static String escape(String text) { return Objects.toString(text, "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
    private static String inline(String text) {
        return escape(text).replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>").replaceAll("`([^`]+)`", "<code>$1</code>");
    }
    public static String render(String content) {
        StringBuilder html = new StringBuilder(); boolean list = false;
        // Upgrade old single-paragraph plans locally without spending an API request.
        String normalized = Objects.toString(content, "").replaceAll("(?i)(?<!^)(?<!\\n)(Mục tiêu(?: buổi học)?|Kiến thức chính|Bài tập(?: thực hành)?|Tóm tắt|Ví dụ minh họa)\\s*:", "\n\n## $1\n");
        for (String line : normalized.split("\\R")) {
            line = line.trim(); if (line.isEmpty()) continue;
            boolean bullet = line.matches("^(?:[-*•]|\\d+[.)])\\s+.*");
            if (!bullet && list) { html.append("</ul>"); list = false; }
            if (bullet) {
                if (!list) { html.append("<ul>"); list = true; }
                html.append("<li>").append(inline(line.replaceFirst("^(?:[-*•]|\\d+[.)])\\s+", ""))).append("</li>");
            } else if (line.startsWith("#") || isHeading(line)) {
                html.append("<h2>").append(inline(line.replaceFirst("^#{1,6}\\s*", ""))).append("</h2>");
            } else {
                for (String paragraph : line.split("(?<=[.!?])\\s+(?=[\\p{Lu}])")) html.append("<p>").append(inline(paragraph)).append("</p>");
            }
        }
        if (list) html.append("</ul>");
        return html.toString();
    }
    private static boolean isHeading(String line) {
        String plain = line.replace("**", "").replaceFirst(":$", "").toLowerCase(Locale.ROOT);
        return Set.of("mục tiêu", "mục tiêu buổi học", "kiến thức tiên quyết", "kiến thức chính", "nội dung trọng tâm", "bài giảng chi tiết", "ví dụ minh họa", "bài tập", "bài tập thực hành và lời giải", "tóm tắt và ghi nhớ", "tài liệu cần đọc", "tự kiểm tra").contains(plain);
    }
    public static String document(String title, String content) {
        return "<html><head><style>body{font-family:'Segoe UI';font-size:13pt;font-weight:normal;color:"+UiTheme.hex("text")+";background-color:"+UiTheme.hex("card")+";margin:18px;}h1{font-size:21pt;color:"+UiTheme.hex("text")+";}h2{font-size:15pt;color:"+UiTheme.hex("accent")+";margin-top:22px;margin-bottom:10px;}p{margin-top:6px;margin-bottom:12px;}li{margin-bottom:9px;}ul{margin-left:18px;}code{background:"+UiTheme.hex("tint")+";}a{color:"+UiTheme.hex("accent")+";}</style></head><body>"
                + (title == null || title.isBlank() ? "" : "<h1>" + escape(title) + "</h1>") + render(content) + "</body></html>";
    }
    public static JEditorPane pane() {
        JEditorPane pane = new JEditorPane("text/html", ""); pane.setEditable(false);
        pane.setFont(new java.awt.Font("Segoe UI",java.awt.Font.PLAIN,15));UiTheme.role(pane,"card","text");
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        pane.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10)); return pane;
    }
    public static void show(JEditorPane pane,String title,String content){
        pane.putClientProperty("lesson.title",title);pane.putClientProperty("lesson.content",content);pane.putClientProperty("lesson.dark",UiTheme.isDark());pane.setText(document(title,content));
    }
    public static void refreshTheme(JEditorPane pane){
        Object content=pane.getClientProperty("lesson.content");if(content==null)return;
        if(java.util.Objects.equals(pane.getClientProperty("lesson.dark"),UiTheme.isDark()))return;
        pane.putClientProperty("lesson.dark",UiTheme.isDark());int caret=pane.getCaretPosition();pane.setText(document((String)pane.getClientProperty("lesson.title"),content.toString()));pane.setCaretPosition(Math.min(caret,pane.getDocument().getLength()));
    }
    public static JEditorPane block(String content) {
        JEditorPane pane = new JEditorPane("text/html", "") {
            @Override public java.awt.Dimension getPreferredSize() {
                int width = getParent() == null || getParent().getWidth() == 0 ? 550 : Math.max(200,getParent().getWidth()-44);
                setSize(width, Short.MAX_VALUE); var size=super.getPreferredSize(); return new java.awt.Dimension(width,size.height);
            }
            @Override public java.awt.Dimension getMaximumSize() { return new java.awt.Dimension(Integer.MAX_VALUE,getPreferredSize().height); }
        };
        pane.setEditable(false);pane.setFont(new java.awt.Font("Segoe UI",java.awt.Font.PLAIN,15));pane.setOpaque(false);show(pane,"",content);pane.setAlignmentX(0);return pane;
    }
}
