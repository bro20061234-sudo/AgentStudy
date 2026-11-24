package com.studyagent.calendar;

import com.google.gson.*;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public final class GoogleCalendarGateway implements CalendarGateway {
    private final GoogleOAuth auth;
    private final URI base;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    public GoogleCalendarGateway(GoogleOAuth auth){this(auth,URI.create("https://www.googleapis.com/calendar/v3"));}
    GoogleCalendarGateway(GoogleOAuth auth,URI base){this.auth=auth;this.base=base;}
    private static String encode(String text){return URLEncoder.encode(text,StandardCharsets.UTF_8);}
    @Override public String ensureCalendar(String previous)throws Exception{
        if(previous!=null&&!previous.isBlank()){
            var response=call("GET","/calendars/"+encode(previous),null);
            if(response.statusCode()==200)return previous;
            if(response.statusCode()!=404&&response.statusCode()!=410&&response.statusCode()!=403)require(response,200);
        }
        JsonObject body=new JsonObject();body.addProperty("summary","Study Agent");body.addProperty("description","Lịch trên lớp, tự học và nhắc học do ứng dụng Study Agent quản lý.");body.addProperty("timeZone","Asia/Ho_Chi_Minh");
        var response=call("POST","/calendars",body);require(response,200);return JsonParser.parseString(response.body()).getAsJsonObject().get("id").getAsString();
    }
    @Override public Map<String,JsonObject> managedEvents(String calendarId)throws Exception{
        Map<String,JsonObject> events=new LinkedHashMap<>();String page="";
        do{
            String path="/calendars/"+encode(calendarId)+"/events?maxResults=2500&showDeleted=false&privateExtendedProperty="+encode("owner="+CalendarPlanner.OWNER)+(page.isBlank()?"":"&pageToken="+encode(page));
            var response=call("GET",path,null);require(response,200);JsonObject body=JsonParser.parseString(response.body()).getAsJsonObject();
            if(body.has("items"))for(var item:body.getAsJsonArray("items")){JsonObject event=item.getAsJsonObject();if(CalendarReconciler.owned(event))events.put(event.get("id").getAsString(),event);}
            page=PrivateJson.text(body,"nextPageToken");
        }while(!page.isBlank());return events;
    }
    @Override public void upsert(String calendarId,CalendarPlanner.Planned event,boolean exists)throws Exception{
        String base="/calendars/"+encode(calendarId)+"/events";
        var response=call(exists?"PATCH":"POST",base+(exists?"/"+encode(event.id()):"")+"?sendUpdates=none",event.body());
        if(!exists&&response.statusCode()==409){var found=call("GET",base+"/"+encode(event.id()),null);require(found,200);if(!CalendarReconciler.owned(JsonParser.parseString(found.body()).getAsJsonObject()))throw new IOException("Sự kiện Google trùng mã nhưng không thuộc Study Agent.");response=call("PATCH",base+"/"+encode(event.id())+"?sendUpdates=none",event.body());}
        require(response,200,201);
    }
    @Override public void delete(String calendarId,String id)throws Exception{require(call("DELETE","/calendars/"+encode(calendarId)+"/events/"+encode(id)+"?sendUpdates=none",null),200,204,404,410);}
    private HttpResponse<String> call(String method,String path,JsonObject body)throws Exception{
        for(int retry=0;retry<2;retry++){
            var builder=HttpRequest.newBuilder(URI.create(base.toString()+path)).timeout(Duration.ofSeconds(20)).header("Authorization","Bearer "+auth.accessToken()).header("Accept","application/json");
            if(body!=null)builder.header("Content-Type","application/json; charset=utf-8");
            var response=http.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body.toString(),StandardCharsets.UTF_8)).build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(response.statusCode()!=401||retry==1)return response;auth.invalidateAccess();
        }throw new IOException("Không thể kết nối Google Calendar.");
    }
    private static void require(HttpResponse<String> response,int... codes)throws IOException{for(int code:codes)if(response.statusCode()==code)return;int code=response.statusCode();throw new IOException("Google Calendar HTTP "+code+". "+(code==403?"Kiểm tra đã bật Calendar API và đã cấp quyền cho dự án OAuth.":code==429?"Đang bị giới hạn yêu cầu; ứng dụng sẽ tự thử lại.":"Chưa đồng bộ thay đổi; hãy kiểm tra mạng hoặc kết nối lại."));}
}
