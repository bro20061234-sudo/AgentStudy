package com.studyagent.ui;

import com.studyagent.ai.GeminiService;
import com.studyagent.model.*;
import com.studyagent.repository.*;
import com.studyagent.scheduler.StudyOptimizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CompactCalendarTest {
    @TempDir Path temp;
    static List<Component> children(Container parent){List<Component> all=new ArrayList<>();for(var c:parent.getComponents()){all.add(c);if(c instanceof Container p)all.addAll(children(p));}return all;}
    static void layout(Container root){root.doLayout();for(var c:root.getComponents())if(c instanceof Container p)layout(p);}
    static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    static void set(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    @Test void datePickerChoosesLeapDayAndADistantYearWithoutTyping()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            DatePicker picker=new DatePicker();picker.setDate(LocalDate.of(2024,2,15));assertFalse(picker.isEditable());
            var popup=picker.createPopup();JButton leap=children(popup).stream().filter(c->c instanceof JButton b&&"29/02/2024".equals(b.getToolTipText())).map(c->(JButton)c).findFirst().orElseThrow();leap.doClick();assertEquals(LocalDate.of(2024,2,29),picker.getDate());
            popup=picker.createPopup();JSpinner year=(JSpinner)children(popup).stream().filter(c->c instanceof JSpinner).findFirst().orElseThrow();JComboBox<?> month=(JComboBox<?>)children(popup).stream().filter(c->c instanceof JComboBox<?>).findFirst().orElseThrow();
            year.setValue(2035);month.setSelectedIndex(2);JButton day=children(popup).stream().filter(c->c instanceof JButton b&&"31/03/2035".equals(b.getToolTipText())).map(c->(JButton)c).findFirst().orElseThrow();day.doClick();assertEquals("31/03/2035",picker.getText());
        });
    }
    static class Source extends StudyRepository {
        final LocalDate monday=LocalDate.now(StudyProgress.ZONE).minusWeeks(1).with(java.time.DayOfWeek.MONDAY);
        final StudySession session=new StudySession("Thứ 2","20:00","20:15","Môn kiểm tra","","");
        Source(){super(false);session.setId("test");session.setScheduledDate(monday.toString());}
        @Override public List<TextbookItem> getAllTextbooks(long id){return List.of();}
        @Override public List<String> getDistinctSubjects(long id){return List.of();}
        @Override public List<StudySession> getStudySessions(long id){return List.of(session);}
    }
    @Test void smallWindowKeepsDateControlsCompactAndShowsFullOverdueStatus()throws Exception{
        var focus=new FocusTimeTracker(1,new FocusTimeRepository("jdbc:sqlite:"+temp.resolve("focus.db")));
        SwingUtilities.invokeAndWait(()->{StudyDesktopUI frame=null;try{
            var source=new Source();var ai=new GeminiService("");frame=new StudyDesktopUI(ai,source,new StudyOptimizer(ai),focus);
            set(frame,"displayedWeekStart",source.monday);set(frame,"scheduleBoundStart",source.monday);set(frame,"scheduleBoundEnd",source.monday.plusDays(6));
            var navigate=StudyDesktopUI.class.getDeclaredMethod("showNavPanel",String.class);navigate.setAccessible(true);navigate.invoke(frame,"schedule");
            var root=frame.getContentPane();root.setSize(1120,760);for(int i=0;i<3;i++)layout(root);
            var picker=(DatePicker)field(frame,"boundFromField");assertTrue(picker.getParent().getHeight()<=50);
            for(var component:picker.getParent().getComponents())assertTrue(component.getY()+component.getHeight()<=picker.getParent().getHeight(),"All date actions must fit on the compact toolbar");
            var calendar=(WeeklyCalendarPanel)field(frame,"studyCalendarDays");assertTrue(calendar.getHeight()>=350,"Calendar should keep enough vertical space in a small window");
            var buttons=children(calendar).stream().filter(c->c instanceof JButton).map(c->(JButton)c).toList();assertEquals(1,buttons.size());assertTrue(buttons.getFirst().getText().contains("Quá hạn"));
            assertTrue(children(picker.getParent()).stream().noneMatch(c->c instanceof JLabel label&&label.getText().contains("20:00")));
        }catch(Exception e){throw new RuntimeException(e);}finally{if(frame!=null)frame.dispose();}});
    }
}
