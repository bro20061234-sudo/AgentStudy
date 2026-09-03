package com.studyagent.agent;

import com.studyagent.parser.AIParser;
import com.studyagent.parser.GeminiAIParser;
import com.studyagent.parser.StudyRequest;
import com.studyagent.pomodoro.PomodoroManager;
import com.studyagent.pomodoro.PomodoroSession;
import com.studyagent.repository.Repository;
import com.studyagent.repository.SqliteRepository;
import com.studyagent.scheduler.Schedule;
import com.studyagent.scheduler.Scheduler;
import com.studyagent.task.Task;
import com.studyagent.task.TaskManager;

import java.util.List;

/**
 * Trợ lý điều phối trung tâm (Study Agent Orchestrator).
 * Kết nối toàn bộ các phân hệ theo đúng Workflow.
 * @author Thành viên 1 (Leader / Core Agent)
 */
public class StudyAgent {
    private AIParser aiParser;
    private TaskManager taskManager;
    private PomodoroManager pomodoroManager;
    private Scheduler scheduler;
    private Repository repository;

    public StudyAgent() {
        // Khởi tạo các phân hệ
        this.aiParser = new GeminiAIParser("YOUR_GEMINI_API_KEY");
        this.repository = new SqliteRepository();
        this.taskManager = new TaskManager(this.repository.getAllTasks());
        this.pomodoroManager = new PomodoroManager();
        this.scheduler = new Scheduler();
    }

    /**
     * Xử lý yêu cầu dạng ngôn ngữ tự nhiên từ người dùng theo đúng Workflow chuẩn:
     * User Input -> AIParser -> StudyRequest -> TaskManager -> Task -> PomodoroManager -> PomodoroSession -> Repository -> SQLite
     */
    public void processNaturalLanguageInput(String userInput) {
        System.out.println("\n=======================================================");
        System.out.println("🤖 [StudyAgent] Tiếp nhận: \"" + userInput + "\"");

        // 1. Bóc tách ngôn ngữ tự nhiên bằng AIParser (TV 2)
        StudyRequest request = aiParser.parse(userInput);
        System.out.println("📋 Dữ liệu trích xuất: " + request);

        // 2. Chuyển cho TaskManager tạo Task (TV 3)
        Task task = taskManager.createTaskFromRequest(request);

        // 3. Chuyển cho PomodoroManager chia phiên học (TV 4)
        List<PomodoroSession> sessions = pomodoroManager.splitTaskToSessions(task);

        // 4. Lập lịch học bằng Scheduler (TV 4)
        Schedule schedule = scheduler.suggestSchedule(task);
        scheduler.scheduleReminder(task, 10); // Đặt lịch nhắc việc

        // 5. Lưu toàn bộ dữ liệu xuống SQLite qua Repository (TV 5)
        repository.saveTask(task);
        repository.saveSessions(sessions);
        repository.saveSchedule(schedule);

        // 6. Hiển thị báo cáo kết quả cho người dùng (TV 1)
        renderSummary(task, sessions, schedule);
        System.out.println("=======================================================\n");
    }

    private void renderSummary(Task task, List<PomodoroSession> sessions, Schedule schedule) {
        System.out.println("\n🎉 KẾT QUẢ XỬ LÝ CỦA TRỢ LÝ HỌC TẬP:");
        System.out.println("📌 Nhiệm vụ : " + task.getTitle() + " [Môn: " + task.getSubject() + "]");
        System.out.println("⏳ Thời lượng : " + task.getEstimatedMinutes() + " phút");
        System.out.println("⏰ Hạn chót   : " + task.getDeadline());
        System.out.println("🚩 Độ ưu tiên : " + task.getPriority().getDisplayName());
        System.out.println("🍅 Phân bổ Pomodoro:");
        for (PomodoroSession s : sessions) {
            System.out.println(s);
        }
        System.out.println(schedule);
    }

    public List<Task> getAllTasks() {
        return repository.getAllTasks();
    }
}
