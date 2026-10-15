package com.studyagent.repository;

import com.studyagent.model.StudySession;
import com.studyagent.model.SubjectSyllabus;
import com.studyagent.model.TimetableEntry;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.text.Normalizer;

public class StudyRepository {

    public StudyRepository() {
        DatabaseManager.initializeDatabase();
    }

    public void saveTimetableEntries(long chatId, List<TimetableEntry> entries) {
        String deleteSql = "DELETE FROM timetables WHERE chat_id = ?";
        String insertSql = "INSERT INTO timetables (id, chat_id, day_of_week, start_time, end_time, subject_name, room, note, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement delStmt = conn.prepareStatement(deleteSql)) {
                delStmt.setLong(1, chatId);
                delStmt.executeUpdate();
            }
            try (PreparedStatement insStmt = conn.prepareStatement(insertSql)) {
                String now = LocalDateTime.now().toString();
                for (TimetableEntry entry : entries) {
                    String id = entry.getId() != null ? entry.getId() : UUID.randomUUID().toString();
                    entry.setId(id);
                    entry.setChatId(chatId);

                    insStmt.setString(1, id);
                    insStmt.setLong(2, chatId);
                    insStmt.setString(3, entry.getDayOfWeek());
                    insStmt.setString(4, entry.getStartTime());
                    insStmt.setString(5, entry.getEndTime());
                    insStmt.setString(6, entry.getSubjectName());
                    insStmt.setString(7, entry.getRoom());
                    insStmt.setString(8, entry.getNote());
                    insStmt.setString(9, now);
                    insStmt.addBatch();
                }
                insStmt.executeBatch();
            }
            conn.commit();
            System.out.printf("💾 [StudyRepository] Đã lưu %d lịch học chính khóa cho user %d.%n", entries.size(), chatId);
        } catch (SQLException e) {
            System.err.println("❌ Lỗi lưu Timetable: " + e.getMessage());
        }
    }

    public List<TimetableEntry> getTimetableEntries(long chatId) {
        List<TimetableEntry> list = new ArrayList<>();
        String sql = "SELECT * FROM timetables WHERE chat_id = ? ORDER BY day_of_week, start_time";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    TimetableEntry entry = new TimetableEntry();
                    entry.setId(rs.getString("id"));
                    entry.setChatId(rs.getLong("chat_id"));
                    entry.setDayOfWeek(rs.getString("day_of_week"));
                    entry.setStartTime(rs.getString("start_time"));
                    entry.setEndTime(rs.getString("end_time"));
                    entry.setSubjectName(rs.getString("subject_name"));
                    entry.setRoom(rs.getString("room"));
                    entry.setNote(rs.getString("note"));
                    list.add(entry);
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Lỗi tải Timetable: " + e.getMessage());
        }
        return list;
    }

    public void saveStudySessions(long chatId, List<StudySession> sessions) {
        String deleteSql = "DELETE FROM study_sessions WHERE chat_id = ?";
        String insertSql = "INSERT INTO study_sessions (id, chat_id, day_of_week, scheduled_date, start_time, end_time, subject_name, topic_overview, preparation_materials, is_reminded, is_completed, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement delStmt = conn.prepareStatement(deleteSql)) {
                delStmt.setLong(1, chatId);
                delStmt.executeUpdate();
            }
            try (PreparedStatement insStmt = conn.prepareStatement(insertSql)) {
                String now = LocalDateTime.now().toString();
                for (StudySession session : sessions) {
                    String id = session.getId() != null ? session.getId() : UUID.randomUUID().toString();
                    session.setId(id);
                    session.setChatId(chatId);

                    insStmt.setString(1, id);
                    insStmt.setLong(2, chatId);
                    insStmt.setString(3, session.getDayOfWeek());
                    insStmt.setString(4, session.getScheduledDate());
                    insStmt.setString(5, session.getStartTime());
                    insStmt.setString(6, session.getEndTime());
                    insStmt.setString(7, session.getSubjectName());
                    insStmt.setString(8, session.getTopicOverview());
                    insStmt.setString(9, session.getPreparationMaterials());
                    insStmt.setInt(10, session.isReminded() ? 1 : 0);
                    insStmt.setInt(11, session.isCompleted() ? 1 : 0);
                    insStmt.setString(12, now);
                    insStmt.addBatch();
                }
                insStmt.executeBatch();
            }
            conn.commit();
            System.out.printf("💾 [StudyRepository] Đã lưu %d phiên tự học cho user %d.%n", sessions.size(), chatId);
        } catch (SQLException e) {
            System.err.println("❌ Lỗi lưu StudySessions: " + e.getMessage());
        }
    }

    public List<StudySession> getStudySessions(long chatId) {
        List<StudySession> list = new ArrayList<>();
        String sql = "SELECT * FROM study_sessions WHERE chat_id = ?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapStudySession(rs));
                }
            }
            list.sort(Comparator.comparing(StudySession::getScheduledDate, Comparator.nullsLast(String::compareTo))
                    .thenComparingInt((StudySession s) -> dayOrder(s.getDayOfWeek()))
                    .thenComparing(StudySession::getStartTime, Comparator.nullsLast(String::compareTo))
                    .thenComparing(StudySession::getId, Comparator.nullsLast(String::compareTo)));
            assignSessionIndexes(list);
        } catch (SQLException e) {
            System.err.println("❌ Lỗi tải StudySessions: " + e.getMessage());
        }
        return list;
    }

    public List<StudySession> getAllUnremindedSessions() {
        List<StudySession> list = new ArrayList<>();
        String sql = "SELECT * FROM study_sessions WHERE is_reminded = 0";
        try (Connection conn = DatabaseManager.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapStudySession(rs));
            }
            list.sort(Comparator.comparingLong(StudySession::getChatId)
                    .thenComparing(StudySession::getScheduledDate, Comparator.nullsLast(String::compareTo))
                    .thenComparingInt(s -> dayOrder(s.getDayOfWeek()))
                    .thenComparing(StudySession::getStartTime, Comparator.nullsLast(String::compareTo))
                    .thenComparing(StudySession::getId, Comparator.nullsLast(String::compareTo)));
            assignSessionIndexes(list);
        } catch (SQLException e) {
            System.err.println("❌ Lỗi tải unreminded sessions: " + e.getMessage());
        }
        return list;
    }

    public void markSessionReminded(String sessionId) {
        String sql = "UPDATE study_sessions SET is_reminded = 1 WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Lỗi cập nhật trạng thái nhắc: " + e.getMessage());
        }
    }

    public void toggleStudySessionCompleted(String sessionId, boolean completed) {
        String sql = "UPDATE study_sessions SET is_completed = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, completed ? 1 : 0);
            pstmt.setString(2, sessionId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Lỗi cập nhật trạng thái hoàn thành: " + e.getMessage());
        }
    }

    public void updateStudySession(StudySession session) {
        String sql = "UPDATE study_sessions SET day_of_week = ?, start_time = ?, end_time = ?, subject_name = ?, topic_overview = ?, is_completed = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, session.getDayOfWeek());
            pstmt.setString(2, session.getStartTime());
            pstmt.setString(3, session.getEndTime());
            pstmt.setString(4, session.getSubjectName());
            pstmt.setString(5, session.getTopicOverview());
            pstmt.setInt(6, session.isCompleted() ? 1 : 0);
            pstmt.setString(7, session.getId());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Lỗi cập nhật StudySession: " + e.getMessage());
        }
    }

    public List<String> getDistinctSubjects(long chatId) {
        List<String> subjects = new ArrayList<>();
        String sql = "SELECT TRIM(subject_name) AS subject_name FROM timetables WHERE chat_id = ? " +
                "AND subject_name IS NOT NULL AND TRIM(subject_name) != '' " +
                "GROUP BY LOWER(TRIM(subject_name)) ORDER BY LOWER(TRIM(subject_name))";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String sub = rs.getString("subject_name");
                    if (sub != null && !sub.isBlank() && !subjects.contains(sub.trim())) {
                        subjects.add(sub.trim());
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Lỗi lấy danh sách môn học: " + e.getMessage());
        }
        return subjects;
    }

    private StudySession mapStudySession(ResultSet rs) throws SQLException {
        StudySession s = new StudySession();
        s.setId(rs.getString("id"));
        s.setChatId(rs.getLong("chat_id"));
        s.setDayOfWeek(rs.getString("day_of_week"));
        s.setScheduledDate(rs.getString("scheduled_date"));
        s.setStartTime(rs.getString("start_time"));
        s.setEndTime(rs.getString("end_time"));
        s.setSubjectName(rs.getString("subject_name"));
        s.setTopicOverview(rs.getString("topic_overview"));
        s.setPreparationMaterials(rs.getString("preparation_materials"));
        s.setReminded(rs.getInt("is_reminded") == 1);
        s.setCompleted(rs.getInt("is_completed") == 1);
        return s;
    }

    private static void assignSessionIndexes(List<StudySession> sessions) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (StudySession session : sessions) {
            String subject = session.getChatId() + ":" +
                    (session.getSubjectName() == null ? "" : session.getSubjectName().trim().toLowerCase(Locale.ROOT));
            int index = indexes.getOrDefault(subject, 0) + 1;
            indexes.put(subject, index);
            session.setSessionIndex(index);
        }
    }

    private static int dayOrder(String day) {
        if (day == null) return 8;
        String normalized = Normalizer.normalize(day.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        if (normalized.contains("chu nhat")) return 7;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("thu\\s*(\\d+)").matcher(normalized);
        if (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            return value >= 2 && value <= 7 ? value - 2 : 8;
        }
        String[] vietnameseDays = {"thu hai", "thu ba", "thu tu", "thu nam", "thu sau", "thu bay"};
        for (int i = 0; i < vietnameseDays.length; i++) {
            if (normalized.contains(vietnameseDays[i])) return i;
        }
        return 8;
    }

    public void saveSyllabus(SubjectSyllabus syllabus) {
        String sql = "INSERT OR REPLACE INTO syllabuses (id, chat_id, subject_name, overview, syllabus_content, exam_tips, recommended_materials, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            String id = syllabus.getId() != null ? syllabus.getId() : UUID.randomUUID().toString();
            syllabus.setId(id);

            pstmt.setString(1, id);
            pstmt.setLong(2, syllabus.getChatId());
            pstmt.setString(3, syllabus.getSubjectName());
            pstmt.setString(4, syllabus.getOverview());
            pstmt.setString(5, syllabus.getSyllabusContent());
            pstmt.setString(6, syllabus.getExamTips());
            pstmt.setString(7, syllabus.getRecommendedMaterials());
            pstmt.setString(8, LocalDateTime.now().toString());
            pstmt.executeUpdate();
            System.out.printf("💾 [StudyRepository] Đã lưu đề cương môn \"%s\" cho user %d.%n", syllabus.getSubjectName(), syllabus.getChatId());
        } catch (SQLException e) {
            System.err.println("❌ Lỗi lưu Syllabus: " + e.getMessage());
        }
    }

    public List<SubjectSyllabus> getAllSyllabuses(long chatId) {
        List<SubjectSyllabus> list = new ArrayList<>();
        String sql = "SELECT * FROM syllabuses WHERE chat_id = ? ORDER BY subject_name";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    SubjectSyllabus s = new SubjectSyllabus();
                    s.setId(rs.getString("id"));
                    s.setChatId(rs.getLong("chat_id"));
                    s.setSubjectName(rs.getString("subject_name"));
                    s.setOverview(rs.getString("overview"));
                    s.setSyllabusContent(rs.getString("syllabus_content"));
                    s.setExamTips(rs.getString("exam_tips"));
                    s.setRecommendedMaterials(rs.getString("recommended_materials"));
                    list.add(s);
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Lỗi tải Syllabuses: " + e.getMessage());
        }
        return list;
    }

    public boolean saveWeeklyCurricula(long chatId, String subjectName, List<com.studyagent.model.WeeklyCurriculum> list) {
        String delSql = "DELETE FROM weekly_curricula WHERE chat_id = ? AND LOWER(subject_name) = LOWER(?)";
        String insSql = "INSERT INTO weekly_curricula (id, chat_id, subject_name, week_number, chapter_title, detailed_syllabus, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement delStmt = conn.prepareStatement(delSql)) {
                delStmt.setLong(1, chatId);
                delStmt.setString(2, subjectName);
                delStmt.executeUpdate();
            }
            try (PreparedStatement insStmt = conn.prepareStatement(insSql)) {
                String now = LocalDateTime.now().toString();
                for (com.studyagent.model.WeeklyCurriculum c : list) {
                    String id = c.getId() != null ? c.getId() : UUID.randomUUID().toString();
                    insStmt.setString(1, id);
                    insStmt.setLong(2, chatId);
                    insStmt.setString(3, subjectName);
                    insStmt.setInt(4, c.getWeekNumber());
                    insStmt.setString(5, c.getChapterTitle());
                    insStmt.setString(6, c.getDetailedSyllabus());
                    insStmt.setString(7, now);
                    insStmt.addBatch();
                }
                insStmt.executeBatch();
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            System.err.println("❌ Lỗi lưu WeeklyCurricula: " + e.getMessage());
            return false;
        }
    }

    public List<com.studyagent.model.WeeklyCurriculum> getWeeklyCurricula(long chatId, String subjectName) {
        List<com.studyagent.model.WeeklyCurriculum> result = new ArrayList<>();
        String sql = "SELECT * FROM weekly_curricula WHERE chat_id = ? AND LOWER(subject_name) = LOWER(?) ORDER BY week_number";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, subjectName);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    com.studyagent.model.WeeklyCurriculum c = new com.studyagent.model.WeeklyCurriculum();
                    c.setId(rs.getString("id"));
                    c.setChatId(rs.getLong("chat_id"));
                    c.setSubjectName(rs.getString("subject_name"));
                    c.setWeekNumber(rs.getInt("week_number"));
                    c.setChapterTitle(rs.getString("chapter_title"));
                    c.setDetailedSyllabus(rs.getString("detailed_syllabus"));
                    result.add(c);
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Lỗi đọc WeeklyCurricula: " + e.getMessage());
        }
        return result;
    }

    public List<com.studyagent.model.WeeklyCurriculum> getAllWeeklyCurricula(long chatId) {
        List<com.studyagent.model.WeeklyCurriculum> result = new ArrayList<>();
        String sql = "SELECT * FROM weekly_curricula WHERE chat_id = ? ORDER BY subject_name, week_number";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    com.studyagent.model.WeeklyCurriculum c = new com.studyagent.model.WeeklyCurriculum();
                    c.setId(rs.getString("id"));
                    c.setChatId(rs.getLong("chat_id"));
                    c.setSubjectName(rs.getString("subject_name"));
                    c.setWeekNumber(rs.getInt("week_number"));
                    c.setChapterTitle(rs.getString("chapter_title"));
                    c.setDetailedSyllabus(rs.getString("detailed_syllabus"));
                    result.add(c);
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Lỗi đọc tất cả WeeklyCurricula: " + e.getMessage());
        }
        return result;
    }

    public void saveQuizQuestions(long chatId, String subjectName, String phase, int weekNumber, List<com.studyagent.model.QuizQuestion> questions) {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        boolean contest = "CONTEST".equalsIgnoreCase(phase);
        int storedWeekNumber = weekNumber;
        if (contest) {
            String nextContestSql = "SELECT COALESCE(MAX(week_number), 0) + 1 FROM quiz_questions WHERE chat_id = ? AND LOWER(subject_name) = LOWER(?) AND phase = 'CONTEST'";
            try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(nextContestSql)) {
                pstmt.setLong(1, chatId);
                pstmt.setString(2, subjectName);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) storedWeekNumber = rs.getInt(1);
                }
            } catch (SQLException e) {
                System.err.println("❌ Lỗi xác định số Contest tiếp theo: " + e.getMessage());
                return;
            }
        }
        String delSql = "DELETE FROM quiz_questions WHERE chat_id = ? AND LOWER(subject_name) = LOWER(?) AND phase = ? AND week_number = ?";
        String insSql = "INSERT INTO quiz_questions (id, chat_id, subject_name, week_number, phase, question, options_json, correct_answer, explanation, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            if (!contest) {
                try (PreparedStatement delStmt = conn.prepareStatement(delSql)) {
                    delStmt.setLong(1, chatId);
                    delStmt.setString(2, subjectName);
                    delStmt.setString(3, phase);
                    delStmt.setInt(4, weekNumber);
                    delStmt.executeUpdate();
                }
            }
            try (PreparedStatement insStmt = conn.prepareStatement(insSql)) {
                String now = LocalDateTime.now().toString();
                for (com.studyagent.model.QuizQuestion q : questions) {
                    String id = q.getId() != null ? q.getId() : UUID.randomUUID().toString();
                    q.setChatId(chatId);
                    q.setSubjectName(subjectName);
                    q.setPhase(phase);
                    q.setWeekNumber(storedWeekNumber);
                    insStmt.setString(1, id);
                    insStmt.setLong(2, chatId);
                    insStmt.setString(3, subjectName);
                    insStmt.setInt(4, storedWeekNumber);
                    insStmt.setString(5, phase);
                    insStmt.setString(6, q.getQuestion());
                    insStmt.setString(7, gson.toJson(q.getOptions()));
                    insStmt.setString(8, q.getCorrectAnswer());
                    insStmt.setString(9, q.getExplanation());
                    insStmt.setString(10, now);
                    insStmt.addBatch();
                }
                insStmt.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            System.err.println("❌ Lỗi lưu QuizQuestions: " + e.getMessage());
        }
    }

    public List<com.studyagent.model.QuizQuestion> getQuizQuestions(long chatId, String subjectName, String phase) {
        return getQuizQuestions(chatId, subjectName, phase, null);
    }

    public List<com.studyagent.model.QuizQuestion> getQuizQuestions(long chatId, String subjectName, String phase, Integer contestNumber) {
        List<com.studyagent.model.QuizQuestion> result = new ArrayList<>();
        com.google.gson.Gson gson = new com.google.gson.Gson();
        String sql = "SELECT * FROM quiz_questions WHERE chat_id = ? AND LOWER(subject_name) = LOWER(?) AND phase = ?" +
                (contestNumber == null ? "" : " AND week_number = ?") + " ORDER BY week_number, created_at, id";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, subjectName);
            pstmt.setString(3, phase);
            if (contestNumber != null) pstmt.setInt(4, contestNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapQuizQuestion(rs, gson));
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Lỗi đọc QuizQuestions: " + e.getMessage());
        }
        return result;
    }

    public Map<String, Map<Integer, List<com.studyagent.model.QuizQuestion>>> getAllContestsGroupedBySubject(long chatId) {
        Map<String, Map<Integer, List<com.studyagent.model.QuizQuestion>>> contests = new LinkedHashMap<>();
        String sql = "SELECT * FROM quiz_questions WHERE chat_id = ? AND phase = 'CONTEST' " +
                "ORDER BY subject_name COLLATE NOCASE, week_number, created_at, id";
        com.google.gson.Gson gson = new com.google.gson.Gson();
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    com.studyagent.model.QuizQuestion question = mapQuizQuestion(rs, gson);
                    contests.computeIfAbsent(question.getSubjectName(), ignored -> new TreeMap<>())
                            .computeIfAbsent(question.getWeekNumber(), ignored -> new ArrayList<>())
                            .add(question);
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Lỗi tải Contest đã lưu: " + e.getMessage());
        }
        return contests;
    }

    private static com.studyagent.model.QuizQuestion mapQuizQuestion(ResultSet rs, com.google.gson.Gson gson) throws SQLException {
        com.studyagent.model.QuizQuestion question = new com.studyagent.model.QuizQuestion();
        question.setId(rs.getString("id"));
        question.setChatId(rs.getLong("chat_id"));
        question.setSubjectName(rs.getString("subject_name"));
        question.setWeekNumber(rs.getInt("week_number"));
        question.setPhase(rs.getString("phase"));
        question.setQuestion(rs.getString("question"));
        java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<List<String>>() {}.getType();
        question.setOptions(gson.fromJson(rs.getString("options_json"), listType));
        question.setCorrectAnswer(rs.getString("correct_answer"));
        question.setExplanation(rs.getString("explanation"));
        return question;
    }

    public static class TextbookItem {
        private String id;
        private long chatId;
        private String subjectName;
        private String fileName;
        private String content;
        private String createdAt;

        public TextbookItem(String id, long chatId, String subjectName, String fileName, String content, String createdAt) {
            this.id = id;
            this.chatId = chatId;
            this.subjectName = subjectName;
            this.fileName = fileName;
            this.content = content;
            this.createdAt = createdAt;
        }

        public String getId() { return id; }
        public long getChatId() { return chatId; }
        public String getSubjectName() { return subjectName; }
        public String getFileName() { return fileName; }
        public String getContent() { return content; }
        public String getCreatedAt() { return createdAt; }
    }

    public boolean saveTextbook(long chatId, String subjectName, String fileName, String content) {
        String delSql = "DELETE FROM textbooks WHERE chat_id = ? AND LOWER(subject_name) = LOWER(?)";
        String delCurriculaSql = "DELETE FROM weekly_curricula WHERE chat_id = ? AND LOWER(subject_name) = LOWER(?)";
        String insSql = "INSERT INTO textbooks (id, chat_id, subject_name, file_name, content, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            boolean contentChanged = true;
            try (PreparedStatement existingStmt = conn.prepareStatement(
                    "SELECT content FROM textbooks WHERE chat_id = ? AND LOWER(subject_name) = LOWER(?) ORDER BY created_at DESC LIMIT 1")) {
                existingStmt.setLong(1, chatId);
                existingStmt.setString(2, subjectName);
                try (ResultSet rs = existingStmt.executeQuery()) {
                    if (rs.next()) {
                        contentChanged = !java.util.Objects.equals(rs.getString("content"), content);
                    }
                }
            }
            try (PreparedStatement delStmt = conn.prepareStatement(delSql)) {
                delStmt.setLong(1, chatId);
                delStmt.setString(2, subjectName);
                delStmt.executeUpdate();
            }
            if (contentChanged) {
                try (PreparedStatement delCurriculaStmt = conn.prepareStatement(delCurriculaSql)) {
                    delCurriculaStmt.setLong(1, chatId);
                    delCurriculaStmt.setString(2, subjectName);
                    delCurriculaStmt.executeUpdate();
                }
            }
            try (PreparedStatement insStmt = conn.prepareStatement(insSql)) {
                insStmt.setString(1, UUID.randomUUID().toString());
                insStmt.setLong(2, chatId);
                insStmt.setString(3, subjectName);
                insStmt.setString(4, fileName);
                insStmt.setString(5, content);
                insStmt.setString(6, LocalDateTime.now().toString());
                insStmt.executeUpdate();
            }
            conn.commit();
            System.out.printf("💾 [StudyRepository] Đã lưu toàn văn giáo trình môn \"%s\" cho user %d.%n", subjectName, chatId);
            return true;
        } catch (SQLException e) {
            System.err.println("❌ Lỗi lưu giáo trình: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteTextbook(long chatId, String subjectName) {
        String deleteCurriculaSql = "DELETE FROM weekly_curricula WHERE chat_id = ? AND LOWER(subject_name) = LOWER(?)";
        String deleteTextbookSql = "DELETE FROM textbooks WHERE chat_id = ? AND LOWER(subject_name) = LOWER(?)";
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement curriculaStmt = conn.prepareStatement(deleteCurriculaSql);
                 PreparedStatement textbookStmt = conn.prepareStatement(deleteTextbookSql)) {
                curriculaStmt.setLong(1, chatId);
                curriculaStmt.setString(2, subjectName);
                curriculaStmt.executeUpdate();

                textbookStmt.setLong(1, chatId);
                textbookStmt.setString(2, subjectName);
                int deleted = textbookStmt.executeUpdate();
                conn.commit();
                return deleted > 0;
            }
        } catch (SQLException e) {
            System.err.println("❌ Lỗi xóa giáo trình: " + e.getMessage());
            return false;
        }
    }

    public TextbookItem getTextbook(long chatId, String subjectName) {
        String sql = "SELECT * FROM textbooks WHERE chat_id = ? AND LOWER(subject_name) = LOWER(?) ORDER BY created_at DESC LIMIT 1";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, subjectName);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new TextbookItem(
                            rs.getString("id"),
                            rs.getLong("chat_id"),
                            rs.getString("subject_name"),
                            rs.getString("file_name"),
                            rs.getString("content"),
                            rs.getString("created_at")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Lỗi đọc giáo trình: " + e.getMessage());
        }
        return null;
    }

    public List<TextbookItem> getAllTextbooks(long chatId) {
        List<TextbookItem> list = new ArrayList<>();
        String sql = "SELECT * FROM textbooks WHERE chat_id = ? ORDER BY subject_name";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new TextbookItem(
                            rs.getString("id"),
                            rs.getLong("chat_id"),
                            rs.getString("subject_name"),
                            rs.getString("file_name"),
                            rs.getString("content"),
                            rs.getString("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Lỗi đọc danh sách giáo trình: " + e.getMessage());
        }
        return list;
    }

    public List<String> getAllContestSubjects(long chatId) {
        List<String> list = new ArrayList<>();
        String sql = "SELECT DISTINCT subject_name FROM quiz_questions WHERE chat_id = ? ORDER BY subject_name";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String sub = rs.getString("subject_name");
                    if (sub != null && !sub.isBlank() && !list.contains(sub.trim())) {
                        list.add(sub.trim());
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Lỗi đọc môn học có Contest: " + e.getMessage());
        }
        return list;
    }
}
