# Walkthrough: Smart Study Agent (OOP Java Repository)

Repository dự án **Smart Study Agent** dành cho nhóm 5 thành viên đã được khởi tạo hoàn chỉnh và kiểm thử thành công tại:
`C:\Users\Dell\.gemini\antigravity\scratch\study-agent`

---

## 📂 Tổng quan cấu trúc thư mục & phân công

```
study-agent/
├── pom.xml                                    # File cấu hình Maven (SQLite JDBC, Gson, JUnit 5)
├── README.md                                  # Hướng dẫn chi tiết phân chia công việc & cài đặt
├── study_agent.db                             # Cơ sở dữ liệu SQLite đã được tạo tự động
├── lib/                                       # Thư viện JAR chạy offline (sqlite-jdbc, gson, slf4j)
└── src/
    ├── main/
    │   ├── java/com/studyagent/
    │   │   ├── Main.java                      # [TV 1] Entry point & Menu tương tác CLI
    │   │   ├── agent/
    │   │   │   └── StudyAgent.java            # [TV 1] Trợ lý điều phối trung tâm (Orchestrator)
    │   │   ├── parser/
    │   │   │   ├── AIParser.java              # [TV 2] Interface bộ phân tích cú pháp
    │   │   │   ├── GeminiAIParser.java        # [TV 2] Gọi Google Gemini REST API
    │   │   │   ├── RegexAIParser.java         # [TV 2] Fallback Regex khi mất mạng
    │   │   │   └── StudyRequest.java          # [TV 2] DTO kết quả bóc tách
    │   │   ├── task/
    │   │   │   ├── Priority.java              # [TV 3] Enum mức độ ưu tiên
    │   │   │   ├── TaskStatus.java            # [TV 3] Enum trạng thái nhiệm vụ
    │   │   │   ├── Task.java                  # [TV 3] Entity Task
    │   │   │   └── TaskManager.java           # [TV 3] Service quản lý CRUD Task & Deadline
    │   │   ├── pomodoro/
    │   │   │   ├── PomodoroSession.java       # [TV 4] Entity một phiên học
    │   │   │   └── PomodoroManager.java       # [TV 4] Thuật toán chia phiên 25p/5p
    │   │   ├── scheduler/
    │   │   │   ├── Schedule.java              # [TV 4] Entity lịch trình
    │   │   │   └── Scheduler.java             # [TV 4] Lập lịch & Nhắc việc đa luồng
    │   │   └── repository/
    │   │       ├── Repository.java            # [TV 5] Interface lưu trữ (Repository Pattern)
    │   │       ├── DatabaseManager.java       # [TV 5] Quản lý kết nối JDBC SQLite
    │   │       └── SqliteRepository.java      # [TV 5] Triển khai CRUD SQLite
    │   └── resources/
    │       └── schema.sql                     # [TV 5] Script SQL tạo bảng
```

---

## 🎯 Chi tiết từng thành viên & các gợi ý TODO sẵn có

| Thành viên | File chính | Các TODO đã cắm sẵn trong code |
| :--- | :--- | :--- |
| **TV 1 (Leader)** | `Main.java`<br>`StudyAgent.java` | - Hoàn thiện menu tương tác CLI.<br>- Gọi luồng điều phối: `AIParser` ➔ `TaskManager` ➔ `PomodoroManager` ➔ `Scheduler` ➔ `Repository`. |
| **TV 2** | `GeminiAIParser.java`<br>`RegexAIParser.java` | - Viết prompt Gemini trả về JSON chuẩn.<br>- Gửi HTTP Request qua `HttpClient`.<br>- Regex nhận diện thời gian (`tiếng`, `giờ`, `phút`) và deadline. |
| **TV 3** | `TaskManager.java`<br>`Task.java` | - Kiểm tra tính hợp lệ của dữ liệu đầu vào.<br>- Lọc danh sách task theo trạng thái (`TODO`, `IN_PROGRESS`, `COMPLETED`, `OVERDUE`). |
| **TV 4** | `PomodoroManager.java`<br>`Scheduler.java` | - Thuật toán chia phiên Pomodoro ($25\text{p} + 5\text{p}$, phiên thứ 4 nghỉ dài $15\text{p}$).<br>- `ScheduledExecutorService` đếm ngược và bắn thông báo nhắc nhở. |
| **TV 5** | `SqliteRepository.java`<br>`DatabaseManager.java` | - Hoàn thiện các câu lệnh PreparedStatement cho `getTaskById()`, `deleteTask()`, `saveSchedule()`.<br>- Tối ưu hóa lưu batch cho `saveSessions()`. |

---

## 🧪 Kết quả kiểm thử (Validation Results)

Đã chạy kiểm thử thực tế toàn bộ luồng tự động (End-to-End Mock Run):
- [x] **Biên dịch**: 100% các file Java biên dịch thành công (0 lỗi cú pháp).
- [x] **SQLite Database**: Tự động tạo file `study_agent.db` và khởi tạo đầy đủ các bảng `tasks`, `pomodoro_sessions`, `schedules`.
- [x] **Workflow hoạt động**:
  1. Người dùng nhập: `"Học 2 tiếng ôn thi OOP Java trước 20h ngày mai"`
  2. Bóc tách thành công ➔ Tạo Task với thời lượng 120 phút.
  3. Tự động chia thành 5 phiên Pomodoro (4 phiên nghỉ 5p, 1 phiên nghỉ dài 15p).
  4. Lập lịch học và đặt hẹn giờ nhắc nhở qua `ScheduledExecutorService`.
  5. Dữ liệu được lưu thành công vào cơ sở dữ liệu SQLite.
