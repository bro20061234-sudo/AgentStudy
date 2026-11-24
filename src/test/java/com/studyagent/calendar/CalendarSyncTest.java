package com.studyagent.calendar;

import com.google.gson.*;
import com.studyagent.model.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CalendarSyncTest {
    final LocalDate date=LocalDate.of(2026,11,2);
    StudySession study(){var s=new StudySession("Thứ 2","20:00","21:00","A","","");s.setScheduledDate(date.toString());s.setId("local-id");return s;}
    CalendarPlanner.Snapshot snapshot(StudySession study){return new CalendarPlanner.Snapshot(List.of(new TimetableEntry("Thứ 2","08:00","10:00","B","Room 1","")),List.of(study),date,date);}
    List<CalendarPlanner.Planned> plan(StudySession s,String time){return CalendarPlanner.plan(snapshot(s),date.atTime(LocalTime.parse(time)));}
    static String kind(CalendarPlanner.Planned e){return e.body().getAsJsonObject("extendedProperties").getAsJsonObject("private").get("kind").getAsString();}
    static CalendarPlanner.Planned event(List<CalendarPlanner.Planned> events,String kind){return events.stream().filter(e->kind(e).equals(kind)).findFirst().orElseThrow();}
    static int reminder(CalendarPlanner.Planned e){return e.body().getAsJsonObject("reminders").getAsJsonArray("overrides").get(0).getAsJsonObject().get("minutes").getAsInt();}
    @Test void uploadsTenMinuteReminderAndBothLateAlertsAheadOfTime(){
        var planned=plan(study(),"18:00");assertEquals(4,planned.size());assertEquals(10,reminder(event(planned,"class")));assertEquals(10,reminder(event(planned,"study")));
        assertEquals(0,reminder(event(planned,"late5")));assertEquals(0,reminder(event(planned,"late10")));
        assertEquals("2026-11-02T20:05:00+07:00",event(planned,"late5").body().getAsJsonObject("start").get("dateTime").getAsString());
        assertEquals("2026-11-02T20:10:00+07:00",event(planned,"late10").body().getAsJsonObject("start").get("dateTime").getAsString());
        assertTrue(planned.stream().allMatch(e->e.id().matches("[0-9a-f]{64}")));
    }
    @Test void enteringStudyRemovesBothLateAlertsButDoesNotCompleteStudy(){
        var s=study();s.setStartedAt("2026-11-02T12:00:00Z");var planned=plan(s,"19:00");assertEquals(2,planned.size());assertFalse(s.isCompleted());assertEquals(10,reminder(event(planned,"study")));
    }
    @Test void earlyCompletionRemovesEveryStudyReminderAndRetryDoesNotCreateDuplicates()throws Exception{
        var s=study();var remote=new Fake();var reconcile=new CalendarReconciler();var before=plan(s,"18:00");reconcile.sync(remote,"own-calendar",before);assertEquals(4,remote.events.size());
        s.setCompleted(true);var after=plan(s,"18:00");var outcome=reconcile.sync(remote,"own-calendar",after);assertEquals(2,outcome.removed());
        assertEquals(0,event(after,"study").body().getAsJsonObject("reminders").getAsJsonArray("overrides").size());
        var repeated=reconcile.sync(remote,"own-calendar",after);assertEquals(0,repeated.saved());assertEquals(0,repeated.removed());assertEquals(2,remote.events.size());
    }
    @Test void localIdChangesDoNotDuplicateCalendarEventsAndUnrelatedEventsArePreserved()throws Exception{
        var s=study();var first=plan(s,"18:00");s.setId("regenerated-id");assertEquals(first.stream().map(CalendarPlanner.Planned::id).toList(),plan(s,"18:00").stream().map(CalendarPlanner.Planned::id).toList());
        var remote=new Fake();remote.events.put("unrelated",new JsonObject());new CalendarReconciler().sync(remote,"own",first);assertTrue(remote.events.containsKey("unrelated"));
    }
    @Test void reconnectingLateDoesNotRecreateExpiredFollowUpAlerts(){var planned=plan(study(),"20:11");assertEquals(2,planned.size());assertTrue(planned.stream().noneMatch(e->kind(e).startsWith("late")));}
    @Test void failedCancellationCanBeRetriedWithoutLosingOtherEvents()throws Exception{
        var s=study();var remote=new Fake();var reconcile=new CalendarReconciler();reconcile.sync(remote,"own",plan(s,"18:00"));s.setCompleted(true);remote.failDelete=true;
        assertThrows(Exception.class,()->reconcile.sync(remote,"own",plan(s,"18:00")));remote.failDelete=false;reconcile.sync(remote,"own",plan(s,"18:00"));assertEquals(2,remote.events.size());
    }
    @Test void classDateRangesAndMergedStudyBlocksAreHonored(){
        var s=study();s.setEndTime("20:30");var second=study();second.setStartTime("20:30");
        var source=new CalendarPlanner.Snapshot(List.of(new TimetableEntry("Thứ 2","08:00","10:00","B","","09/11/26 - 09/11/26")),List.of(s,second),date,date);
        var events=CalendarPlanner.plan(source,date.atTime(18,0));assertEquals(3,events.size());assertTrue(events.stream().noneMatch(e->kind(e).equals("class")));
        s.setCompleted(true);var partial=CalendarPlanner.plan(source,date.atTime(20,15));assertEquals(1,partial.size());assertEquals(10,reminder(event(partial,"study")));
    }
    static class Fake implements CalendarGateway {
        Map<String,JsonObject> events=new LinkedHashMap<>();boolean failDelete;
        public String ensureCalendar(String previous){return "own";}
        public Map<String,JsonObject> managedEvents(String id){return new LinkedHashMap<>(events);}
        public void upsert(String calendar,CalendarPlanner.Planned event,boolean exists){events.put(event.id(),event.body().deepCopy());}
        public void delete(String calendar,String id)throws Exception{if(failDelete)throw new java.io.IOException("Offline");events.remove(id);}
    }
}
