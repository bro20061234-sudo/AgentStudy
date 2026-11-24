# Người 3 — Thuật toán lịch học

## Phạm vi

Nhận tiết học trên lớp, tính nhu cầu tự học, tìm chỗ trống và tạo ca; giải thích gộp lịch. Thuật toán hiện tại chạy cục bộ, không cần Gemini đặt tiêu đề cho từng ca.

## File và thứ tự đọc

| Thứ tự | File | Chú thích cần nắm |
|---|---|---|
| 1 | [TimetableEntry.java](../../src/main/java/com/studyagent/model/TimetableEntry.java) | Dữ liệu đầu vào: môn, thứ, giờ, phòng, ghi chú và số tiết/ngày hiệu lực nếu có. |
| 2 | [StudySession.java](../../src/main/java/com/studyagent/model/StudySession.java) | Ca đầu ra: ngày cụ thể, giờ bắt đầu/kết thúc, môn và tiến độ. |
| 3 | [StudyOptimizer.java](../../src/main/java/com/studyagent/scheduler/StudyOptimizer.java) | Đọc hằng số chính sách rồi `generateOptimalScheduleForWeek`, `generateSemesterSchedule`, các hàm tính nhu cầu và ngày hiệu lực. Đây là file thuật toán chính. |
| 4 | [ScheduleBlocks.java](../../src/main/java/com/studyagent/scheduler/ScheduleBlocks.java) | Gộp các tiết/ca cùng môn gần nhau; giữ danh sách ca nguồn để cập nhật đúng ID khi hoàn thành. |
| 5 | [StudyRepository.java](../../src/main/java/com/studyagent/repository/StudyRepository.java) | Đọc riêng phần lưu/lấy lịch và giữ tiến độ khi lịch được sinh lại; người 1 chịu trách nhiệm SQL. |

## Chính sách hiện tại

- Mỗi tiết trên lớp đủ điều kiện tạo 15 phút tự học.
- Các tiết bắt đầu từ 18:00 không tạo nhu cầu tự học; xem riêng cách tính phần trước 18:00 khi tiết vắt qua mốc này trong code.
- Chỉ xếp tự học 20:00–22:00, tối đa 60 phút cho một môn mỗi ngày.
- Xem các ô trống và tránh giờ bận; lịch không đủ chỗ phải báo lỗi, không âm thầm bỏ nhu cầu.
- Không còn trần 24 tuần; ngày hiệu lực thực tế của môn vẫn cần được tôn trọng.
- Gộp cùng môn gần nhau với khoảng cách tối đa 15 phút theo điều kiện trong `ScheduleBlocks`; không gộp tùy ý các môn khác nhau.

Ví dụ để trình bày: 4 tiết hợp lệ tương ứng 60 phút tự học. Nếu cả 4 tiết đều bắt đầu từ 18:00 thì không phát sinh nhu cầu từ chúng. Không đồng nhất “tiết” với “giờ đồng hồ” khi dữ liệu có số tiết riêng.

## Điểm nối và kiểm thử

- [StudyDesktopUI.java](../../src/main/java/com/studyagent/ui/StudyDesktopUI.java): tìm `runAnalysis`, `generateSemesterSchedule`, `renderWeeklyCalendar` để theo dõi nhập lịch → tạo ca → lưu → hiển thị.
- [WeeklyCalendarPanel.java](../../src/main/java/com/studyagent/ui/WeeklyCalendarPanel.java) là phần vẽ lịch, không phải nơi quyết định nhu cầu học.
- Người 4 nhận các ID ca nguồn để lưu tiến độ; người 5 dùng các nhóm lịch để tạo sự kiện.
- Đọc [StudyPolicyTest](../../src/test/java/com/studyagent/StudyPolicyTest.java), [ScheduleWithoutAiTest](../../src/test/java/com/studyagent/ScheduleWithoutAiTest.java), [CalendarGroupingTest](../../src/test/java/com/studyagent/CalendarGroupingTest.java), [LongScheduleTest](../../src/test/java/com/studyagent/LongScheduleTest.java), [StudyOptimizerTest](../../src/test/java/com/studyagent/StudyOptimizerTest.java).

Chuẩn bị giải thích: tối ưu ở đây là thuật toán theo ràng buộc đang cài đặt, không phải khẳng định tìm nghiệm tối ưu toán học cho mọi tình huống. `Scheduler.java`/`Schedule.java` thuộc luồng task cũ, không thay cho `StudyOptimizer`.
