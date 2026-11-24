package com.studyagent;

import com.studyagent.model.*;
import com.studyagent.scheduler.ScheduleBlocks;
import com.studyagent.ui.WeeklyCalendarPanel;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class CalendarGroupingTest {
    StudySession session(String subject,String start,String end,String date){
        var s=new StudySession("Thứ 2",start,end,subject,"","");s.setScheduledDate(date);s.setId(UUID.randomUUID().toString());return s;
    }
    @Test void groupsAdjacentSavedSessionsWithoutMutatingTheirIdsHoursOrProgress(){
        var first=session("A","20:00","20:30","2026-10-26");first.setCompleted(true);
        var second=session(" a ","20:45","21:00","2026-10-26");
        var last=session("A","21:00","21:30","2026-10-26");
        var groups=ScheduleBlocks.mergeStudySessions(List.of(last,second,first));assertEquals(1,groups.size());
        var block=groups.getFirst();assertEquals("20:00",block.start());assertEquals("21:30",block.end());assertFalse(block.completed());
        assertEquals(List.of(first,second,last),block.sessions());assertEquals("20:30",first.getEndTime());assertTrue(first.isCompleted());assertFalse(second.isCompleted());
    }
    @Test void doesNotMergeAcrossDaysLongGapsOrAnInterveningSubject(){
        var a=session("A","20:00","20:15","2026-10-26");
        var b=session("B","20:15","20:30","2026-10-26");
        var later=session("A","20:30","20:45","2026-10-26");
        assertEquals(3,ScheduleBlocks.mergeStudySessions(List.of(a,b,later)).size());
        assertEquals(2,ScheduleBlocks.mergeStudySessions(List.of(a,session("A","20:31","21:00","2026-10-26"))).size());
        assertEquals(2,ScheduleBlocks.mergeStudySessions(List.of(a,session("A","20:15","21:00","2026-10-27"))).size());
    }
    @Test void overlappingClassRecordsOfSameSubjectBecomeOneBlockWithoutLosingRoomInfo(){
        var first=new TimetableEntry("Thứ 2","16:30","18:30","Python","A1","");
        var second=new TimetableEntry("Thứ hai","18:00","19:00"," Python ","A2","");
        var blocks=ScheduleBlocks.mergeClasses(List.of(second,first));assertEquals(1,blocks.size());
        assertEquals("16:30",blocks.getFirst().getStartTime());assertEquals("19:00",blocks.getFirst().getEndTime());
        assertTrue(blocks.getFirst().getRoom().contains("A1"));assertTrue(blocks.getFirst().getRoom().contains("A2"));
        assertEquals("18:30",first.getEndTime());
    }
    @Test void compactCalendarGroupsExistingDataAndRetainsOriginalEditAndCompletionTargets()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            var a=session("A","20:00","20:30","2026-10-26");a.setCompleted(true);
            var b=session("A","20:40","21:00","2026-10-26");
            AtomicReference<StudySession> learned=new AtomicReference<>(),edited=new AtomicReference<>();
            var calendar=new WeeklyCalendarPanel(true,learned::set,edited::set,s->s.setCompleted(!s.isCompleted()));
            calendar.setWeek(LocalDate.of(2026,10,26),null,null,List.of(),List.of(a,b));
            calendar.setSize(1100,800);layout(calendar);List<JButton> buttons=new ArrayList<>();collect(calendar,buttons);
            assertEquals(1,buttons.size());assertTrue(calendar.getPreferredSize().height<=540);assertFalse(calendar.getScrollableTracksViewportHeight());
            assertTrue(buttons.getFirst().getParent().getHeight()<=540);assertTrue(buttons.getFirst().getToolTipText().contains("2 khung giờ"));assertFalse(buttons.getFirst().getText().contains("gộp"));
            buttons.getFirst().doClick();assertSame(a,learned.get());
            var popup=buttons.getFirst().getComponentPopupMenu();var edit=(JMenu)popup.getComponent(1);edit.getItem(1).doClick();assertSame(b,edited.get());
            ((JMenuItem)popup.getComponent(2)).doClick();assertTrue(a.isCompleted());assertTrue(b.isCompleted());
            assertEquals("20:30",a.getEndTime());
        });
    }
    @Test void fullClassDayUsesCompactTimeScale()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            var calendar=new WeeklyCalendarPanel(false,s->{},s->{},s->{});
            calendar.setWeek(LocalDate.of(2026,10,26),null,null,List.of(new TimetableEntry("Thứ 2","07:00","09:00","A","",""),new TimetableEntry("Thứ 2","20:00","22:00","B","","")),List.of());
            assertTrue(calendar.getPreferredSize().height<=540);assertFalse(calendar.getScrollableTracksViewportHeight());
        });
    }
    @Test void bothCalendarsFitTheirLastEventInsideShortViewport()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            for(boolean study:new boolean[]{false,true}){
                var calendar=new WeeklyCalendarPanel(study,s->{},s->{},s->{});
                calendar.setWeek(LocalDate.of(2026,10,26),null,null,
                        List.of(new TimetableEntry("Thứ 2","07:00","09:00","A","",""),new TimetableEntry("Thứ 2","21:00","22:00","B","","")),
                        List.of(session("A","19:30","20:00","2026-10-26"),session("B","22:00","23:00","2026-10-26")));
                JScrollPane scroll=new JScrollPane(calendar);scroll.setSize(1100,310);
                for(int i=0;i<3;i++)layout(scroll);
                assertTrue(calendar.getPreferredSize().height<=scroll.getViewport().getExtentSize().height);
                List<JButton> buttons=new ArrayList<>();collect(calendar,buttons);assertEquals(2,buttons.size());
                for(var button:buttons)assertTrue(button.getY()+button.getHeight()<=scroll.getViewport().getExtentSize().height);
            }
        });
    }
    private static void collect(Container c,List<JButton> result){for(var child:c.getComponents()){if(child instanceof JButton b)result.add(b);if(child instanceof Container p)collect(p,result);}}
    private static void layout(Container c){c.doLayout();for(var child:c.getComponents())if(child instanceof Container p)layout(p);}
}
