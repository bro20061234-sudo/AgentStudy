-- SQLite Database Schema for Smart Study Agent
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

CREATE TABLE IF NOT EXISTS timetables (
    id TEXT PRIMARY KEY,
    chat_id INTEGER NOT NULL,
    day_of_week TEXT NOT NULL,
    start_time TEXT NOT NULL,
    end_time TEXT NOT NULL,
    subject_name TEXT NOT NULL,
    room TEXT,
    note TEXT,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS study_sessions (
    id TEXT PRIMARY KEY,
    chat_id INTEGER NOT NULL,
    day_of_week TEXT NOT NULL,
    scheduled_date TEXT,
    start_time TEXT NOT NULL,
    end_time TEXT NOT NULL,
    subject_name TEXT NOT NULL,
    topic_overview TEXT,
    preparation_materials TEXT,
    is_reminded INTEGER DEFAULT 0,
    is_completed INTEGER DEFAULT 0,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS textbooks (
    id TEXT PRIMARY KEY,
    chat_id INTEGER NOT NULL,
    subject_name TEXT NOT NULL,
    file_name TEXT,
    content TEXT,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS syllabuses (
    id TEXT PRIMARY KEY,
    chat_id INTEGER NOT NULL,
    subject_name TEXT NOT NULL,
    overview TEXT,
    syllabus_content TEXT,
    exam_tips TEXT,
    recommended_materials TEXT,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS weekly_curricula (
    id TEXT PRIMARY KEY,
    chat_id INTEGER NOT NULL,
    subject_name TEXT NOT NULL,
    week_number INTEGER NOT NULL,
    chapter_title TEXT NOT NULL,
    detailed_syllabus TEXT,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS quiz_questions (
    id TEXT PRIMARY KEY,
    chat_id INTEGER NOT NULL,
    subject_name TEXT NOT NULL,
    week_number INTEGER DEFAULT 1,
    phase TEXT NOT NULL, -- 'START_SESSION' (đầu ca: ôn bài cũ), 'END_SESSION' (cuối ca: ôn bài mới), 'CONTEST'
    question TEXT NOT NULL,
    options_json TEXT NOT NULL, -- JSON array ["A. ...", "B. ...", "C. ...", "D. ..."]
    correct_answer TEXT NOT NULL, -- e.g. "A"
    explanation TEXT,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS quiz_attempts (
    id TEXT PRIMARY KEY,
    chat_id INTEGER NOT NULL,
    subject_name TEXT NOT NULL,
    quiz_type TEXT NOT NULL,
    score INTEGER NOT NULL,
    total_questions INTEGER NOT NULL,
    completed_at TEXT NOT NULL
);
