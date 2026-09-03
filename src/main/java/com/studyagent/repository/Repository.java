package com.studyagent.repository;

import com.studyagent.pomodoro.PomodoroSession;
import com.studyagent.scheduler.Schedule;
import com.studyagent.task.Task;
import java.util.List;

/**
 * Interface trừu tượng hóa tầng lưu trữ dữ liệu (Repository Pattern).
 * @author Thành viên 5 (Database & Persistence)
 */
public interface Repository {
    // Quản lý Task
    void saveTask(Task task);
    Task getTaskById(String id);
    List<Task> getAllTasks();
    void deleteTask(String id);

    // Quản lý Pomodoro Sessions
    void saveSessions(List<PomodoroSession> sessions);
    List<PomodoroSession> getSessionsByTaskId(String taskId);

    // Quản lý Schedule
    void saveSchedule(Schedule schedule);
    List<Schedule> getSchedules();
}
