package com.studyagent;

import com.studyagent.ai.GeminiService;
import com.studyagent.model.*;
import com.studyagent.scheduler.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StudyPolicyTest {
    final StudyOptimizer optimizer=new StudyOptimizer(new GeminiService(""));
    TimetableEntry entry(String day,String start,String end,String subject,String note){return new TimetableEntry(day,start,end,subject,"A1",note);}
    int minutes(List<StudySession> sessions){return sessions.stream().mapToInt(s->StudyOptimizer.parseMinutes(s.getEndTime())-StudyOptimizer.parseMinutes(s.getStartTime())).sum();}
    @Test void mergesSameSubjectDemandWithoutCountingBreaksAsPeriods(){
        var source=List.of(entry("Thứ 2","07:00","07:50","A","1 tiết"),entry("Thứ 2","08:00","08:50","A","1 tiết"));
        var blocks=ScheduleBlocks.mergeClasses(source);
        assertEquals(1,blocks.size());assertEquals("08:50",blocks.getFirst().getEndTime());assertEquals("07:50",source.getFirst().getEndTime());
        var study=optimizer.generateOptimalSchedule(source);
        assertEquals(1,study.size());assertEquals("20:00",study.getFirst().getStartTime());assertEquals("20:30",study.getFirst().getEndTime());
    }
    @Test void separateSubjectsAndDistantClassesStaySeparate(){
        assertEquals(3,ScheduleBlocks.mergeClasses(List.of(entry("Thứ 2","07:00","08:00","A",""),entry("Thứ 2","08:10","09:00","B",""),entry("Thứ 2","09:30","10:00","B",""))).size());
    }
    @Test void splitsLargeDemandAcrossNightsAndNeverDropsMinutes(){
        var sessions=optimizer.generateOptimalSchedule(List.of(entry("Thứ 2","07:00","17:00","A","12 tiết")));
        assertEquals(180,minutes(sessions));assertEquals(3,sessions.size());assertEquals("Thứ 4",sessions.getLast().getDayOfWeek());
        assertTrue(sessions.stream().allMatch(s->StudyOptimizer.parseMinutes(s.getEndTime())-StudyOptimizer.parseMinutes(s.getStartTime())<=60));
        assertTrue(sessions.stream().allMatch(s->StudyOptimizer.parseMinutes(s.getStartTime())>=1200&&StudyOptimizer.parseMinutes(s.getEndTime())<=1320));
    }
    @Test void eveningClassDoesNotContributeDemand(){
        var sessions=optimizer.generateOptimalSchedule(List.of(entry("Thứ 2","20:00","22:00","A","2 tiết")));
        assertTrue(sessions.isEmpty());
    }
    @Test void classesAtOrAfterEighteenNeverGenerateStudyEvenWithPeriodNotes(){
        var sessions=optimizer.generateOptimalSchedule(List.of(entry("Thứ 2","18:00","21:30","A","4 tiết"),entry("Thứ 3","19:00","20:00","B","1 tiết")));
        assertTrue(sessions.isEmpty());
    }
    @Test void eveningClassStillBlocksStudyGeneratedByDaytimeClasses(){
        var sessions=optimizer.generateOptimalSchedule(List.of(entry("Thứ 2","08:00","10:00","A","2 tiết"),entry("Thứ 2","18:00","21:00","B","3 tiết")));
        assertEquals(1,sessions.size());assertEquals("A",sessions.getFirst().getSubjectName());assertEquals("21:00",sessions.getFirst().getStartTime());assertEquals(30,minutes(sessions));
    }
    @Test void mixedDayAndEveningClassesOfSameSubjectOnlyCountDaytimePeriods(){
        var sessions=optimizer.generateOptimalSchedule(List.of(entry("Thứ 2","08:00","10:00","A","2 tiết"),entry("Thứ 2","18:00","21:00","A","3 tiết")));
        assertEquals(30,minutes(sessions));
    }
    @Test void classCrossingEighteenCountsOnlyPeriodsStartingBeforeCutoff(){
        var source=entry("Thứ 2","17:00","19:00","A","2 tiết");
        assertEquals(15,minutes(optimizer.generateOptimalSchedule(List.of(source))));
        assertEquals("19:00",source.getEndTime());assertEquals("2 tiết",source.getNote());
    }
    @Test void semesterNeverPlacesStudyOutsideSelectedDates(){
        var start=LocalDate.of(2026,10,26);var end=start.plusDays(1);
        var sessions=optimizer.generateSemesterSchedule(List.of(entry("Thứ 2","07:00","17:00","A","8 tiết")),start,end);
        assertEquals(120,minutes(sessions));assertTrue(sessions.stream().allMatch(s->!LocalDate.parse(s.getScheduledDate()).isAfter(end)));
        assertThrows(IllegalStateException.class,()->optimizer.generateSemesterSchedule(List.of(entry("Thứ 2","07:00","17:00","A","12 tiết")),start,start));
    }
    @Test void spilloverAndNewDemandShareOneDailySubjectLimit(){
        var sessions=optimizer.generateOptimalSchedule(List.of(entry("Thứ 2","07:00","17:00","A","6 tiết"),entry("Thứ 3","07:00","10:00","a","3 tiết")));
        Map<String,Integer> totals=new HashMap<>();for(var s:sessions)totals.merge(s.getDayOfWeek()+s.getSubjectName().toLowerCase(Locale.ROOT),StudyOptimizer.parseMinutes(s.getEndTime())-StudyOptimizer.parseMinutes(s.getStartTime()),Integer::sum);
        assertEquals(135,minutes(sessions));assertTrue(totals.values().stream().allMatch(n->n<=60));assertEquals(3,totals.size());
    }
    @Test void twoSubjectsCanEachUseOneHourInTheSameEvening(){
        var sessions=optimizer.generateOptimalSchedule(List.of(entry("Thứ 2","07:00","11:00","A","4 tiết"),entry("Thứ 2","13:00","17:00","B","4 tiết")));
        assertEquals(2,sessions.size());assertEquals("20:00",sessions.getFirst().getStartTime());assertEquals("22:00",sessions.getLast().getEndTime());assertEquals(120,minutes(sessions));
    }
}
