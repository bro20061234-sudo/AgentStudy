# Người 5 — Google Calendar, OAuth và nhắc học

## Phạm vi

Giải thích đăng nhập Google, biến lịch cục bộ thành sự kiện, cập nhật/xóa nhắc thừa và hoạt động khi tắt máy. Google Calendar đảm nhiệm nhắc trên điện thoại sau khi sự kiện được đồng bộ.

## File và thứ tự đọc

| Thứ tự | File | Chú thích cần nắm |
|---|---|---|
| 1 | [CalendarPlanner.java](../../src/main/java/com/studyagent/calendar/CalendarPlanner.java) | Đọc snapshot, tạo sự kiện theo múi giờ Việt Nam, ID ổn định và fingerprint. Quyết định nhắc trước 10 phút và các sự kiện phụ sau 5/10 phút. |
| 2 | [CalendarGateway.java](../../src/main/java/com/studyagent/calendar/CalendarGateway.java) | Hợp đồng thao tác lịch; giúp thay API thật bằng gateway giả trong test. |
| 3 | [CalendarReconciler.java](../../src/main/java/com/studyagent/calendar/CalendarReconciler.java) | So trạng thái mong muốn với Google, tạo/cập nhật/xóa sự kiện do app quản lý; không quét xóa mọi lịch cá nhân. |
| 4 | [GoogleOAuth.java](../../src/main/java/com/studyagent/calendar/GoogleOAuth.java) | OAuth Desktop với PKCE, callback cục bộ, kiểm tra state, lưu và refresh token. API key Gemini không thay thế được bước này. |
| 5 | [PrivateJson.java](../../src/main/java/com/studyagent/calendar/PrivateJson.java) | Lưu JSON riêng tư và quyền truy cập file; không in token khi debug. |
| 6 | [GoogleCalendarGateway.java](../../src/main/java/com/studyagent/calendar/GoogleCalendarGateway.java) | Gọi HTTP Calendar API, lịch riêng của app, phân trang và xử lý lỗi API/quyền sở hữu. |
| 7 | [GoogleCalendarCoordinator.java](../../src/main/java/com/studyagent/calendar/GoogleCalendarCoordinator.java) | Worker nền, gộp yêu cầu đồng bộ, theo dõi thay đổi và thử lại; đọc dữ liệu nguồn qua repository. |
| 8 | [GoogleCalendarPanel.java](../../src/main/java/com/studyagent/ui/GoogleCalendarPanel.java) | Điểm nối giao diện: nhập OAuth JSON, đăng nhập, đồng bộ và hiển thị trạng thái. |

## Các hành vi cần trình bày đúng

- Đã đồng bộ sự kiện lên Google thì điện thoại có thể được Google nhắc khi laptop tắt; cần tài khoản/lịch và quyền thông báo trên điện thoại được thiết lập đúng.
- Laptop tắt thì ứng dụng desktop không thể gửi thay đổi mới lên Google. Hoàn thành trước lịch chỉ ảnh hưởng nhắc trên Google sau khi đồng bộ thành công.
- Ca hoàn thành vẫn giữ sự kiện chính có dấu ✓, tắt nhắc và bỏ sự kiện phụ 5/10 phút; không tự xóa hẳn lịch môn đó.
- Bắt đầu học và hoàn thành học là hai trạng thái khác nhau; xem nhánh `started`/`completed` trong planner.
- Scope hiện dùng là `calendar.app.created`, giới hạn với lịch do ứng dụng tạo. Xem [GOOGLE-CALENDAR.md](../../GOOGLE-CALENDAR.md) để đọc cấu hình và các giới hạn đăng nhập.
- Đồng bộ nhiều lần cần cập nhật đúng sự kiện cũ thay vì tạo bản sao; đọc ID, owner và fingerprint để giải thích.

## Kiểm thử và phối hợp

Đọc [CalendarSyncTest](../../src/test/java/com/studyagent/calendar/CalendarSyncTest.java), [GoogleOAuthTest](../../src/test/java/com/studyagent/calendar/GoogleOAuthTest.java), [GoogleCalendarGatewayTest](../../src/test/java/com/studyagent/calendar/GoogleCalendarGatewayTest.java). Các test gateway/OAuth giả lập không có nghĩa đã gửi thông báo thật tới điện thoại.

Người 3 cung cấp lịch/nhóm ca; người 4 cung cấp trạng thái; người 1 cung cấp dữ liệu nguồn. Khi demo nên dùng lịch thử, đồng bộ một buổi rồi đánh dấu hoàn thành và đồng bộ lại để quan sát dấu ✓ và việc bỏ nhắc muộn.
