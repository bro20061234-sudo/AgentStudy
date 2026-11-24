package com.studyagent.calendar;

import com.google.gson.*;
import com.studyagent.model.*;
import com.studyagent.repository.StudyRepository;
import com.studyagent.scheduler.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

/** Google only supports reminders before events; follow-ups are separate, cancellable events. */
public final class CalendarPlanner {
    public static final String OWNER="smart-study-agent-v1";
    public record Snapshot(List<TimetableEntry> classes,List<StudySession> study,LocalDate from,LocalDate to){}
    public record Planned(String id,JsonObject body){}
    private CalendarPlanner(){}
    public static Snapshot load(StudyRepository repository,long chatId){
        var classes=repository.getTimetableEntries(chatId);var study=repository.getStudySessions(chatId);List<LocalDate> dates=new ArrayList<>();
        for(var s:study)if(s.getScheduledDate()!=null&&!s.getScheduledDate().isBlank())dates.add(LocalDate.parse(s.getScheduledDate()));
        var range=Pattern.compile("(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s*(?:đến|-)\\s*(\\d{1,2}/\\d{1,2}/\\d{2,4})",Pattern.CASE_INSENSITIVE);
        for(var entry:classes){var matcher=range.matcher(Objects.toString(entry.getNote(),""));if(matcher.find()){var a=StudyOptimizer.parseDate(matcher.group(1));var b=StudyOptimizer.parseDate(matcher.group(2));if(a!=null&&b!=null){dates.add(a);dates.add(b);}}}
        if(!classes.isEmpty()&&dates.isEmpty())throw new IllegalStateException("Hãy đặt khoảng ngày học kỳ và tạo lịch trước khi đồng bộ Google.");
        LocalDate from=dates.stream().min(LocalDate::compareTo).orElse(LocalDate.now(StudyProgress.ZONE)),to=dates.stream().max(LocalDate::compareTo).orElse(from);
        return new Snapshot(classes,study,from,to);
    }
    public static List<Planned> plan(Snapshot source,LocalDateTime now){
        if(source.to().isBefore(source.from()))throw new IllegalArgumentException("Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.");
        Map<String,Planned> result=new TreeMap<>();
        for(LocalDate day=source.from();!day.isAfter(source.to());day=day.plusDays(1)){
            final LocalDate date=day;
            var active=source.classes().stream().filter(e->StudyOptimizer.dayIndex(e.getDayOfWeek())==date.getDayOfWeek().getValue()-1&&StudyOptimizer.isEntryActiveOnDate(e,date)).toList();
            for(var entry:ScheduleBlocks.mergeClasses(active)){
                LocalDateTime start=date.atTime(time(entry.getStartTime())),end=date.atTime(time(entry.getEndTime()));
                String key="class|"+date+"|"+normalized(entry.getSubjectName())+"|"+entry.getStartTime()+"|"+entry.getEndTime()+"|"+Objects.toString(entry.getRoom(),"");
                put(result,key,event("Trên lớp: "+entry.getSubjectName(),"Lịch trên lớp từ Study Agent.",Objects.toString(entry.getRoom(),""),start,end,10,"class"));
            }
        }
        for(var group:ScheduleBlocks.mergeStudySessions(source.study())){
            var first=group.sessions().getFirst();if(first.getScheduledDate()==null||first.getScheduledDate().isBlank())continue;
            LocalDate date=LocalDate.parse(first.getScheduledDate());LocalDateTime start=date.atTime(time(group.start())),end=date.atTime(time(group.end()));
            boolean completed=group.completed(),started=StudyProgress.started(group.sessions());
            String key="study|"+date+"|"+normalized(group.subject())+"|"+group.start()+"|"+group.end();
            String state=StudyProgress.status(group.sessions(),now).label;
            put(result,key,event((completed?"✓ ":"")+"Tự học: "+group.subject(),"Trạng thái: "+state+".\nMở Study Agent để vào học hoặc đánh dấu hoàn thành.\nCác thay đổi cần được đồng bộ để cập nhật nhắc học trên điện thoại.","",start,end,completed?-1:10,"study"));
            if(!completed&&!started)for(int late:new int[]{5,10}){
                LocalDateTime at=start.plusMinutes(late);
                // An expired alert must never be recreated after opening the desktop app late.
                if(at.isBefore(now))continue;
                put(result,key+"|late"+late,event("Nhắc tự học: "+group.subject()+" (muộn "+late+" phút)",late==5?"Bạn chưa ghi nhận vào học. Mở Study Agent để bắt đầu; nếu đã vào học, hãy kiểm tra đồng bộ.":"Đã muộn 10 phút. Nếu chưa vào học, ca được tính là quá hạn, chưa hoàn thành. Bạn vẫn có thể học bù và đánh dấu hoàn thành.","",at,at.plusMinutes(1),0,"late"+late));
            }
        }
        return List.copyOf(result.values());
    }
    private static LocalTime time(String value){int minutes=StudyOptimizer.parseMinutes(value);if(minutes<0)throw new IllegalArgumentException("Giờ học không hợp lệ: "+value);return LocalTime.of(minutes/60,minutes%60);}
    private static String normalized(String value){return Objects.toString(value,"").trim().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");}
    private static JsonObject event(String title,String description,String room,LocalDateTime start,LocalDateTime end,int reminder,String kind){
        if(!end.isAfter(start))throw new IllegalArgumentException("Giờ kết thúc phải sau giờ bắt đầu: "+title);
        JsonObject body=new JsonObject();body.addProperty("summary",title);body.addProperty("description",description);if(!room.isBlank())body.addProperty("location",room);
        body.add("start",date(start));body.add("end",date(end));body.addProperty("visibility","private");body.addProperty("transparency",kind.startsWith("late")?"transparent":"opaque");
        JsonObject reminders=new JsonObject();reminders.addProperty("useDefault",false);JsonArray overrides=new JsonArray();
        if(reminder>=0){JsonObject r=new JsonObject();r.addProperty("method","popup");r.addProperty("minutes",reminder);overrides.add(r);}reminders.add("overrides",overrides);body.add("reminders",reminders);
        JsonObject properties=new JsonObject(),privateProperties=new JsonObject();privateProperties.addProperty("owner",OWNER);privateProperties.addProperty("kind",kind);properties.add("private",privateProperties);body.add("extendedProperties",properties);return body;
    }
    private static JsonObject date(LocalDateTime time){JsonObject date=new JsonObject();date.addProperty("dateTime",time.atZone(StudyProgress.ZONE).format(java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssXXX")));date.addProperty("timeZone",StudyProgress.ZONE.getId());return date;}
    private static void put(Map<String,Planned> result,String key,JsonObject body){String id=hash(key);body.addProperty("id",id);body.getAsJsonObject("extendedProperties").getAsJsonObject("private").addProperty("fingerprint",hash(body.toString()));result.put(id,new Planned(id,body));}
    public static String hash(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
