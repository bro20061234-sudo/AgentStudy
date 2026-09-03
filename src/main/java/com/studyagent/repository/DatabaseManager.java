package com.studyagent.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Quản lý kết nối JDBC tới cơ sở dữ liệu SQLite và tự động tạo bảng.
 * @author Thành viên 5 (Database & Persistence)
 */
public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:study_agent.db";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public static void initializeDatabase() {
        System.out.println("🗄️ [DatabaseManager] Đang kiểm tra và khởi tạo cấu trúc bảng SQLite...");
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // Tạo bảng tasks
            stmt.execute("CREATE TABLE IF NOT EXISTS tasks (" +
                    "id TEXT PRIMARY KEY, " +
                    "title TEXT NOT NULL, " +
                    "subject TEXT, " +
                    "estimated_minutes INTEGER NOT NULL, " +
                    "deadline TEXT, " +
                    "priority TEXT DEFAULT 'MEDIUM', " +
                    "status TEXT DEFAULT 'TODO', " +
                    "created_at TEXT NOT NULL);");

            // Tạo bảng pomodoro_sessions
            stmt.execute("CREATE TABLE IF NOT EXISTS pomodoro_sessions (" +
                    "id TEXT PRIMARY KEY, " +
                    "task_id TEXT NOT NULL, " +
                    "session_index INTEGER NOT NULL, " +
                    "duration_minutes INTEGER NOT NULL, " +
                    "break_minutes INTEGER NOT NULL, " +
                    "is_completed INTEGER DEFAULT 0, " +
                    "completed_at TEXT);");

            // Tạo bảng schedules
            stmt.execute("CREATE TABLE IF NOT EXISTS schedules (" +
                    "id TEXT PRIMARY KEY, " +
                    "task_id TEXT NOT NULL, " +
                    "start_time TEXT NOT NULL, " +
                    "end_time TEXT NOT NULL, " +
                    "reminder_sent INTEGER DEFAULT 0);");

            System.out.println("✅ [DatabaseManager] Cơ sở dữ liệu SQLite đã sẵn sàng.");
        } catch (SQLException e) {
            System.err.println("❌ [DatabaseManager] Lỗi khởi tạo SQLite: " + e.getMessage());
        }
    }
}
