package com.studyagent.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:study_agent.db";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public static void initializeDatabase() {
        System.out.println("🗄️ [DatabaseManager] Đang kiểm tra và khởi tạo cấu trúc bảng SQLite...");
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS tasks (" +
                    "id TEXT PRIMARY KEY, title TEXT NOT NULL, subject TEXT, estimated_minutes INTEGER NOT NULL, " +
                    "deadline TEXT, priority TEXT DEFAULT 'MEDIUM', status TEXT DEFAULT 'TODO', created_at TEXT NOT NULL);");

            stmt.execute("CREATE TABLE IF NOT EXISTS pomodoro_sessions (" +
                    "id TEXT PRIMARY KEY, task_id TEXT NOT NULL, session_index INTEGER NOT NULL, " +
                    "duration_minutes INTEGER NOT NULL, break_minutes INTEGER NOT NULL, is_completed INTEGER DEFAULT 0, completed_at TEXT);");

            stmt.execute("CREATE TABLE IF NOT EXISTS schedules (" +
                    "id TEXT PRIMARY KEY, task_id TEXT NOT NULL, start_time TEXT NOT NULL, end_time TEXT NOT NULL, reminder_sent INTEGER DEFAULT 0);");

            stmt.execute("CREATE TABLE IF NOT EXISTS timetables (" +
                    "id TEXT PRIMARY KEY, chat_id INTEGER NOT NULL, day_of_week TEXT NOT NULL, start_time TEXT NOT NULL, " +
                    "end_time TEXT NOT NULL, subject_name TEXT NOT NULL, room TEXT, note TEXT, created_at TEXT NOT NULL);");

            stmt.execute("CREATE TABLE IF NOT EXISTS study_sessions (" +
                    "id TEXT PRIMARY KEY, chat_id INTEGER NOT NULL, day_of_week TEXT NOT NULL, scheduled_date TEXT, " +
                    "start_time TEXT NOT NULL, end_time TEXT NOT NULL, subject_name TEXT NOT NULL, topic_overview TEXT, " +
                    "preparation_materials TEXT, is_reminded INTEGER DEFAULT 0, created_at TEXT NOT NULL);");

            stmt.execute("CREATE TABLE IF NOT EXISTS syllabuses (" +
                    "id TEXT PRIMARY KEY, chat_id INTEGER NOT NULL, subject_name TEXT NOT NULL, overview TEXT, " +
                    "syllabus_content TEXT, exam_tips TEXT, recommended_materials TEXT, created_at TEXT NOT NULL);");

            System.out.println("✅ [DatabaseManager] Toàn bộ cơ sở dữ liệu SQLite đã sẵn sàng.");
        } catch (SQLException e) {
            System.err.println("❌ [DatabaseManager] Lỗi khởi tạo SQLite: " + e.getMessage());
        }
    }
}
