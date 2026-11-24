package com.studyagent.calendar;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class GoogleCalendarGatewayTest {
    @TempDir Path temp;
    JsonObject token(String access){JsonObject t=new JsonObject();t.addProperty("access_token",access);t.addProperty("refresh_token","test-refresh");t.addProperty("expires_at",System.currentTimeMillis()+3600000);t.addProperty("expires_in",3600);return t;}
    JsonObject owned(String id){JsonObject e=new JsonObject();e.addProperty("id",id);JsonObject ext=new JsonObject(),priv=new JsonObject();priv.addProperty("owner",CalendarPlanner.OWNER);priv.addProperty("fingerprint","fake");ext.add("private",priv);e.add("extendedProperties",ext);return e;}
    void reply(HttpExchange request,int code,String body)throws java.io.IOException{byte[] bytes=body.getBytes(StandardCharsets.UTF_8);request.getRequestBody().readAllBytes();request.getResponseHeaders().set("Content-Type","application/json");request.sendResponseHeaders(code,bytes.length);request.getResponseBody().write(bytes);request.close();}
    @Test void httpAdapterRefreshesOncePagesOwnedEventsAndResolvesDuplicateInsertWithoutCreatingAnotherEvent()throws Exception{
        Path dir=temp.resolve("google");PrivateJson.write(dir.resolve("token.json"),token("old"));JsonObject cfg=new JsonObject(),client=new JsonObject();client.addProperty("client_id","test.apps.googleusercontent.com");cfg.add("installed",client);PrivateJson.write(dir.resolve("client.json"),cfg);
        AtomicInteger refresh=new AtomicInteger(),posts=new AtomicInteger(),patches=new AtomicInteger(),deletes=new AtomicInteger();
        var auth=new GoogleOAuth(dir,data->{assertEquals("refresh_token",data.get("grant_type"));refresh.incrementAndGet();return token("new");});
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/calendar/v3",request->{
            if("Bearer old".equals(request.getRequestHeaders().getFirst("Authorization"))){reply(request,401,"{}");return;}
            assertEquals("Bearer new",request.getRequestHeaders().getFirst("Authorization"));String path=request.getRequestURI().getPath(),method=request.getRequestMethod();
            if(path.endsWith("/events")&&method.equals("GET")){
                var query=GoogleOAuth.parseQuery(request.getRequestURI().getRawQuery());assertEquals("owner="+CalendarPlanner.OWNER,query.get("privateExtendedProperty"));JsonObject body=new JsonObject();JsonArray items=new JsonArray();
                if(query.containsKey("pageToken")){assertEquals("second+page",query.get("pageToken"));items.add(owned("second"));JsonObject foreign=new JsonObject();foreign.addProperty("id","unrelated");items.add(foreign);}
                else{items.add(owned("first"));body.addProperty("nextPageToken","second+page");}body.add("items",items);reply(request,200,body.toString());return;
            }
            if(method.equals("POST")){posts.incrementAndGet();reply(request,409,"{}");return;}
            if(method.equals("PATCH")){patches.incrementAndGet();assertEquals("none",GoogleOAuth.parseQuery(request.getRequestURI().getRawQuery()).get("sendUpdates"));reply(request,200,"{}");return;}
            if(method.equals("DELETE")){deletes.incrementAndGet();reply(request,404,"{}");return;}
            reply(request,200,owned("first").toString());
        });server.start();
        try{
            var api=new GoogleCalendarGateway(auth,URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/calendar/v3"));assertEquals("own@calendar",api.ensureCalendar("own@calendar"));assertEquals(1,refresh.get());
            assertEquals(2,api.managedEvents("own@calendar").size());api.upsert("own@calendar",new CalendarPlanner.Planned("first",owned("first")),false);assertEquals(1,posts.get());assertEquals(1,patches.get());
            api.delete("own@calendar","gone");assertEquals(1,deletes.get());assertEquals(1,refresh.get());
        }finally{server.stop(0);}
    }
    @Test void permissionsFailureIsReportedWithoutPretendingToSync()throws Exception{
        Path dir=temp.resolve("denied");PrivateJson.write(dir.resolve("token.json"),token("new"));var auth=new GoogleOAuth(dir);
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/calendar/v3",request->reply(request,403,"{}"));server.start();
        try{var api=new GoogleCalendarGateway(auth,URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/calendar/v3"));var error=assertThrows(java.io.IOException.class,()->api.ensureCalendar(""));assertTrue(error.getMessage().contains("403"));}finally{server.stop(0);}
    }
}
