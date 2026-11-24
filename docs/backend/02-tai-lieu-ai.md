# Người 2 — Đọc tài liệu và AI

## Phạm vi

Giải thích hai luồng riêng: nhập thời khóa biểu để có tiết học có cấu trúc; nhập giáo trình để có chương và đề cương. Không phải mọi bước đều gọi Gemini.

## File và thứ tự đọc

| Thứ tự | File | Chú thích cần nắm |
|---|---|---|
| 1 | [DocumentParser.java](../../src/main/java/com/studyagent/parser/DocumentParser.java) | PDFBox trích chữ PDF trên máy, đánh dấu trang; Apache POI đọc Word. PDF scan không có lớp chữ là giới hạn của luồng giáo trình hiện tại. |
| 2 | [TimetableFiles.java](../../src/main/java/com/studyagent/parser/TimetableFiles.java) | Nhận danh sách PDF/Word/ảnh, chuyển tới cách đọc thời khóa biểu phù hợp. |
| 3 | [TextbookChapters.java](../../src/main/java/com/studyagent/parser/TextbookChapters.java) | Nhận diện tiêu đề chương/bài bằng quy tắc, lọc mục lục và các câu nhắc tới chương; tách vùng nội dung thật. |
| 4 | [LessonSources.java](../../src/main/java/com/studyagent/parser/LessonSources.java) | Lấy mẫu nguồn theo giới hạn độ dài và thêm tham chiếu; không phải luôn gửi nguyên văn toàn bộ sách cho AI. |
| 5 | [GeminiService.java](../../src/main/java/com/studyagent/ai/GeminiService.java) | Prompt và xử lý kết quả: đọc lịch, `discoverChapters`, `generateChapterOutlines`, `generateChapterOutline`, tạo câu hỏi. Phân biệt nhận diện cấu trúc với biên soạn nội dung. |
| 6 | [GeminiTransport.java](../../src/main/java/com/studyagent/ai/GeminiTransport.java) | Tầng vận chuyển HTTP tới Gemini; xem xử lý lỗi và giới hạn chờ tại đây. |
| 7 | [AiJson.java](../../src/main/java/com/studyagent/ai/AiJson.java) | Hỗ trợ đọc dữ liệu JSON từ phản hồi AI. |
| 8 | [TextbookProcessor.java](../../src/main/java/com/studyagent/ai/TextbookProcessor.java) | Điều phối: thử nhận diện cục bộ → AI khi chưa có chương → soạn nhóm 1–2 chương → lưu từng kết quả; tiếp tục phần thiếu sau lỗi; khóa tránh chạy trùng cùng môn. |

## Luồng giáo trình

File → trích chữ theo trang → lưu giáo trình/file gốc → nhận diện chương bằng quy tắc → nếu chưa có chương thì Gemini nhận diện → Gemini soạn từng nhóm → lưu SQLite → đọc lại đề cương đã lưu.

Chương độc lập với ca tự học: không chia sách thành 19 phần chỉ vì lịch có 19 ca. Một chương có thể học qua nhiều ca. Mở đề cương đã lưu không cần sinh lại bằng API.

## Điểm nối giao diện cần đọc

- [StudyDesktopUI.java](../../src/main/java/com/studyagent/ui/StudyDesktopUI.java): tìm `chooseImages`, `importTimetableFiles`, `chooseTextbook`, `importTextbookFile`; đây là điểm gọi nghiệp vụ và worker nền.
- [FileDropHandler.java](../../src/main/java/com/studyagent/ui/FileDropHandler.java): kiểm tra file kéo thả; dùng chung luồng nhập với nút chọn file, không có bộ phân tích riêng.
- [SyllabusPanel.java](../../src/main/java/com/studyagent/ui/SyllabusPanel.java) và [LessonHtml.java](../../src/main/java/com/studyagent/ui/LessonHtml.java): xem cách mở lại nguồn/hiển thị đề cương có định dạng.

## Kiểm thử và giới hạn cần trình bày

Đọc [TextbookProcessorTest](../../src/test/java/com/studyagent/TextbookProcessorTest.java), [ChapterLearningTest](../../src/test/java/com/studyagent/ChapterLearningTest.java), [LearningContentTest](../../src/test/java/com/studyagent/LearningContentTest.java), [TimetableImportTest](../../src/test/java/com/studyagent/ui/TimetableImportTest.java).

Chuẩn bị trả lời: PDF có chữ khác PDF scan thế nào; vì sao timeout không làm mất chương đã lưu; nguồn bị trích chữ lỗi thì AI có thể tạo nội dung thiếu chính xác ra sao. Không khẳng định đề cương AI luôn đầy đủ hay đúng tuyệt đối.
