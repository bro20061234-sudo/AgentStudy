package com.studyagent;

import com.studyagent.model.FocusTimeTracker;
import com.studyagent.repository.FocusTimeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class FocusTimeTest {
    @TempDir Path temp;
    FocusTimeRepository repository(){return new FocusTimeRepository("jdbc:sqlite:"+temp.resolve("focus.db"));}
    Clock day=Clock.fixed(Instant.parse("2026-10-30T04:00:00Z"),ZoneId.of("Asia/Ho_Chi_Minh"));
    @Test void countsActualElapsedFocusIncludingDelayedTicksAndSurvivesRestart(){
        var r=repository();var nanos=new AtomicLong();var timer=new FocusTimeTracker(1,r,day,nanos::get);
        timer.start();nanos.addAndGet(12_500_000_000L);assertEquals(12,timer.advance(true,1500));assertEquals(12,timer.summary().todaySeconds());
        nanos.addAndGet(500_000_000L);assertEquals(1,timer.advance(true,1488));timer.pause();
        assertEquals(13,new FocusTimeTracker(1,repository(),day,nanos::get).summary().totalSeconds());
        assertEquals(0,new FocusTimeTracker(2,repository(),day,nanos::get).summary().totalSeconds());
    }
    @Test void excludesPauseAndBreakAndNeverCountsBeyondFocusCountdown(){
        var r=repository();var nanos=new AtomicLong();var timer=new FocusTimeTracker(1,r,day,nanos::get);
        timer.start();nanos.addAndGet(7_000_000_000L);assertEquals(7,timer.advance(true,1500));timer.pause();
        nanos.addAndGet(100_000_000_000L);assertEquals(0,timer.advance(true,1500));
        timer.start();nanos.addAndGet(40_000_000_000L);assertEquals(30,timer.advance(true,30));timer.restartInterval();
        nanos.addAndGet(20_000_000_000L);assertEquals(20,timer.advance(false,300));timer.pause();
        assertEquals(37,timer.summary().totalSeconds());assertEquals(37,repository().summary(1,LocalDate.of(2026,10,30)).todaySeconds());
    }
    @Test void allocatesAnElapsedIntervalToBothSidesOfMidnight(){
        var r=repository();var nanos=new AtomicLong();Clock afterMidnight=Clock.fixed(Instant.parse("2026-10-30T17:00:05Z"),ZoneId.of("Asia/Ho_Chi_Minh"));
        var timer=new FocusTimeTracker(1,r,afterMidnight,nanos::get);timer.start();nanos.addAndGet(10_000_000_000L);timer.advance(true,100);timer.pause();
        assertEquals(5,r.summary(1,LocalDate.of(2026,10,30)).todaySeconds());assertEquals(5,r.summary(1,LocalDate.of(2026,10,31)).todaySeconds());assertEquals(10,timer.summary().totalSeconds());
    }
}
