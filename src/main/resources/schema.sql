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
    started_at TEXT,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS textbooks (
    id TEXT PRIMARY KEY,
    chat_id INTEGER NOT NULL,
    subject_name TEXT NOT NULL,
    file_name TEXT,
    original_path TEXT,
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
    source_text TEXT,
    content_status TEXT DEFAULT 'LEGACY',
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS curriculum_archives (
    id TEXT PRIMARY KEY,
    chat_id INTEGER NOT NULL,
    subject_name TEXT NOT NULL,
    plans_json TEXT NOT NULL,
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

-- Versioned per-lesson review workspace (independent from legacy contests).
CREATE TABLE IF NOT EXISTS lesson_reviews (
    chat_id INTEGER NOT NULL,
    lesson_key TEXT NOT NULL,
    questions_json TEXT NOT NULL,
    answers_json TEXT NOT NULL,
    submitted INTEGER NOT NULL DEFAULT 0,
    completed INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (chat_id, lesson_key)
);
CREATE TABLE IF NOT EXISTS lesson_review_attempts (
    id TEXT PRIMARY KEY,
    chat_id INTEGER NOT NULL,
    lesson_key TEXT NOT NULL,
    questions_json TEXT NOT NULL,
    answers_json TEXT NOT NULL,
    correct INTEGER NOT NULL,
    total INTEGER NOT NULL,
    completed_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_review_history ON lesson_review_attempts(chat_id, lesson_key, completed_at);

CREATE TABLE IF NOT EXISTS question_bank (
    id TEXT NOT NULL,
    chat_id INTEGER NOT NULL,
    subject_name TEXT NOT NULL,
    lesson_key TEXT,
    question_json TEXT NOT NULL,
    created_at TEXT NOT NULL,
    PRIMARY KEY (chat_id, id)
);
CREATE INDEX IF NOT EXISTS idx_bank_subject ON question_bank(chat_id, subject_name);

CREATE TABLE IF NOT EXISTS focus_time (
    chat_id INTEGER NOT NULL,
    study_date TEXT NOT NULL,
    seconds INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (chat_id, study_date)
);
