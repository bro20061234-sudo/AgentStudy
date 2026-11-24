package com.studyagent.ui;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;

/** Stable course colors; the saved hue is shared by calendars, outlines and progress bars. */
public final class SubjectColors {
    private static final String[] LIGHT={"#2755aa","#147252","#986000","#7346b3","#b03559","#126b87","#a64b19","#4c4fb4","#156d6c","#9b3985","#536b16","#45637e"};
    private static final String[] DARK={"#a6c6ff","#8dddb6","#f2ce81","#d0b3ff","#ffb0c3","#91d9ef","#ffc09b","#bfc0ff","#8bddd6","#ecb0e0","#c9df92","#b0cfe5"};
    private static final Registry SAVED=new Registry(com.studyagent.AppPaths.resolve("data","subject-colors.properties"));
    private SubjectColors(){}
    public static String key(String subject){return Normalizer.normalize(Objects.toString(subject,"").trim().toLowerCase(Locale.ROOT),Normalizer.Form.NFC).replaceAll("\\s+"," ");}
    public static Color foreground(String subject){int index=SAVED.index(subject);return Color.decode((UiTheme.isDark()?DARK:LIGHT)[index]);}
    public static Color background(String subject){Color base=UiTheme.color("card"),ink=foreground(subject);double amount=UiTheme.isDark()?.18:.10;return new Color((int)(base.getRed()*(1-amount)+ink.getRed()*amount),(int)(base.getGreen()*(1-amount)+ink.getGreen()*amount),(int)(base.getBlue()*(1-amount)+ink.getBlue()*amount));}
    public static String hex(String subject){return String.format("#%06x",foreground(subject).getRGB()&0xffffff);}
    public static void decorate(JComponent component,String subject,boolean fill){component.putClientProperty("theme.subject",subject);component.putClientProperty("theme.subject.fill",fill);apply(component);}
    static void apply(JComponent component){Object subject=component.getClientProperty("theme.subject");if(!(subject instanceof String name)||name.isBlank())return;if(component instanceof JList<?> list){list.setSelectionForeground(foreground(name));list.setSelectionBackground(background(name));return;}component.setForeground(foreground(name));if(Boolean.TRUE.equals(component.getClientProperty("theme.subject.fill")))component.setBackground(background(name));}

    public static final class Registry {
        private final Path file;private final Properties assigned=new Properties();
        public Registry(Path file){this.file=file;if(file!=null)try(var in=Files.newInputStream(file)){assigned.load(in);}catch(IOException ignored){}}
        public synchronized int index(String subject){
            String name=key(subject);String saved=assigned.getProperty(name);
            if(saved!=null)try{int value=Integer.parseInt(saved);if(value>=0&&value<LIGHT.length)return value;}catch(NumberFormatException ignored){}
            int index=Math.floorMod(name.hashCode(),LIGHT.length);Set<Integer> used=new HashSet<>();
            for(Object value:assigned.values())try{used.add(Integer.parseInt(value.toString()));}catch(NumberFormatException ignored){}
            for(int offset=0;offset<LIGHT.length;offset++){int candidate=(index+offset)%LIGHT.length;if(!used.contains(candidate)){index=candidate;break;}}
            assigned.setProperty(name,Integer.toString(index));
            if(file!=null)try{Files.createDirectories(file.toAbsolutePath().getParent());try(var out=Files.newOutputStream(file)){assigned.store(out,"Course colors");}}catch(IOException e){System.err.println("Không lưu được màu môn học: "+e.getMessage());}
            return index;
        }
    }
}
