-- SQLite Database Schema for Smart Study Agent
-- Phụ trách: Thành viên 5 (Database Layer)

CREATE TABLE IF NOT EXISTS tasks (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    subject TEXT,
    estimated_minutes INTEGER NOT NULL,
    deadline TEXT,
    priority TEXT DEFAULT 'MEDIUM',
    status TEXT DEFAULT 'TODO',
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS pomodoro_sessions (
    id TEXT PRIMARY KEY,
    task_id TEXT NOT NULL,
    session_index INTEGER NOT NULL,
    duration_minutes INTEGER NOT NULL,
    break_minutes INTEGER NOT NULL,
    is_completed INTEGER DEFAULT 0,
    completed_at TEXT,
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS schedules (
    id TEXT PRIMARY KEY,
    task_id TEXT NOT NULL,
    start_time TEXT NOT NULL,
    end_time TEXT NOT NULL,
    reminder_sent INTEGER DEFAULT 0,
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
);
