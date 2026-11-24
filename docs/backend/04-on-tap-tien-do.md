# Người 4 — Ôn tập, đề thi, tiến độ và Pomodoro

## Phạm vi

Theo dõi từ chương đang học → câu hỏi → làm bài → chấm và lưu lịch sử; giải thích trạng thái ca học và số giây tập trung. Mảng này có nghiệp vụ nằm cả trong model, repository và các panel UI nên phải đọc theo luồng.

## File và thứ tự đọc

| Thứ tự | File | Chú thích cần nắm |
|---|---|---|
| 1 | [QuizQuestion.java](../../src/main/java/com/studyagent/model/QuizQuestion.java) | Cấu trúc câu hỏi, lựa chọn, đáp án và giải thích. |
| 2 | [QuizLogic.java](../../src/main/java/com/studyagent/model/QuizLogic.java) | Lọc câu cần ôn, lấy tập con, chọn đề ngẫu nhiên/toàn bộ và khởi tạo câu chưa trả lời. |
| 3 | [ReviewRepository.java](../../src/main/java/com/studyagent/repository/ReviewRepository.java) | Lưu bộ câu hỏi theo bài, ngân hàng theo môn và lịch sử; đọc `bank`, `questionId`, `validate`, `score` cùng các hàm lưu/đọc trạng thái. |
| 4 | [LessonReviewPanel.java](../../src/main/java/com/studyagent/ui/LessonReviewPanel.java) | Điều phối bài ôn theo chương: tải bộ đã lưu, tạo thêm, lọc toàn bộ hoặc sai/chưa làm, nộp bài. Đây là nơi có cả logic giao diện và nghiệp vụ. |
| 5 | [ExamPanel.java](../../src/main/java/com/studyagent/ui/ExamPanel.java) | Chọn môn, lấy ngân hàng, chọn số câu/ngẫu nhiên/toàn bộ và lưu lần thi. |
| 6 | [QuestionPlayer.java](../../src/main/java/com/studyagent/ui/QuestionPlayer.java), [QuizResults.java](../../src/main/java/com/studyagent/ui/QuizResults.java) | Lưu lựa chọn khi chuyển câu; xem đáp án từng câu trong luyện tập; hiển thị kết quả/giải thích sau nộp kể cả câu bỏ trống. |
| 7 | [StudyProgress.java](../../src/main/java/com/studyagent/model/StudyProgress.java) | Tính trạng thái hoàn thành, chưa tới lịch, tới giờ, đang học, quá hạn và thống kê ca đến hạn. Không dựa vào một timer phải luôn chạy khi máy bật. |
| 8 | [FocusTimeTracker.java](../../src/main/java/com/studyagent/model/FocusTimeTracker.java) | Tính thời gian tập trung thực tế, dừng tính khi tạm dừng/nghỉ và đẩy số giây xuống lưu trữ. |
| 9 | [FocusTimeRepository.java](../../src/main/java/com/studyagent/repository/FocusTimeRepository.java) | Cộng số giây theo ngày và thống kê; phối hợp người 1 về lưu trữ. |

## Hai loại tiến độ phải phân biệt

**Tiến độ ca học:** trong [StudyDesktopUI.java](../../src/main/java/com/studyagent/ui/StudyDesktopUI.java), đọc `startStudyEarly`, `completeActiveStudy`, `toggleSessionStatus`, các lời gọi `markStudySessionsStarted`/`completeStudySessions`, `FocusTimeTracker` và các thao tác Pomodoro. Một nhóm ca hiển thị có thể chứa nhiều ID nguồn.

**Kết quả câu hỏi:** lưu lựa chọn và điểm làm bài. Không mặc định một câu đúng đồng nghĩa mọi ca học của môn đều hoàn thành. Câu chưa làm thuộc nhóm cần ôn; khi nộp vẫn phải có đáp án/giải thích cho câu đó.

## Điểm nối

Người 2 cung cấp nội dung chương và phương thức tạo câu hỏi trong `GeminiService`; người 4 quyết định dùng nguồn nào, lưu/lọc/làm bài thế nào. Người 5 nhận thay đổi bắt đầu/hoàn thành để cập nhật nhắc học.

`pomodoro/PomodoroManager` và `PomodoroSession` là bộ chia task của luồng cũ; timer desktop hiện tại cần theo dõi từ `StudyDesktopUI` và `FocusTimeTracker`.

## Kiểm thử và câu hỏi

Đọc [QuestionBankTest](../../src/test/java/com/studyagent/QuestionBankTest.java), [LessonReviewTest](../../src/test/java/com/studyagent/LessonReviewTest.java), [QuizWorkflowTest](../../src/test/java/com/studyagent/ui/QuizWorkflowTest.java), [QuestionPreviewTest](../../src/test/java/com/studyagent/ui/QuestionPreviewTest.java), [StudyProgressTest](../../src/test/java/com/studyagent/StudyProgressTest.java), [FocusTimeTest](../../src/test/java/com/studyagent/FocusTimeTest.java), [StudyAttendanceWorkflowTest](../../src/test/java/com/studyagent/ui/StudyAttendanceWorkflowTest.java).

Chuẩn bị demo: bỏ trống một câu rồi nộp; mở lại bộ câu đã lưu; hoàn thành ca trước lịch; tạm dừng đồng hồ và kiểm tra thời gian không tăng trong thời gian nghỉ.
