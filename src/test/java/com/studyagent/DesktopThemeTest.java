package com.studyagent;

import com.studyagent.ui.*;
import com.studyagent.model.*;
import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DesktopThemeTest {
    @TempDir Path temp;
    @Test void appearancePersistsBesideApplicationData()throws Exception{
        Path settings=temp.resolve("data/appearance.properties");assertFalse(UiTheme.readDark(settings));
        UiTheme.writeDark(settings,true);assertTrue(UiTheme.readDark(settings));UiTheme.writeDark(settings,false);assertFalse(UiTheme.readDark(settings));
    }
    @Test void changingThemePreservesInputSelectionAndHtmlContent()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            try{
                UiTheme.install();UiTheme.setDark(false,false);JPanel root=new UiKit.Card(new GridLayout(0,1));
                JTextField input=new JTextField("Nội dung chưa gửi");input.select(2,8);root.add(input);
                JComboBox<String> subjects=new JComboBox<>(new String[]{"CSDL","Hệ điều hành"});subjects.setSelectedIndex(1);root.add(subjects);
                var html=LessonHtml.pane();LessonHtml.show(html,"Bài 1","## Mục tiêu\n- Nắm **khái niệm**.");root.add(html);UiTheme.apply(root);
                UiTheme.setDark(true,false);UiTheme.apply(root);
                assertEquals("Nội dung chưa gửi",input.getText());assertEquals(2,input.getSelectionStart());assertEquals("Hệ điều hành",subjects.getSelectedItem());
                assertEquals(UiTheme.color("card"),root.getBackground());assertEquals(UiTheme.color("text"),input.getForeground());
                assertTrue(html.getText().toLowerCase(Locale.ROOT).contains(UiTheme.hex("accent")));
                assertTrue(assertDoesNotThrow(()->html.getDocument().getText(0,html.getDocument().getLength())).contains("khái niệm"));
                UiTheme.setDark(false,false);UiTheme.apply(root);assertTrue(html.getText().toLowerCase(Locale.ROOT).contains(UiTheme.hex("accent")));
            }finally{UiTheme.setDark(false,false);}
        });
    }
    @Test void readingTextHasSufficientContrastInBothThemes(){
        try{for(boolean dark:new boolean[]{false,true}){
            UiTheme.setDark(dark,false);
            assertTrue(contrast(UiTheme.color("text"),UiTheme.color("card"))>=7);
            for(String[] pair:new String[][]{{"white","primary"},{"muted","card"},{"muted","app"},{"accent","tint"},{"green","greenSoft"},{"warning","warningSoft"},{"classText","classBg"},{"studyText","studyBg"}})
                assertTrue(contrast(UiTheme.color(pair[0]),UiTheme.color(pair[1]))>=4.5,(dark?"dark ":"light ")+Arrays.toString(pair));
        }}
        finally{UiTheme.setDark(false,false);}
    }
    @Test void unstyledComponentsCreatedBeforeSwitchResolveTheirOriginalColorRoles()throws Exception{
        SwingUtilities.invokeAndWait(()->{try{
            UiTheme.setDark(false,false);JLabel old=new JLabel("Link");old.setForeground(UiTheme.color("accent"));old.setBackground(UiTheme.color("tint"));
            Color lightAccent=old.getForeground();UiTheme.setDark(true,false);UiTheme.apply(old);
            assertEquals(UiTheme.color("accent"),old.getForeground());assertEquals(UiTheme.color("tint"),old.getBackground());assertNotEquals(lightAccent,old.getForeground());
            UiTheme.setDark(false,false);UiTheme.apply(old);assertEquals(lightAccent,old.getForeground());
        }finally{UiTheme.setDark(false,false);}});
    }
    @Test void calendarFiltersDatesAndKeepsLearnAndEditActionsOnRealSessions()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            AtomicInteger learned=new AtomicInteger();var calendar=new WeeklyCalendarPanel(true,s->learned.incrementAndGet(),s->{},s->{});
            StudySession first=new StudySession("Thứ 6","19:30","20:30","Cơ sở dữ liệu","","");first.setScheduledDate("2026-10-30");
            StudySession outside=new StudySession("Thứ 6","19:30","20:30","Ngoài tuần","","");outside.setScheduledDate("2026-11-06");
            calendar.setWeek(LocalDate.of(2026,10,26),LocalDate.of(2026,10,26),LocalDate.of(2026,11,1),List.of(),List.of(first,outside));calendar.setSize(1100,500);layout(calendar);
            List<JButton> buttons=new ArrayList<>();collect(calendar,buttons);assertEquals(1,buttons.size());assertTrue(buttons.get(0).getText().contains("19:30"));
            assertEquals(3,buttons.get(0).getComponentPopupMenu().getComponentCount());buttons.get(0).doClick();assertEquals(1,learned.get());
        });
    }
    private static void collect(Container c,List<JButton> buttons){for(Component child:c.getComponents()){if(child instanceof JButton b)buttons.add(b);if(child instanceof Container p)collect(p,buttons);}}
    private static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container p)layout(p);}
    private static double contrast(Color a,Color b){double first=luminance(a),second=luminance(b);return (Math.max(first,second)+.05)/(Math.min(first,second)+.05);}
    private static double luminance(Color c){return .2126*linear(c.getRed())+.7152*linear(c.getGreen())+.0722*linear(c.getBlue());}
    private static double linear(int channel){double n=channel/255.;return n<=.04045?n/12.92:Math.pow((n+.055)/1.055,2.4);}
}
