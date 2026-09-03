package com.studyagent.scheduler;

import com.studyagent.task.Task;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service lập lịch học thông minh và thông báo nhắc việc chạy ngầm.
 * @author Thành viên 4 (Pomodoro & Scheduler)
 */
public class Scheduler {
    private List<Schedule> schedules;
    private ScheduledExecutorService reminderWorker;

    public Scheduler() {
        this.schedules = new ArrayList<>();
        this.reminderWorker = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Gợi ý khung giờ học tối ưu trước khi đến hạn deadline.
     */
    public Schedule suggestSchedule(Task task) {
        // TODO (TV 4):
        // 1. Dựa vào deadline và thời lượng estimatedMinutes để tìm khung giờ rảnh gần nhất (ví dụ: tối nay 19:30 - 21:30).
        // 2. Tạo Schedule mới và thêm vào danh sách schedules.
        // 3. Đăng ký nhắc nhở tự động trước giờ học 15 phút.

        LocalDateTime start = LocalDateTime.now().plusHours(1);
        LocalDateTime end = start.plusMinutes(task.getEstimatedMinutes());

        Schedule schedule = new Schedule(task.getId(), start, end);
        this.schedules.add(schedule);
        System.out.println("📅 [Scheduler] Gợi ý lịch học: " + schedule);
        return schedule;
    }

    /**
     * Đăng ký một thông báo nhắc việc chạy ngầm không làm đơ giao diện chính.
     */
    public void scheduleReminder(Task task, long delaySeconds) {
        // TODO (TV 4):
        // Dùng reminderWorker.schedule(() -> { notifyUser(task); }, delaySeconds, TimeUnit.SECONDS);
        System.out.println("🔔 [Scheduler] Đã đặt lịch nhắc cho task \"" + task.getTitle() + "\" sau " + delaySeconds + "s.");
    }

    public void shutdown() {
        reminderWorker.shutdown();
    }
}
