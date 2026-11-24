# 🤖 Smart Study Agent (Trợ lý học tập thông minh)

Dự án môn **Lập trình Hướng đối tượng (OOP - Java)** dành cho nhóm 5 thành viên.

## Bản chạy Windows (.exe)

Nhắc lịch Google Calendar và thiết lập điện thoại: xem [GOOGLE-CALENDAR.md](GOOGLE-CALENDAR.md). Ca tự học có trạng thái hoàn thành, quá hạn, chưa đến lịch; hỗ trợ học trước và lưu thời điểm vào học. Cần kết nối tài khoản Google trong ứng dụng trước khi nhận thông báo trên điện thoại.

Chạy `package-windows.bat` để tạo `dist/SmartStudyAgent/SmartStudyAgent.exe` và `dist/SmartStudyAgent-Windows.zip`. Bản đóng gói có Java đi kèm, không cần Java hay Maven trên máy sử dụng. Giải nén ZIP, giữ nguyên cả thư mục và mở `SmartStudyAgent.exe`.

Mặc định bản đóng gói kèm bản sao dữ liệu, tài liệu và cấu hình cá nhân hiện tại. Đường dẫn tài liệu trong bản sao được đổi để tiếp tục mở được khi chuyển thư mục; dữ liệu gốc không bị đổi. Muốn tạo bản không chứa dữ liệu/cấu hình cá nhân, chạy `package-windows.bat -CleanProfile`.

Sau khi đóng gói, cấu hình nằm ở `config.properties` cạnh EXE, dữ liệu ở `study_agent.db` và `data`, nhật ký ở `data/logs/desktop.log`. Có thể chạy EXE từ shortcut mà không cần đặt thư mục làm việc. Script tự kiểm tra khởi động, SQLite, giao diện và bộ đọc PDF/Word, không gọi API trong lúc kiểm tra.



Bản desktop hiện tại khởi động từ Main (Maven) hoặc DesktopLauncher (EXE), nhắc học qua Google Calendar. Phân công đọc backend hiện tại: [5 phần backend](docs/backend/README.md). Các mô tả CLI/task bên dưới thuộc kiến trúc ban đầu.

## 🏗️ Kiến trúc & Luồng hoạt động (Workflow)

```
                            👤 USER
                               │
                               ↓
                        💬 User Input
                               │
                               ↓
                       🤖 STUDY AGENT  (TV 1)
                               │
              ┌────────────────┼────────────────┐
              ↓                ↓                ↓
          AIParser        TaskManager       Scheduler
           (TV 2)           (TV 3)            (TV 4)
              │                │                │
              ↓                ↓                ↓
        StudyRequest          Task           Schedule
                               │
                               ↓
                        PomodoroManager  (TV 4)
                               │
                               ↓
                        PomodoroSession
                               │
                               ↓
                           Repository    (TV 5)
                               │
                               ↓
                            SQLite
```


## 👥 Phân chia trách nhiệm 5 thành viên

### 🥇 Thành viên 1: Core Agent & CLI Interface
* **File phụ trách:**
  * `src/main/java/com/studyagent/Main.java`
  * `src/main/java/com/studyagent/agent/StudyAgent.java`
* **Nhiệm vụ:**
  1. Xây dựng giao diện dòng lệnh (CLI Menu & interactive prompt loop).
  2. Tiếp nhận input từ người dùng và điều phối các Service (`AIParser` -> `TaskManager` -> `PomodoroManager` -> `Scheduler` -> `Repository`).
  3. Xử lý phản hồi kết quả và hiển thị lịch học/tiến độ trực quan cho người dùng.


### 🥈 Thành viên 2: AI Natural Language Processing (NLP)
* **File phụ trách:**
  * `src/main/java/com/studyagent/parser/AIParser.java` (Interface)
  * `src/main/java/com/studyagent/parser/GeminiAIParser.java`
  * `src/main/java/com/studyagent/parser/RegexAIParser.java`
  * `src/main/java/com/studyagent/parser/StudyRequest.java` (DTO)
* **Nhiệm vụ:**
  1. Gửi chuỗi input tự nhiên của người dùng tới Gemini API (REST Endpoint với Java `HttpClient`).
  2. Bóc tách JSON trả về thành đối tượng `StudyRequest` (tiêu đề, môn học, thời lượng phút, deadline).
  3. Hoàn thiện `RegexAIParser` làm cơ chế Fallback phòng khi offline hoặc mất mạng.


