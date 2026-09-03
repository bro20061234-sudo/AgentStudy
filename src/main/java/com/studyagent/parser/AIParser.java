package com.studyagent.parser;

/**
 * Interface cho các bộ bóc tách ngôn ngữ tự nhiên (Strategy Pattern).
 * @author Thành viên 2 (AI/NLP)
 */
public interface AIParser {
    /**
     * Nhận chuỗi ký tự tự nhiên của người dùng và chuyển thành StudyRequest chuẩn hóa.
     * @param input Câu yêu cầu của người dùng (ví dụ: "Học 2 tiếng làm bài tập OOP trước 20h tối mai")
     * @return Đối tượng StudyRequest chứa các trường thông tin đã trích xuất
     */
    StudyRequest parse(String input);
}
