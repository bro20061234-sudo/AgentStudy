package com.studyagent.repository;

import com.studyagent.pomodoro.PomodoroSession;
import com.studyagent.scheduler.Schedule;
import com.studyagent.task.Priority;
import com.studyagent.task.Task;
import com.studyagent.task.TaskStatus;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Triển khai lưu trữ dữ liệu vào SQLite qua JDBC.
 * @author Thành viên 5 (Database & Persistence)
 */
public class SqliteRepository implements Repository {

    public SqliteRepository() {
        DatabaseManager.initializeDatabase();
    }

    @Override
    public void saveTask(Task task) {
        String sql = "INSERT OR REPLACE INTO tasks (id, title, subject, estimated_minutes, deadline, priority, status, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, task.getId());
            pstmt.setString(2, task.getTitle());
            pstmt.setString(3, task.getSubject());
            pstmt.setInt(4, task.getEstimatedMinutes());
            pstmt.setString(5, task.getDeadline() != null ? task.getDeadline().toString() : null);
            pstmt.setString(6, task.getPriority().name());
            pstmt.setString(7, task.getStatus().name());
            pstmt.setString(8, task.getCreatedAt().toString());
            pstmt.executeUpdate();
            System.out.println("💾 [SqliteRepository] Đã lưu Task [" + task.getId() + "] vào SQLite.");
        } catch (SQLException e) {
            System.err.println("❌ Lỗi lưu Task: " + e.getMessage());
        }
    }

    @Override
    public Task getTaskById(String id) {
        // TODO (TV 5): Viết câu lệnh SELECT * FROM tasks WHERE id = ?
        return null;
    }

    @Override
    public List<Task> getAllTasks() {
        List<Task> list = new ArrayList<>();
        String sql = "SELECT * FROM tasks";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Task t = new Task();
                t.setId(rs.getString("id"));
                t.setTitle(rs.getString("title"));
                t.setSubject(rs.getString("subject"));
                t.setEstimatedMinutes(rs.getInt("estimated_minutes"));
                String dl = rs.getString("deadline");
                if (dl != null) t.setDeadline(LocalDateTime.parse(dl));
                t.setPriority(Priority.fromString(rs.getString("priority")));
                t.setStatus(TaskStatus.valueOf(rs.getString("status")));
                t.setCreatedAt(LocalDateTime.parse(rs.getString("created_at")));
                list.add(t);
            }
        } catch (SQLException e) {
            System.err.println("❌ Lỗi đọc Tasks: " + e.getMessage());
        }
        return list;
    }

    @Override
    public void deleteTask(String id) {
        // TODO (TV 5): Viết câu lệnh DELETE FROM tasks WHERE id = ?
    }

    @Override
    public void saveSessions(List<PomodoroSession> sessions) {
        // TODO (TV 5): Dùng PreparedStatement và batch insert để lưu toàn bộ danh sách sessions
        String sql = "INSERT OR REPLACE INTO pomodoro_sessions (id, task_id, session_index, duration_minutes, break_minutes, is_completed, completed_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (PomodoroSession s : sessions) {
                pstmt.setString(1, s.getId());
                pstmt.setString(2, s.getTaskId());
                pstmt.setInt(3, s.getSessionIndex());
                pstmt.setInt(4, s.getDurationMinutes());
                pstmt.setInt(5, s.getBreakMinutes());
                pstmt.setInt(6, s.isCompleted() ? 1 : 0);
                pstmt.setString(7, s.getCompletedAt() != null ? s.getCompletedAt().toString() : null);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            System.out.println("💾 [SqliteRepository] Đã lưu " + sessions.size() + " phiên Pomodoro vào SQLite.");
        } catch (SQLException e) {
            System.err.println("❌ Lỗi lưu Sessions: " + e.getMessage());
        }
    }

    @Override
    public List<PomodoroSession> getSessionsByTaskId(String taskId) {
        // TODO (TV 5): Lấy danh sách phiên của 1 task từ bảng pomodoro_sessions
        return new ArrayList<>();
    }

    @Override
    public void saveSchedule(Schedule schedule) {
        // TODO (TV 5): Lưu schedule vào bảng schedules
    }

    @Override
    public List<Schedule> getSchedules() {
        // TODO (TV 5): Đọc toàn bộ danh sách schedules
        return new ArrayList<>();
    }
}
