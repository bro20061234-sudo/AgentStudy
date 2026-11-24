package com.studyagent;

import com.studyagent.ai.GeminiService;
import com.studyagent.model.*;
import com.studyagent.scheduler.StudyOptimizer;
import com.studyagent.calendar.CalendarPlanner;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LongScheduleTest {
    @Test void localScheduleAndGooglePlanBothSupportSeveralYears(){
        var start=LocalDate.of(2026,1,1);var end=LocalDate.of(2028,12,31);
        var entries=List.of(new TimetableEntry("Thứ 2","08:00","10:00","A","A1",""));
        var sessions=new StudyOptimizer(new GeminiService("")).generateSemesterSchedule(entries,start,end);
        assertTrue(sessions.size()>150);assertTrue(sessions.stream().allMatch(s->!LocalDate.parse(s.getScheduledDate()).isBefore(start)&&!LocalDate.parse(s.getScheduledDate()).isAfter(end)));
        var planned=assertDoesNotThrow(()->CalendarPlanner.plan(new CalendarPlanner.Snapshot(entries,sessions,start,end),start.atStartOfDay()));assertEquals(sessions.size()*4,planned.size());
    }
    @Test void reversedDateRangeIsStillRejected(){
        var day=LocalDate.of(2026,1,1);assertThrows(IllegalArgumentException.class,()->CalendarPlanner.plan(new CalendarPlanner.Snapshot(List.of(),List.of(),day,day.minusDays(1)),day.atStartOfDay()));
    }
}
