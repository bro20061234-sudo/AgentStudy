package com.studyagent.repository;

import com.studyagent.model.StudySession;
import com.studyagent.model.SubjectSyllabus;
import com.studyagent.model.TimetableEntry;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
        String insertSql = "INSERT INTO study_sessions (id, chat_id, day_of_week, scheduled_date, start_time, end_time, subject_name, topic_overview, preparation_materials, is_reminded, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
                    insStmt.setString(11, now);
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
        String sql = "SELECT * FROM study_sessions WHERE chat_id = ? ORDER BY day_of_week, start_time";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapStudySession(rs));
                }
            }
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
        return s;
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
}
