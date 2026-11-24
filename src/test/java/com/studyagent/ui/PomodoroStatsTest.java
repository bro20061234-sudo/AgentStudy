package com.studyagent.ui;

import com.studyagent.ai.GeminiService;
import com.studyagent.model.FocusTimeTracker;
import com.studyagent.repository.*;
import com.studyagent.scheduler.StudyOptimizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.nio.file.Path;
import java.time.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class PomodoroStatsTest {
    @TempDir Path temp;
    static class EmptyStudy extends StudyRepository{
        EmptyStudy(){super(false);}
        @Override public List<TextbookItem> getAllTextbooks(long id){return List.of();}
        @Override public List<String> getDistinctSubjects(long id){return List.of();}
        @Override public List<com.studyagent.model.StudySession> getStudySessions(long id){return List.of();}
    }
    static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    static void call(Object target,String name)throws Exception{var m=target.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(target);}
    @Test void dashboardReflectsRunningClockAndResetNeverErasesAccumulatedStudyTime()throws Exception{
        String url="jdbc:sqlite:"+temp.resolve("time.db");var repository=new FocusTimeRepository(url);var nanos=new AtomicLong();
        var tracker=new FocusTimeTracker(1,repository,Clock.fixed(Instant.parse("2026-10-30T04:00:00Z"),ZoneId.of("Asia/Ho_Chi_Minh")),nanos::get);
        SwingUtilities.invokeAndWait(()->{StudyDesktopUI ui=null;try{
            var ai=new GeminiService("");ui=new StudyDesktopUI(ai,new EmptyStudy(),new StudyOptimizer(ai),tracker);
            call(ui,"togglePomodoro");nanos.addAndGet(10_000_000_000L);call(ui,"tickPomodoro");
            assertEquals(1490,field(ui,"remainingSeconds"));assertEquals("10 giây",((JLabel)field(field(ui,"homePanel"),"todayTime")).getText());
            nanos.addAndGet(5_000_000_000L);call(ui,"togglePomodoro");assertEquals(15,tracker.summary().todaySeconds());
            nanos.addAndGet(3600_000_000_000L);call(ui,"togglePomodoro");nanos.addAndGet(7_000_000_000L);call(ui,"resetPomodoro");
            assertEquals(22,tracker.summary().totalSeconds());assertEquals(1500,field(ui,"remainingSeconds"));
            call(ui,"togglePomodoro");nanos.addAndGet(4_000_000_000L);
        }catch(Exception e){throw new RuntimeException(e);}finally{if(ui!=null)ui.dispose();}});
        assertEquals(26,new FocusTimeRepository(url).summary(1,LocalDate.of(2026,10,30)).totalSeconds());
    }
}