### 🥉 Thành viên 3: Task & Deadline Management
* **File phụ trách:**
  * `src/main/java/com/studyagent/task/Task.java`
  * `src/main/java/com/studyagent/task/TaskManager.java`
  * `src/main/java/com/studyagent/task/TaskStatus.java`
  * `src/main/java/com/studyagent/task/Priority.java`
* **Nhiệm vụ:**
  1. Hiện thực hóa các phương thức trong `TaskManager`: `createTaskFromRequest()`, `getTasks()`, `updateTaskStatus()`, `getOverdueTasks()`.
  2. Kiểm tra tính hợp lệ của Deadline và phân loại mức độ ưu tiên.
  3. Lọc danh sách công việc theo trạng thái (`TODO`, `IN_PROGRESS`, `DONE`, `OVERDUE`) hoặc theo môn học.


### 🏅 Thành viên 4: Pomodoro Engine & Scheduler
* **File phụ trách:**
  * `src/main/java/com/studyagent/pomodoro/PomodoroSession.java`
  * `src/main/java/com/studyagent/pomodoro/PomodoroManager.java`
  * `src/main/java/com/studyagent/scheduler/Schedule.java`
  * `src/main/java/com/studyagent/scheduler/Scheduler.java`
* **Nhiệm vụ:**
  1. Xây dựng thuật toán trong `PomodoroManager.splitTaskToSessions()` để chia nhỏ thời lượng thành các phiên (mặc định 25 phút học + 5 phút nghỉ).
  2. Viết bộ đếm ngược Pomodoro và cập nhật trạng thái hoàn thành từng phiên.
  3. Viết thuật toán gợi ý khung giờ học tối ưu trong `Scheduler`.
  4. Sử dụng `ScheduledExecutorService` để chạy luồng thông báo nhắc việc khi đến hạn.


### 🏅 Thành viên 5: Database & Persistence Layer (SQLite)
* **File phụ trách:**
  * `src/main/java/com/studyagent/repository/Repository.java` (Interface)
  * `src/main/java/com/studyagent/repository/DatabaseManager.java`
  * `src/main/java/com/studyagent/repository/SqliteRepository.java`
  * `src/main/resources/schema.sql`
* **Nhiệm vụ:**
  1. Quản lý kết nối SQLite JDBC (`study_agent.db`) và khởi tạo bảng tự động từ file `schema.sql`.
  2. Hiện thực các hàm CRUD trong `SqliteRepository`:
     - `saveTask()`, `getAllTasks()`, `getTaskById()`, `deleteTask()`
     - `saveSessions()`, `getSessionsByTaskId()`
     - `saveSchedule()`, `getSchedules()`
  3. Đảm bảo an toàn luồng và xử lý transaction khi lưu dữ liệu.


## 🚀 Hướng dẫn Chạy & Build Dự án

### Cấu hình Gemini API Key

