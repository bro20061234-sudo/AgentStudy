package com.studyagent;

import com.studyagent.model.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StudyProgressTest {
    StudySession session(String start,String end){var s=new StudySession("Thứ 2",start,end,"A","","");s.setScheduledDate("2026-11-02");return s;}
    LocalDateTime at(String time){return LocalDateTime.of(LocalDate.of(2026,11,2),LocalTime.parse(time));}
    @Test void unstartedSessionBecomesOverdueAtExactlyTenMinutesEvenWithoutRunningAJob(){
        var s=session("20:00","21:00");assertEquals(StudyProgress.Status.UPCOMING,StudyProgress.status(List.of(s),at("19:59")));
        assertEquals(StudyProgress.Status.DUE,StudyProgress.status(List.of(s),at("20:00")));assertEquals(StudyProgress.Status.DUE,StudyProgress.status(List.of(s),at("20:09:59")));
        assertEquals(StudyProgress.Status.OVERDUE,StudyProgress.status(List.of(s),at("20:10")));assertEquals(StudyProgress.Status.OVERDUE,StudyProgress.status(List.of(s),at("23:00")));
    }
    @Test void startedStudyCanContinuePastTenMinutesButUnfinishedStudyIsOverdueAtItsEnd(){
        var s=session("20:00","21:00");s.setStartedAt("2026-11-02T12:55:00Z");
        assertEquals(StudyProgress.Status.IN_PROGRESS,StudyProgress.status(List.of(s),at("20:30")));assertEquals(StudyProgress.Status.OVERDUE,StudyProgress.status(List.of(s),at("21:00")));
        s.setCompleted(true);assertEquals(StudyProgress.Status.COMPLETED,StudyProgress.status(List.of(s),at("23:00")));
    }
    @Test void earlyCompletionDoesNotInflateCompletedOverDueFractionAndCountsAtItsStart(){
        var due=session("20:00","20:30");due.setCompleted(true);var future=session("21:00","21:30");future.setCompleted(true);
        var now=StudyProgress.summary(List.of(due,future),at("20:15"));assertEquals(1,now.completedDue());assertEquals(1,now.due());assertEquals(1,now.completedEarly());
        var later=StudyProgress.summary(List.of(due,future),at("21:00"));assertEquals(2,later.completedDue());assertEquals(2,later.due());assertEquals(0,later.completedEarly());
    }
    @Test void mergedSessionsCountAsOneVisibleStudySession(){
        var a=session("20:00","20:30");var b=session("20:30","21:00");a.setCompleted(true);
        var partial=StudyProgress.summary(List.of(a,b),at("21:10"));assertEquals(1,partial.due());assertEquals(0,partial.completedDue());assertEquals(1,partial.overdue());
        b.setCompleted(true);assertEquals(1,StudyProgress.summary(List.of(a,b),at("21:10")).completedDue());
    }
}
