package com.studyagent;

import com.studyagent.ai.GeminiService;
import com.studyagent.model.*;
import com.studyagent.scheduler.StudyOptimizer;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScheduleWithoutAiTest {
    @Test void weekAndSemesterSchedulingNeverUseAiEvenWithConfiguredKey() {
        GeminiService ai=new GeminiService("configured-key") {
            @Override public boolean hasApiKey(){throw new AssertionError("Xếp lịch không cần kiểm tra hoặc gọi AI");}
            @Override public List<StudySession> optimizeStudyScheduleWithAI(List<TimetableEntry> entries){throw new AssertionError("Không được tạo chủ đề bằng AI khi xếp lịch");}
        };
        var optimizer=new StudyOptimizer(ai);
        var entries=List.of(new TimetableEntry("Thứ 2","07:00","09:00","Cơ sở dữ liệu","",""));
        var weekly=optimizer.generateOptimalSchedule(entries);
        assertEquals(1,weekly.size());assertEquals("20:00",weekly.get(0).getStartTime());assertEquals("20:30",weekly.get(0).getEndTime());
        var semester=optimizer.generateSemesterSchedule(entries,LocalDate.of(2026,10,26),LocalDate.of(2026,11,8));
        assertEquals(2,semester.size());
        assertTrue(semester.stream().allMatch(s->s.getSubjectName().equals("Cơ sở dữ liệu")&&s.getTopicOverview().isBlank()&&s.getPreparationMaterials().isBlank()));
        assertEquals("2026-10-26",semester.get(0).getScheduledDate());assertEquals("2026-11-02",semester.get(1).getScheduledDate());
    }
}
