package com.studyagent.repository;

import com.google.gson.Gson;
import com.studyagent.model.QuizQuestion;
import java.sql.*;
import java.util.*;
import java.time.LocalDateTime;

/** Versioned review data, separate from legacy contests. SQL failures reach the UI. */
public class ReviewRepository {
    private final Gson gson = new Gson();
    private final String databaseUrl;
    public record State(List<QuizQuestion> questions, int[] answers, boolean submitted, boolean completed) {}
    public record Attempt(String id, String completedAt, int correct, int total) {}

    public ReviewRepository() {
        this(null);
    }

    public ReviewRepository(String databaseUrl) {
        this.databaseUrl = databaseUrl;
        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS lesson_reviews (chat_id INTEGER NOT NULL, lesson_key TEXT NOT NULL, questions_json TEXT NOT NULL, answers_json TEXT NOT NULL, submitted INTEGER NOT NULL DEFAULT 0, completed INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(chat_id, lesson_key))");
            s.execute("CREATE TABLE IF NOT EXISTS lesson_review_attempts (id TEXT PRIMARY KEY, chat_id INTEGER NOT NULL, lesson_key TEXT NOT NULL, questions_json TEXT NOT NULL, answers_json TEXT NOT NULL, correct INTEGER NOT NULL, total INTEGER NOT NULL, completed_at TEXT NOT NULL)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_review_history ON lesson_review_attempts(chat_id, lesson_key, completed_at)");
            s.execute("CREATE TABLE IF NOT EXISTS question_bank(id TEXT NOT NULL,chat_id INTEGER NOT NULL,subject_name TEXT NOT NULL,lesson_key TEXT,question_json TEXT NOT NULL,created_at TEXT NOT NULL,PRIMARY KEY(chat_id,id))");
            s.execute("CREATE INDEX IF NOT EXISTS idx_bank_subject ON question_bank(chat_id,subject_name)");
        } catch (SQLException e) { throw new IllegalStateException("Không thể khởi tạo dữ liệu ôn tập", e); }
    }

    private Connection connection() throws SQLException {
        return databaseUrl == null ? DatabaseManager.getConnection() : DriverManager.getConnection(databaseUrl);
    }

    public State load(long chatId, String key) {
        try (Connection c = connection(); PreparedStatement s = c.prepareStatement("SELECT * FROM lesson_reviews WHERE chat_id=? AND lesson_key=?")) {
            s.setLong(1, chatId); s.setString(2, key);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) return new State(List.of(), new int[0], false, false);
                return new State(Arrays.asList(gson.fromJson(r.getString("questions_json"), QuizQuestion[].class)),
                        gson.fromJson(r.getString("answers_json"), int[].class), r.getBoolean("submitted"), r.getBoolean("completed"));
            }
        } catch (SQLException e) { throw new IllegalStateException("Không thể tải bài ôn tập", e); }
    }

    public void save(long chatId, String key, State state) {
        try (Connection c = connection()) { write(c, chatId, key, state); }
        catch (SQLException e) { throw new IllegalStateException("Không thể lưu tiến độ ôn tập", e); }
    }

    public void saveCompletion(long chatId, String key, State state, String sessionId) {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                write(c, chatId, key, state);
                if (sessionId != null) {
                    try (PreparedStatement s = c.prepareStatement("UPDATE study_sessions SET is_completed=? WHERE id=? AND chat_id=?")) {
                        s.setBoolean(1, state.completed()); s.setString(2, sessionId); s.setLong(3, chatId);
                        if (s.executeUpdate() != 1) throw new SQLException("Buổi học trên lịch đã thay đổi. Hãy làm mới lộ trình.");
                    }
                }
                c.commit();
            } catch (SQLException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new IllegalStateException("Không thể cập nhật tiến độ và lịch học", e); }
    }

    private void write(Connection c, long chatId, String key, State state) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("INSERT INTO lesson_reviews(chat_id,lesson_key,questions_json,answers_json,submitted,completed) VALUES(?,?,?,?,?,?) ON CONFLICT(chat_id,lesson_key) DO UPDATE SET questions_json=excluded.questions_json,answers_json=excluded.answers_json,submitted=excluded.submitted,completed=excluded.completed")) {
            s.setLong(1, chatId); s.setString(2, key); s.setString(3, gson.toJson(state.questions()));
            s.setString(4, gson.toJson(state.answers())); s.setBoolean(5, state.submitted()); s.setBoolean(6, state.completed()); s.executeUpdate();
        }
    }

    public void submit(long chatId, String key, State state) {
        submit(chatId,key,state,state);
    }
    public void submit(long chatId,String key,State state,State attempt) {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                write(c, chatId, key, state);
                try (PreparedStatement s = c.prepareStatement("INSERT INTO lesson_review_attempts VALUES(?,?,?,?,?,?,?,?)")) {
                    s.setString(1, UUID.randomUUID().toString()); s.setLong(2, chatId); s.setString(3, key);
                    s.setString(4, gson.toJson(attempt.questions())); s.setString(5, gson.toJson(attempt.answers()));
                    s.setInt(6, score(attempt)); s.setInt(7, attempt.questions().size()); s.setString(8, LocalDateTime.now().toString()); s.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new IllegalStateException("Không thể lưu kết quả bài làm", e); }
    }

    public List<Attempt> history(long chatId, String key) {
        List<Attempt> result = new ArrayList<>();
        try (Connection c = connection(); PreparedStatement s = c.prepareStatement("SELECT id,completed_at,correct,total FROM lesson_review_attempts WHERE chat_id=? AND lesson_key=? ORDER BY completed_at DESC LIMIT 100")) {
            s.setLong(1, chatId); s.setString(2, key);
            try (ResultSet r = s.executeQuery()) { while (r.next()) result.add(new Attempt(r.getString(1),r.getString(2), r.getInt(3), r.getInt(4))); }
        } catch (SQLException e) { throw new IllegalStateException("Không thể tải lịch sử", e); }
        return result;
    }

    public State attempt(long chatId,String id){
        try(Connection c=connection();PreparedStatement s=c.prepareStatement("SELECT questions_json,answers_json FROM lesson_review_attempts WHERE chat_id=? AND id=?")){
            s.setLong(1,chatId);s.setString(2,id);try(ResultSet r=s.executeQuery()){if(!r.next())throw new IllegalArgumentException("Không tìm thấy bài làm.");return new State(Arrays.asList(gson.fromJson(r.getString(1),QuizQuestion[].class)),gson.fromJson(r.getString(2),int[].class),true,false);}
        }catch(SQLException e){throw new IllegalStateException("Không tải được bài làm",e);}
    }
    public void addToBank(long chatId,String subject,String lessonKey,List<QuizQuestion> questions){
        if(questions.isEmpty())return;validate(questions);
        try(Connection c=connection()){
            c.setAutoCommit(false);try(PreparedStatement s=c.prepareStatement("INSERT OR IGNORE INTO question_bank VALUES(?,?,?,?,?,?)")){
                for(var q:questions){String id=questionId(subject,q);q.setId(id);q.setSubjectName(subject);q.setChatId(chatId);
                    s.setString(1,id);s.setLong(2,chatId);s.setString(3,subject.trim());s.setString(4,lessonKey);s.setString(5,gson.toJson(q));s.setString(6,LocalDateTime.now().toString());s.addBatch();}s.executeBatch();c.commit();
            }catch(SQLException e){c.rollback();throw e;}
        }catch(SQLException e){throw new IllegalStateException("Không lưu được ngân hàng câu hỏi",e);}
    }
    public List<QuizQuestion> bank(long chatId,String subject){
        // Import existing practice questions, including sets created before the question bank existed.
        List<Map.Entry<String,List<QuizQuestion>>> old=new ArrayList<>();
        try(Connection c=connection();PreparedStatement s=c.prepareStatement("SELECT lesson_key,questions_json FROM lesson_reviews WHERE chat_id=? AND lesson_key NOT LIKE 'EXAM:%'")){
            s.setLong(1,chatId);try(ResultSet r=s.executeQuery()){while(r.next()){
                List<QuizQuestion> matching=Arrays.stream(gson.fromJson(r.getString(2),QuizQuestion[].class)).filter(q->q.getSubjectName()!=null&&q.getSubjectName().trim().equalsIgnoreCase(subject.trim())).toList();
                if(!matching.isEmpty())old.add(Map.entry(r.getString(1),matching));
            }}
        }catch(SQLException e){throw new IllegalStateException("Không đọc được câu hỏi cũ",e);}
        for(var entry:old)addToBank(chatId,subject,entry.getKey(),entry.getValue());
        List<QuizQuestion> result=new ArrayList<>();
        try(Connection c=connection();PreparedStatement s=c.prepareStatement("SELECT question_json FROM question_bank WHERE chat_id=? AND LOWER(TRIM(subject_name))=LOWER(TRIM(?)) ORDER BY created_at,id")){
            s.setLong(1,chatId);s.setString(2,subject);try(ResultSet r=s.executeQuery()){while(r.next())result.add(gson.fromJson(r.getString(1),QuizQuestion.class));}
        }catch(SQLException e){throw new IllegalStateException("Không tải được ngân hàng câu hỏi",e);}return result;
    }
    public static String questionId(String subject,QuizQuestion question){
        try{String data=new Gson().toJson(List.of(subject.trim().toLowerCase(Locale.ROOT),question.getQuestion().trim().toLowerCase(Locale.ROOT),question.getOptions(),correctIndex(question)));
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public static String examKey(String subject){return "EXAM:"+subject.trim().toLowerCase(Locale.ROOT);}

    public static int correctIndex(QuizQuestion q) {
        if (q.getOptions() == null || q.getCorrectAnswer() == null) return -1;
        String answer = q.getCorrectAnswer().trim();
        for (int i = 0; i < q.getOptions().size(); i++) if (answer.equalsIgnoreCase(q.getOptions().get(i).trim())) return i;
        if (answer.matches("(?i)^[A-D](?:[.():\\s].*)?$")) return Character.toUpperCase(answer.charAt(0)) - 'A';
        return -1;
    }

    public static void validate(List<QuizQuestion> questions) {
        if (questions == null || questions.isEmpty()) throw new IllegalArgumentException("AI chưa trả về câu hỏi hợp lệ. Hãy thử lại.");
        for (QuizQuestion q : questions) {
            if (q == null || q.getQuestion() == null || q.getQuestion().isBlank() || q.getOptions() == null
                    || q.getOptions().size() != 4 || q.getOptions().stream().anyMatch(o -> o == null || o.isBlank())
                    || new HashSet<>(q.getOptions()).size() != 4 || correctIndex(q) < 0 || correctIndex(q) >= 4)
                throw new IllegalArgumentException("Bộ câu hỏi chưa đủ nội dung hoặc đáp án hợp lệ. Hãy thử tạo lại.");
        }
    }

    public static int score(State state) {
        int result = 0;
        for (int i = 0; i < state.questions().size(); i++)
            if (i < state.answers().length && state.answers()[i] >= 0 && state.answers()[i] == correctIndex(state.questions().get(i))) result++;
        return result;
    }
}
