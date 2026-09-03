# 🤖 Smart Study Agent (Trợ lý học tập thông minh)

Dự án môn **Lập trình Hướng đối tượng (OOP - Java)** dành cho nhóm 5 thành viên.

---

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

---

## 👥 Phân chia trách nhiệm 5 thành viên

### 🥇 Thành viên 1: Core Agent & CLI Interface
* **File phụ trách:**
  * `src/main/java/com/studyagent/Main.java`
  * `src/main/java/com/studyagent/agent/StudyAgent.java`
* **Nhiệm vụ:**
  1. Xây dựng giao diện dòng lệnh (CLI Menu & interactive prompt loop).
  2. Tiếp nhận input từ người dùng và điều phối các Service (`AIParser` -> `TaskManager` -> `PomodoroManager` -> `Scheduler` -> `Repository`).
  3. Xử lý phản hồi kết quả và hiển thị lịch học/tiến độ trực quan cho người dùng.

---

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

---

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

---

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

---

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

---

## 🚀 Hướng dẫn Chạy & Build Dự án

### 1. Mở trong IDE (IntelliJ IDEA / Eclipse / VS Code)
- Mở thư mục gốc `study-agent/` trong IDE.
- IDE sẽ tự động nhận diện file `pom.xml` và tải các thư viện cần thiết (`sqlite-jdbc`, `gson`).
- Chạy class `com.studyagent.Main`.

### 2. Chạy trực tiếp từ dòng lệnh (Không cần Maven)
```bash
# Biên dịch toàn bộ code Java
javac -encoding UTF-8 -d bin src/main/java/com/studyagent/**/*.java src/main/java/com/studyagent/*.java

# Chạy chương trình
java -cp bin com.studyagent.Main
```
