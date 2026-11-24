package com.studyagent.ui;

import com.studyagent.ai.GeminiService;
import com.studyagent.model.*;
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

class StudyAttendanceWorkflowTest {
    @TempDir Path temp;
    static class Memory extends StudyRepository{
        final StudySession session=new StudySession("Thứ 2","20:00","21:00","A","","");
        Memory(){super(false);session.setId("s");session.setScheduledDate(LocalDate.now(StudyProgress.ZONE).plusDays(1).toString());}
        @Override public List<TextbookItem> getAllTextbooks(long id){return List.of();}
        @Override public List<TimetableEntry> getTimetableEntries(long id){return List.of();}
        @Override public List<String> getDistinctSubjects(long id){return List.of();}
        @Override public List<StudySession> getStudySessions(long id){return List.of(session);}
        @Override public List<WeeklyCurriculum> prepareChapters(long id,String subject){return List.of();}
        @Override public void markStudySessionsStarted(long id,List<String> ids,String at){assertEquals(List.of("s"),ids);session.setStartedAt(at);}
        @Override public void completeStudySessions(long id,List<String> ids){assertEquals(List.of("s"),ids);session.setCompleted(true);}
    }
    static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    static void call(Object target,String name,Class<?>[] types,Object... args)throws Exception{var m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(target,args);}
    @Test void enteringEarlyRecordsAttendanceAndFinishButtonCompletesTheCaWithoutWaitingForDueDate()throws Exception{
        var source=new Memory();var nanos=new AtomicLong();var focus=new FocusTimeTracker(1,new FocusTimeRepository("jdbc:sqlite:"+temp.resolve("focus.db")),Clock.system(StudyProgress.ZONE),nanos::get);
        SwingUtilities.invokeAndWait(()->{StudyDesktopUI ui=null;try{
            var ai=new GeminiService("");ui=new StudyDesktopUI(ai,source,new StudyOptimizer(ai),focus);call(ui,"refreshSavedData",new Class<?>[0]);
            call(ui,"startStudyEarly",new Class<?>[]{StudySession.class},source.session);assertTrue(source.session.hasStarted());assertFalse(source.session.isCompleted());assertTrue(((JButton)field(ui,"finishStudyButton")).isEnabled());
            nanos.addAndGet(10_000_000_000L);((JButton)field(ui,"finishStudyButton")).doClick();assertTrue(source.session.isCompleted());assertFalse(((Timer)field(ui,"pomodoroTimer")).isRunning());
            assertTrue(((JLabel)field(ui,"studyCounts")).getText().contains("Học trước: 1"));assertEquals(10,focus.summary().todaySeconds());
        }catch(Exception e){throw new RuntimeException(e);}finally{if(ui!=null)ui.dispose();}});
    }
    @Test void finishingTheFocusedCountdownAutomaticallyCompletesItsActiveStudySession()throws Exception{
        var source=new Memory();var nanos=new AtomicLong();var focus=new FocusTimeTracker(1,new FocusTimeRepository("jdbc:sqlite:"+temp.resolve("auto.db")),Clock.system(StudyProgress.ZONE),nanos::get);
        SwingUtilities.invokeAndWait(()->{StudyDesktopUI ui=null;try{
            var ai=new GeminiService("");ui=new StudyDesktopUI(ai,source,new StudyOptimizer(ai),focus);call(ui,"refreshSavedData",new Class<?>[0]);call(ui,"startStudyEarly",new Class<?>[]{StudySession.class},source.session);
            var remaining=StudyDesktopUI.class.getDeclaredField("remainingSeconds");remaining.setAccessible(true);remaining.setInt(ui,1);nanos.addAndGet(1_000_000_000L);call(ui,"tickPomodoro",new Class<?>[0]);assertTrue(source.session.isCompleted());assertFalse((boolean)field(ui,"focusMode"));
        }catch(Exception e){throw new RuntimeException(e);}finally{if(ui!=null)ui.dispose();}});
    }
}
