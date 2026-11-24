package com.studyagent.model;

import com.studyagent.repository.FocusTimeRepository;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.LongSupplier;

/** Monotonic time counts delayed timer ticks correctly; periodic commits limit disk writes. */
public final class FocusTimeTracker {
    private final long chatId;
    private final FocusTimeRepository repository;
    private final Clock clock;
    private final LongSupplier nanos;
    private final Map<LocalDate,Long> pending=new LinkedHashMap<>();
    private boolean running;
    private long anchor;
    public FocusTimeTracker(long chatId,FocusTimeRepository repository){this(chatId,repository,Clock.systemDefaultZone(),System::nanoTime);}
    public FocusTimeTracker(long chatId,FocusTimeRepository repository,Clock clock,LongSupplier nanos){this.chatId=chatId;this.repository=repository;this.clock=clock;this.nanos=nanos;}
    public void start(){if(!running){running=true;anchor=nanos.getAsLong();}}
    public void restartInterval(){anchor=nanos.getAsLong();}
    public void pause(){running=false;flush();}
    public boolean isRunning(){return running;}
    public int advance(boolean focus,int remainingSeconds){
        if(!running)return 0;
        long now=nanos.getAsLong(),elapsed=Math.max(0,(now-anchor)/1_000_000_000L);
        if(elapsed==0)return 0;
        anchor+=elapsed*1_000_000_000L;
        int spent=(int)Math.min(elapsed,Math.max(0,remainingSeconds));
        if(focus&&spent>0){
            Instant cursor=clock.instant().truncatedTo(ChronoUnit.SECONDS).minusSeconds(elapsed);long left=spent;
            while(left>0){LocalDate day=cursor.atZone(clock.getZone()).toLocalDate();Instant midnight=day.plusDays(1).atStartOfDay(clock.getZone()).toInstant();long part=Math.min(left,Duration.between(cursor,midnight).getSeconds());pending.merge(day,part,Long::sum);left-=part;cursor=cursor.plusSeconds(part);}
            if(pending.values().stream().mapToLong(Long::longValue).sum()>=15)flush();
        }
        return spent;
    }
    public void flush(){if(!pending.isEmpty()){repository.add(chatId,Map.copyOf(pending));pending.clear();}}
    public FocusTimeRepository.Summary summary(){var saved=repository.summary(chatId,LocalDate.now(clock));return new FocusTimeRepository.Summary(saved.todaySeconds()+pending.getOrDefault(LocalDate.now(clock),0L),saved.totalSeconds()+pending.values().stream().mapToLong(Long::longValue).sum());}
    public static String format(long seconds){long h=seconds/3600,m=seconds%3600/60,s=seconds%60;return h>0?String.format("%dh %02dp",h,m):m>0?String.format("%dp %02ds",m,s):s+" giây";}
}
