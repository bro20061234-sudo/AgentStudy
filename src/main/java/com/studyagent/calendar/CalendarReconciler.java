package com.studyagent.calendar;

import com.google.gson.JsonObject;
import java.util.*;

/** Own-calendar reconciliation is idempotent across retries and never deletes unrelated events. */
public final class CalendarReconciler {
    public record Result(int saved,int removed){}
    public Result sync(CalendarGateway gateway,String calendarId,List<CalendarPlanner.Planned> planned)throws Exception{
        var remote=gateway.managedEvents(calendarId);Map<String,CalendarPlanner.Planned> desired=new LinkedHashMap<>();for(var event:planned)desired.put(event.id(),event);
        int saved=0,removed=0;
        for(var entry:remote.entrySet())if(!desired.containsKey(entry.getKey())&&owned(entry.getValue())){gateway.delete(calendarId,entry.getKey());removed++;}
        for(var event:planned){JsonObject previous=remote.get(event.id());if(previous!=null&&!owned(previous))throw new IllegalStateException("Trùng mã với sự kiện không thuộc Study Agent; chưa ghi đè.");
            if(previous==null||!fingerprint(previous).equals(fingerprint(event.body()))){gateway.upsert(calendarId,event,previous!=null);saved++;}}
        return new Result(saved,removed);
    }
    public static boolean owned(JsonObject body){try{return CalendarPlanner.OWNER.equals(body.getAsJsonObject("extendedProperties").getAsJsonObject("private").get("owner").getAsString());}catch(RuntimeException e){return false;}}
    static String fingerprint(JsonObject body){try{return body.getAsJsonObject("extendedProperties").getAsJsonObject("private").get("fingerprint").getAsString();}catch(RuntimeException e){return "";}}
}
