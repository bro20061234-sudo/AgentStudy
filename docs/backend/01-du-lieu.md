# Người 1 — Khởi động, cấu hình và lưu trữ

## Phạm vi

Giải thích ứng dụng lấy cấu hình ở đâu, khởi tạo các thành phần thế nào, lưu dữ liệu gì và làm sao giữ dữ liệu giữa các lần mở. SQLite là cơ sở dữ liệu cục bộ; ứng dụng hiện không có một máy chủ backend HTTP riêng cho desktop.

## File và thứ tự đọc

| Thứ tự | File | Chú thích cần nắm |
|---|---|---|
| 1 | [DesktopLauncher.java](../../src/main/java/com/studyagent/DesktopLauncher.java) | Điểm vào EXE; khởi tạo DB, AI, repository, optimizer và cửa sổ; có chế độ smoke test. |
| 2 | [AppPaths.java](../../src/main/java/com/studyagent/AppPaths.java) | Xác định thư mục gốc ứng dụng, tránh phụ thuộc thư mục hiện tại khi mở EXE. |
| 3 | [ConfigLoader.java](../../src/main/java/com/studyagent/ConfigLoader.java) | Đọc cấu hình API key và bot token; chỉ giải thích tên cấu hình, không chiếu giá trị thật. |
| 4 | [DatabaseManager.java](../../src/main/java/com/studyagent/repository/DatabaseManager.java) | Mở kết nối, tạo bảng và bổ sung cột cho dữ liệu cũ; đọc schema tại đây trước khi đọc SQL ở repository. |
| 5 | [StudyRepository.java](../../src/main/java/com/studyagent/repository/StudyRepository.java) | CRUD thời khóa biểu, ca tự học, giáo trình, chương và đề cương; lưu trạng thái bắt đầu/hoàn thành. Chú ý `chatId` phân vùng dữ liệu và việc giữ tiến độ khi sinh lại lịch. |
| 6 | [TextbookFiles.java](../../src/main/java/com/studyagent/repository/TextbookFiles.java) | Sao chép file giáo trình gốc vào vùng dữ liệu quản lý để mở lại tài liệu. |
| 7 | [ReviewRepository.java](../../src/main/java/com/studyagent/repository/ReviewRepository.java) | Đọc cách lưu ngân hàng câu hỏi, trạng thái bài và lịch sử; nghiệp vụ chấm/lọc do người 4 trình bày. |
| 8 | [FocusTimeRepository.java](../../src/main/java/com/studyagent/repository/FocusTimeRepository.java) | Lưu số giây tập trung theo ngày và truy vấn thống kê hôm nay/tổng cộng. |

Các model đọc kèm: [TimetableEntry](../../src/main/java/com/studyagent/model/TimetableEntry.java), [StudySession](../../src/main/java/com/studyagent/model/StudySession.java), [WeeklyCurriculum](../../src/main/java/com/studyagent/model/WeeklyCurriculum.java), [SubjectSyllabus](../../src/main/java/com/studyagent/model/SubjectSyllabus.java). Model chủ yếu biểu diễn dữ liệu, không thay thế tầng lưu trữ.

## Luồng cần giải thích

Khởi động → xác định đường dẫn → tạo/nâng cấp bảng → repository đọc dữ liệu → giao diện hiển thị. Khi có thay đổi: nghiệp vụ gọi repository → SQL ghi SQLite → đọc lại để làm mới màn hình.

Phân biệt `study_agent.db` với thư mục `data/textbooks`: DB lưu nội dung/trạng thái/đường dẫn, thư mục giáo trình giữ file gốc. `data/google` thuộc phần 5, không phải dữ liệu cần sửa bằng SQL.

## Điểm nối và kiểm thử

- Người 2 gọi `saveTextbook`, `prepareChapters`, lưu đề cương; người 3 lưu các ca sinh ra; người 4 lưu đáp án/kết quả; người 5 đọc snapshot để đồng bộ.
- Đọc [AppPathsTest](../../src/test/java/com/studyagent/AppPathsTest.java) và [SessionProgressPersistenceTest](../../src/test/java/com/studyagent/SessionProgressPersistenceTest.java).
- Khi demo: mở lại ứng dụng để chứng minh dữ liệu được lưu; chỉ dùng DB thử nghiệm nếu cần sửa/xóa dữ liệu để minh họa.

## Câu hỏi tự kiểm tra

1. Vì sao mở EXE từ thư mục khác vẫn tìm đúng DB?
2. Sinh lại lịch có làm mất mọi tiến độ không? Đọc điều kiện ghép ca cũ/mới trước khi trả lời.
3. Vì sao không nên chỉ sao chép EXE mà bỏ `app`, `runtime` và dữ liệu đi kèm?
