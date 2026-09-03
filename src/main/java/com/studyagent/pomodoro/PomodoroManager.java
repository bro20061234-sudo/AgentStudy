package com.studyagent.pomodoro;

import com.studyagent.task.Task;
import java.util.ArrayList;
import java.util.List;

/**
 * Bộ điều khiển và thuật toán chia nhỏ công việc thành các phiên Pomodoro.
 * @author Thành viên 4 (Pomodoro & Scheduler)
 */
public class PomodoroManager {
    public static final int DEFAULT_FOCUS_TIME = 25;
    public static final int DEFAULT_SHORT_BREAK = 5;
    public static final int DEFAULT_LONG_BREAK = 15;

    /**
     * Tự động chia nhỏ thời lượng của Task thành các phiên Pomodoro 25p.
     */
    public List<PomodoroSession> splitTaskToSessions(Task task) {
        List<PomodoroSession> sessions = new ArrayList<>();
        int totalMinutes = task.getEstimatedMinutes();

        // TODO (TV 4):
        // 1. Tính toán số lượng phiên học cần thiết: int numSessions = (int) Math.ceil((double) totalMinutes / DEFAULT_FOCUS_TIME);
        // 2. Cứ mỗi 4 phiên học thì cho 1 phiên nghỉ dài (DEFAULT_LONG_BREAK = 15p), các phiên khác nghỉ ngắn 5p.
        // 3. Tạo các đối tượng PomodoroSession và gán taskId.
        // 4. Gán danh sách phiên này vào task.setSessions(sessions).

        int count = Math.max(1, (int) Math.ceil((double) totalMinutes / DEFAULT_FOCUS_TIME));
        for (int i = 1; i <= count; i++) {
            int breakTime = (i % 4 == 0) ? DEFAULT_LONG_BREAK : DEFAULT_SHORT_BREAK;
            PomodoroSession session = new PomodoroSession(task.getId(), i, DEFAULT_FOCUS_TIME, breakTime);
            sessions.add(session);
        }

        task.setSessions(sessions);
        System.out.println("⏱️ [PomodoroManager] Đã chia nhiệm vụ thành " + sessions.size() + " phiên Pomodoro.");
        return sessions;
    }

    /**
     * Khởi động đồng hồ đếm ngược cho một phiên Pomodoro.
     */
    public void startSessionTimer(PomodoroSession session) {
        System.out.println("⏳ Bắt đầu phiên " + session.getSessionIndex() + " (" + session.getDurationMinutes() + " phút tập trung)...");
        // TODO (TV 4):
        // Dùng Thread.sleep() hoặc ScheduledExecutorService để đếm ngược thời gian,
        // Khi hết giờ -> phát chuông/in thông báo -> chuyển sang giờ nghỉ -> gọi session.completeSession().
    }
}