Để phân tích thời khóa biểu bằng AI, hãy tạo API key tại [Google AI Studio](https://aistudio.google.com), sau đó thay `YOUR_GEMINI_API_KEY` trong `config.properties`:

```properties
gemini.api.key=YOUR_ACTUAL_GEMINI_API_KEY
```

Không để nguyên giá trị mẫu. Có thể thay bằng biến môi trường `GEMINI_API_KEY`; biến môi trường sẽ được ưu tiên hơn giá trị trong file. Khởi động lại ứng dụng sau khi cập nhật cấu hình. Không chia sẻ hoặc đưa API key lên Git.

### 1. Mở trong IDE (IntelliJ IDEA / Eclipse / VS Code)

### 2. Chạy trực tiếp từ dòng lệnh (Không cần Maven)
```bash
# Biên dịch toàn bộ code Java
javac -encoding UTF-8 -d bin src/main/java/com/studyagent/**/*.java src/main/java/com/studyagent/*.java

# Chạy chương trình
java -cp bin com.studyagent.Main
```
#
## Tự động phân tích giáo trình

Nạp PDF/DOCX và chọn môn: ứng dụng tự lưu file gốc, nhận diện cấu trúc **Chương / Bài / Phần** rồi soạn đề cương cho tất cả các mục, độc lập với số ca tự học trên lịch. Không cần bấm soạn từng chương.

- Tự phân tích theo nhóm 1–2 mục mỗi request, hiển thị tiến độ đã xong bao nhiêu mục. Mỗi đề cương có tiêu đề màu, từ khóa in đậm, các ý chính, ví dụ, bài tập, tự kiểm tra và trang PDF tham khảo.
- Lưu ngay từng đề cương thành công. Nếu timeout/quota, báo rõ đã xong bao nhiêu; **Phân tích / tiếp tục giáo trình** chỉ soạn các mục còn thiếu. Nạp lại cùng nội dung không gọi lại cho những đề cương đã xong. Có thể soạn lại riêng mục đã chọn nếu muốn.
- Nhận diện cả tài liệu dùng “Bài 1”, bài gộp “Bài 3 và 4”, “Chương I”, “Chapter”, “Phần”. Loại mục lục có dấu chấm dẫn và câu nhắc chương trong lời giới thiệu. Giữ tên mục theo sách.
- Khi không nhận diện được bằng tiêu đề, tự dùng AI nhận diện cấu trúc; đối chiếu tiêu đề/số trang với văn bản thật trước khi lưu. Không còn tạo một chương giả “Toàn bộ giáo trình — chưa nhận diện chương” rồi báo thành công. PDF scan không có văn bản cần OCR trước.
- Khi đến ca tự học, bạn chọn chương/bài muốn học; một mục có thể học qua nhiều ca. Tiến độ mục độc lập với trạng thái ca học. Ôn tập có câu hỏi được lưu, bài đang làm, chấm điểm, lịch sử và luyện câu sai. Câu hỏi dùng nguồn của đúng chương/bài.
- File gốc được lưu nguyên bản trong `data/textbooks/` và mở bằng trình đọc PDF/Word mặc định. Số trang là thứ tự trang PDF thật; Word không tự gán số trang. Giáo trình cũ chỉ lưu văn bản cần chọn lại file gốc, hoặc nạp lại PDF để có trang tham khảo.
- Đề cương chia theo lịch cũ và chương giả được sửa khi phân tích tiếp. Danh sách trước khi thay thế được lưu trong `curriculum_archives`, không xóa lịch sử câu hỏi cũ.
- API nhớ model đã thành công. 429 dừng gửi và đọc thời gian chờ; 400/401/403 dừng ngay; không dò liên tục qua mọi model. Có thể cấu hình model qua `GEMINI_MODEL`. Tự động xử lý không làm tăng quota Google cấp.

Kiểm tra không gọi API: `build.bat "-Dtest=TextbookProcessorTest,ChapterLearningTest,LessonReviewTest,LearningContentTest" test`.
## Giao diện và chế độ tối

- Trang Tổng quan dùng dữ liệu thật: tiến độ chương/bài, các đề cương đã xong và lịch sắp tới. Không tự gọi AI khi mở màn hình.
- Nút **Chế độ tối / Chế độ sáng** ở góc phải áp dụng cho toàn ứng dụng, gồm cả nội dung đề cương và hộp thoại. Lựa chọn được lưu tại `data/appearance.properties` và khôi phục khi mở lại.
- Lịch học là lưới tuần có khung giờ, mặc định ở tuần hiện tại và tab Tự học. Chọn ca để vào học; nhấp chuột phải để sửa giờ hoặc đổi trạng thái đã học. Tạo lịch vẫn không gọi AI để viết nội dung ca học.
- Thư viện có nút Mở file, Xem đề cương và Xóa trên từng môn. Nhập liệu không tự điền lịch mẫu; nút Dùng mẫu vẫn có sẵn khi cần thử.
- Các màn dùng chung màu sắc, cỡ chữ và nút. Đổi chế độ không tạo lại cửa sổ, không mất nội dung đang nhập hoặc bài đang làm. Các thao tác thành công thông thường được báo ở thanh trạng thái; lỗi vẫn có thông báo cụ thể.

Chạy ứng dụng thật: `run.bat`. Bản mẫu `preview-ui.bat` chỉ để tham khảo thiết kế, không phải dữ liệu thật.

Kiểm tra: `build.bat "-Dtest=DesktopThemeTest,ScheduleWithoutAiTest,TextbookProcessorTest,ChapterLearningTest,LessonReviewTest,LearningContentTest" test`.
