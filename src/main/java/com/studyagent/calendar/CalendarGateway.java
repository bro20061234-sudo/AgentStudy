package com.studyagent.calendar;

import com.google.gson.JsonObject;
import java.util.Map;

public interface CalendarGateway {
    String ensureCalendar(String previousId)throws Exception;
    Map<String,JsonObject> managedEvents(String calendarId)throws Exception;
    void upsert(String calendarId,CalendarPlanner.Planned event,boolean exists)throws Exception;
    void delete(String calendarId,String eventId)throws Exception;
}
