package com.studyagent;

import com.studyagent.model.*;
import com.studyagent.repository.*;
import com.studyagent.calendar.CalendarPlanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SessionProgressPersistenceTest {
    @TempDir Path temp;
    StudySession session(){var s=new StudySession("Thứ 2","20:00","21:00","A","","");s.setScheduledDate("2026-11-02");return s;}
    interface Work{void run(StudyRepository r)throws Exception;}
    void isolated(Work work)throws Exception{
        String old=System.getProperty("studyagent.home");try{System.setProperty("studyagent.home",temp.toString());work.run(new StudyRepository());}finally{if(old==null)System.clearProperty("studyagent.home");else System.setProperty("studyagent.home",old);}
    }
    @Test void entryAndEarlyCompletionSurviveRestartAndRegeneratingSameSchedule()throws Exception{
        isolated(r->{var s=session();r.saveStudySessions(1,List.of(s));r.markStudySessionsStarted(1,List.of(s.getId()),"2026-11-01T12:00:00Z");r.completeStudySessions(1,List.of(s.getId()));
            var loaded=new StudyRepository().getStudySessions(1).getFirst();assertTrue(loaded.hasStarted());assertTrue(loaded.isCompleted());
            r.saveStudySessions(1,List.of(session()));var regenerated=r.getStudySessions(1).getFirst();assertTrue(regenerated.hasStarted());assertTrue(regenerated.isCompleted());
            var changed=session();changed.setStartTime("21:00");changed.setEndTime("22:00");r.saveStudySessions(1,List.of(changed));assertFalse(r.getStudySessions(1).getFirst().isCompleted());
        });
    }
    @Test void groupStateUpdatesRollbackIfAMemberWasDeletedAndAreScopedToOwner()throws Exception{
        isolated(r->{var s=session();r.saveStudySessions(1,List.of(s));
            assertThrows(IllegalStateException.class,()->r.markStudySessionsStarted(1,List.of(s.getId(),"missing"),"2026-11-01T12:00:00Z"));assertFalse(r.getStudySessions(1).getFirst().hasStarted());
            assertThrows(IllegalStateException.class,()->r.completeStudySessions(1,List.of(s.getId(),"missing")));assertFalse(r.getStudySessions(1).getFirst().isCompleted());
            assertThrows(IllegalStateException.class,()->r.completeStudySessions(2,List.of(s.getId())));assertFalse(r.getStudySessions(1).getFirst().isCompleted());
        });
    }
    @Test void editedDateIsPersistedAndDatabaseReadFailureDoesNotBecomeAnEmptySync()throws Exception{
        isolated(r->{var s=session();r.saveStudySessions(1,List.of(s));s.setScheduledDate("2026-11-03");s.setDayOfWeek("Thứ 3");r.updateStudySession(s);assertEquals("2026-11-03",r.getStudySessions(1).getFirst().getScheduledDate());
            try(var c=DatabaseManager.getConnection();var sql=c.createStatement()){sql.execute("DROP TABLE timetables");}
            assertThrows(IllegalStateException.class,()->CalendarPlanner.load(r,1));
        });
    }
    @Test void oldDatabaseGetsStartedFieldWithoutLosingCompletion()throws Exception{
        isolated(r->{var s=session();s.setCompleted(true);r.saveStudySessions(1,List.of(s));
            try(var c=DatabaseManager.getConnection();var sql=c.createStatement()){sql.execute("ALTER TABLE study_sessions DROP COLUMN started_at");}
            var migrated=new StudyRepository().getStudySessions(1).getFirst();assertTrue(migrated.isCompleted());assertFalse(migrated.hasStarted());
        });
    }
}
