package com.studyagent;

import com.studyagent.model.QuizQuestion;
import com.studyagent.model.WeeklyCurriculum;
import com.studyagent.repository.ReviewRepository;
import com.studyagent.ui.LessonReviewPanel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LessonReviewTest {
    @TempDir Path temp;
    private ReviewRepository repository() { return new ReviewRepository("jdbc:sqlite:" + temp.resolve("review.db")); }
    private QuizQuestion question(String answer) {
        return new QuizQuestion("Đặc điểm của đóng gói?", List.of("A. Che giấu dữ liệu", "B. Sao chép", "C. Kế thừa", "D. Biên dịch"), answer, "Bảo vệ trạng thái nội bộ.");
    }

    @Test void draftSurvivesRestartAndIsIsolatedByUserAndLesson() {
        var r = repository();
        r.save(1, "lesson-a", new ReviewRepository.State(List.of(question("A")), new int[]{2}, false, true));
        var loaded = repository().load(1, "lesson-a");
        assertEquals(2, loaded.answers()[0]); assertTrue(loaded.completed()); assertFalse(loaded.submitted());
        assertEquals("Đặc điểm của đóng gói?", loaded.questions().get(0).getQuestion());
        assertTrue(r.load(2, "lesson-a").questions().isEmpty());
        assertTrue(r.load(1, "lesson-b").questions().isEmpty());
    }

    @Test void submissionAndRetryPreserveHistoryAndCompletionSeparately() {
        var r = repository(); var questions = List.of(question("A"), question("B"));
        var first = new ReviewRepository.State(questions, new int[]{0, 0}, true, false);
        r.submit(1, "lesson", first);
        assertEquals(1, r.history(1, "lesson").get(0).correct()); assertFalse(r.load(1, "lesson").completed());
        r.save(1, "lesson", new ReviewRepository.State(questions, new int[]{-1, -1}, false, true));
        assertEquals(1, r.history(1, "lesson").size());
        r.submit(1, "lesson", new ReviewRepository.State(questions, new int[]{0, 1}, true, true));
        assertEquals(2, r.history(1, "lesson").size()); assertTrue(r.load(1, "lesson").completed());
        assertEquals(2, ReviewRepository.score(r.load(1, "lesson")));
    }

    @Test void versionKeyChangesWithTextbookAndLessonButNotDatabaseId() {
        var plan = new WeeklyCurriculum(1,"OOP",1,"Đóng gói","Getter, setter và quyền truy cập");
        String first = LessonReviewPanel.lessonKey("OOP", "Giáo trình 1", plan);
        plan.setId("new-id"); assertEquals(first, LessonReviewPanel.lessonKey(" oop ", "Giáo trình 1", plan));
        assertNotEquals(first, LessonReviewPanel.lessonKey("OOP", "Giáo trình 2", plan));
        plan.setDetailedSyllabus("Nội dung cập nhật");
        assertNotEquals(first, LessonReviewPanel.lessonKey("OOP", "Giáo trình 1", plan));
    }

    @Test void gradingHandlesLettersAndFullOptionTextWithoutTreatingUnansweredAsCorrect() {
        assertEquals(0, ReviewRepository.correctIndex(question("A")));
        assertEquals(0, ReviewRepository.correctIndex(question("A. Che giấu dữ liệu")));
        assertEquals(0, ReviewRepository.correctIndex(question("a) Che giấu dữ liệu")));
        assertEquals(-1, ReviewRepository.correctIndex(question("Không rõ")));
        assertEquals(0, ReviewRepository.score(new ReviewRepository.State(List.of(question("A")), new int[]{-1}, false, false)));
    }

    @Test void invalidGeneratedQuestionsAreRejectedBeforeSaving() {
        assertDoesNotThrow(() -> ReviewRepository.validate(List.of(question("A"))));
        assertThrows(IllegalArgumentException.class, () -> ReviewRepository.validate(List.of()));
        assertThrows(IllegalArgumentException.class, () -> ReviewRepository.validate(List.of(question("Unknown"))));
        var broken = question("A"); broken.setOptions(List.of("A. X", "A. X", "C. Z", "D. Y"));
        assertThrows(IllegalArgumentException.class, () -> ReviewRepository.validate(List.of(broken)));
    }

    @Test void scheduleAndReviewCompletionCommitTogetherAndRollbackOnMissingSession() throws Exception {
        var r = repository(); String url = "jdbc:sqlite:" + temp.resolve("review.db");
        try (var c = java.sql.DriverManager.getConnection(url); var s = c.createStatement()) {
            s.execute("CREATE TABLE study_sessions(id TEXT PRIMARY KEY, chat_id INTEGER, is_completed INTEGER)");
            s.execute("INSERT INTO study_sessions VALUES('session',1,0)");
        }
        var done = new ReviewRepository.State(List.of(), new int[0], false, true);
        r.saveCompletion(1, "lesson", done, "session");
        assertTrue(r.load(1, "lesson").completed());
        try (var c = java.sql.DriverManager.getConnection(url); var s = c.createStatement(); var rows = s.executeQuery("SELECT is_completed FROM study_sessions")) {
            assertTrue(rows.next()); assertEquals(1, rows.getInt(1));
        }
        assertThrows(IllegalStateException.class, () -> r.saveCompletion(2, "other", done, "session"));
        assertFalse(r.load(2, "other").completed());
        assertThrows(IllegalStateException.class, () -> r.saveCompletion(1, "missing", done, "gone"));
        assertFalse(r.load(1, "missing").completed());
    }
}
