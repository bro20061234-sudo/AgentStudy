# Nhận nhắc học trên điện thoại khi máy tính tắt

## 1. Tạo cấu hình Google một lần

1. Mở [Google Cloud Console](https://console.cloud.google.com/), đăng nhập Gmail bạn sẽ dùng trên điện thoại và tạo/chọn một dự án.
2. Trong **APIs & Services → Library**, tìm **Google Calendar API** và bấm **Enable**.
3. Mở **Google Auth Platform → Branding**. Nếu chưa thiết lập, chọn **Get started**, đặt tên ứng dụng **Study Agent**, điền email hỗ trợ và email liên hệ của bạn.
4. Với Gmail cá nhân, chọn **External** ở phần Audience. Khi dự án ở chế độ **Testing**, thêm chính Gmail của bạn vào **Audience → Test users**.
5. Ở **Data Access**, chọn quyền `https://www.googleapis.com/auth/calendar.app.created`. Quyền này cho phép quản lý các lịch phụ do ứng dụng tạo, thay vì yêu cầu toàn bộ lịch cá nhân.
6. Mở **Clients → Create client → Desktop app**, đặt tên, tạo client và tải file JSON.
7. Trong Study Agent: **Lịch học → Google Calendar… → Chọn cấu hình OAuth (.json)**, chọn file vừa tải.
8. Bấm **Kết nối Google**, chọn đúng Gmail trên trang Google và đồng ý cấp quyền. Chờ dòng **Đã đồng bộ** trong ứng dụng trước khi tắt máy.

Nguồn thiết lập: [Google Auth Platform](https://developers.google.com/workspace/guides/configure-oauth-consent), [quyền Calendar API](https://developers.google.com/workspace/calendar/api/auth), [OAuth cho ứng dụng máy tính](https://developers.google.com/identity/protocols/oauth2/native-app).

## 2. Bật nhắc trên điện thoại

- Cài Google Calendar/Lịch Google trên Android hoặc iPhone.
- Đăng nhập cùng Gmail vừa kết nối. Bật hiển thị và đồng bộ lịch **Study Agent**.
- Cho phép thông báo trong cài đặt hệ điều hành và trong Google Calendar. Âm thanh, rung và chế độ Không làm phiền có thể ảnh hưởng việc bạn nhận biết thông báo.
- Lịch đã lên Google tiếp tục nhắc khi máy tính tắt. Máy tính chỉ cần Internet lúc đưa lịch lên hoặc gửi thay đổi.

[Hướng dẫn thông báo Google Calendar](https://support.google.com/calendar/answer/37242?hl=vi).

## 3. Quy tắc đang dùng

- **Lịch trên lớp:** nhắc trước giờ bắt đầu **10 phút**.
- **Tự học:** nhắc trước **10 phút**; nếu chưa vào học, có thêm nhắc sau **5 phút** và **10 phút**. Hai mốc sau giờ học là hai sự kiện nhắc nhỏ trên Google Calendar, vì Calendar API đặt nhắc theo số phút trước giờ bắt đầu sự kiện.
- Bấm **Vào học** trên ca: lưu thời điểm bắt đầu và đồng bộ để bỏ hai nhắc muộn của ca đó.
- Nếu sau 10 phút chưa vào học, trạng thái là **Quá hạn**. Ca vẫn có thể học bù và hoàn thành sau đó.
- Đã vào học nhưng chưa hoàn thành khi hết khung giờ: cũng là **Quá hạn**.
- Bấm **Hoàn thành ca học** ở Tập trung/Ôn tập hoặc đánh dấu hoàn thành từ lịch. Khi đồng hồ tập trung của ca chạy hết, ca đang học cũng được hoàn thành tự động.
- Có thể vào học và hoàn thành trước lịch. Khi trạng thái hoàn thành đã đồng bộ, tất cả nhắc của ca đó được tắt. Các thông báo đã phát trước lúc đồng bộ không thể thu hồi.
- Tỷ lệ **đã hoàn thành / ca đã đến hạn** tính trên toàn bộ lịch tự học và chỉ tính ca đã đến giờ bắt đầu. Ca hoàn thành sớm nằm ở **Hoàn thành trước lịch**, rồi vào tỷ lệ khi đến giờ. Các khung cùng môn đã gộp trên giao diện được tính là một ca.

## 4. Khi mạng hoặc quyền Google có vấn đề

- Nhìn trạng thái cạnh nút Google Calendar. **Chưa đồng bộ** nghĩa là các thay đổi gần nhất vẫn chưa lên Google; lịch cũ trên điện thoại có thể vẫn nhắc.
- Ứng dụng tự thử lại khi đang mở. Có thể bấm **Đồng bộ ngay**.
- Nếu học trước khi máy không có Internet, cần đồng bộ trạng thái hoàn thành trước mốc nhắc để Google ngừng nhắc.
- OAuth ở chế độ **Testing** với quyền Calendar có thể cần đăng nhập lại sau 7 ngày theo chính sách Google. Khi báo hết quyền, chọn **Đăng nhập lại**. Lịch đã lưu trên Google vẫn còn, nhưng ứng dụng cần được cấp quyền lại để cập nhật nó. [Chính sách refresh token](https://developers.google.com/identity/protocols/oauth2).
- Cấu hình và quyền đăng nhập lưu riêng trong `data/google`. Không đưa thư mục này vào bản chia sẻ cho người khác. Chạy `package-windows.bat -CleanProfile` để tạo bản ứng dụng sạch.

## Tình trạng kiểm tra

Logic thời gian, học trước, hoàn thành, xóa nhắc, chống trùng và OAuth đã có kiểm thử cục bộ. Gửi thông báo tới điện thoại thật cần bạn hoàn tất đăng nhập và đồng bộ tài khoản Google như trên.
