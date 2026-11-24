package com.studyagent;

import com.studyagent.ui.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.awt.Color;
import javax.swing.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SubjectColorsTest {
    @TempDir Path temp;
    @Test void courseAssignmentsAreDistinctAndPersistWhenOtherCoursesAreAdded(){
        Path file=temp.resolve("colors.properties");var r=new SubjectColors.Registry(file);Set<Integer> indices=new HashSet<>();
        for(String s:List.of("Hệ điều hành","Cơ sở dữ liệu","Lịch sử Đảng","Python","OOP","Tiếng Anh"))assertTrue(indices.add(r.index(s)));
        int original=r.index("Hệ điều hành");r.index("New course");assertEquals(original,new SubjectColors.Registry(file).index("  HỆ   ĐIỀU HÀNH "));
    }
    @Test void subjectWidgetsKeepTheirCourseColorsAndContrastAfterThemeRoundTrip()throws Exception{
        SwingUtilities.invokeAndWait(()->{try{for(boolean dark:new boolean[]{false,true,false}){
            UiTheme.setDark(dark,false);
            for(String subject:List.of("Hệ điều hành","Cơ sở dữ liệu","Lịch sử Đảng","Python","OOP","Tiếng Anh")){
                JButton event=new JButton("Course");JProgressBar bar=new JProgressBar();SubjectColors.decorate(event,subject,true);SubjectColors.decorate(bar,subject,false);UiTheme.apply(event);UiTheme.apply(bar);
                assertEquals(event.getForeground(),bar.getForeground());assertTrue(contrast(event.getForeground(),event.getBackground())>=4.5,subject+" "+dark);
            }
        }}finally{UiTheme.setDark(false,false);}});
    }
    static double contrast(Color a,Color b){double x=lum(a),y=lum(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    static double lum(Color c){return .2126*channel(c.getRed())+.7152*channel(c.getGreen())+.0722*channel(c.getBlue());}
    static double channel(int value){double n=value/255.;return n<=.04045?n/12.92:Math.pow((n+.055)/1.055,2.4);}
}
