package com.studyagent.ui;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Shared palette for every screen and popup. Preferences stay beside application data. */
public final class UiTheme {
    private static final Path SETTINGS=com.studyagent.AppPaths.resolve("data","appearance.properties");
    private static boolean dark=readDark(SETTINGS);
    private static boolean installed;
    private UiTheme(){}
    public static boolean isDark(){return dark;}
    public static String hex(String role){return String.format("#%06x",color(role).getRGB()&0xffffff);}
    public static Color color(String role){return paletteColor(role,dark);}
    private static Color paletteColor(String role,boolean night){return Color.decode(switch(role){
        case "app"->night?"#10151f":"#f4f6fa";
        case "card"->night?"#1b2331":"#ffffff";
        case "soft"->night?"#273246":"#edf1f6";
        case "line"->night?"#41516a":"#c9d3e0";
        case "text"->night?"#edf2fa":"#172235";
        case "muted"->night?"#b2c0d4":"#506078";
        case "accent"->night?"#9cbcff":"#244fb0";
        case "primary"->night?"#355fb5":"#2653b7";
        case "tint"->night?"#293c5d":"#e7efff";
        case "green"->night?"#89dbb6":"#17664f";
        case "greenSoft"->night?"#1c3833":"#e5f4ed";
        case "warning"->night?"#f0ca87":"#805600";
        case "warningSoft"->night?"#3c3121":"#fff2d5";
        case "danger"->night?"#ffa4ae":"#b62c41";
        case "classBg"->night?"#203757":"#e5efff";
        case "classText"->night?"#dbe8ff":"#193e79";
        case "studyBg"->night?"#1c3833":"#e6f3ec";
        case "studyText"->night?"#c5f1dc":"#17553d";
        case "todayBg"->night?"#172231":"#f7faff";
        case "white"->"#ffffff";
        default->night?"#edf2fa":"#172235";
    });}
    public static boolean readDark(Path file){Properties p=new Properties();try(var in=Files.newInputStream(file)){p.load(in);}catch(IOException ignored){}return Boolean.parseBoolean(p.getProperty("dark","false"));}
    public static void writeDark(Path file,boolean value)throws IOException{
        if(file.toAbsolutePath().getParent()!=null)Files.createDirectories(file.toAbsolutePath().getParent());
        Properties p=new Properties();p.setProperty("dark",Boolean.toString(value));
        try(var out=Files.newOutputStream(file)){p.store(out,"Study Agent appearance");}
    }
    public static void setDark(boolean value,boolean persist){
        dark=value;installDefaults();
        if(persist)try{writeDark(SETTINGS,value);}catch(IOException e){throw new IllegalStateException("Đã đổi giao diện nhưng chưa lưu được lựa chọn: "+e.getMessage(),e);}
    }
    public static void install(){if(!installed){installed=true;installDefaults();}}
    private static void installDefaults(){
        Font font=new Font("Segoe UI",Font.PLAIN,15);
        for(String type:new String[]{"Button","ToggleButton","Label","TextField","TextArea","EditorPane","ComboBox","List","TabbedPane","CheckBox","RadioButton","OptionPane","Menu","MenuItem","ToolTip","Spinner"}){
            UIManager.put(type+".font",font);UIManager.put(type+".background",color("card"));UIManager.put(type+".foreground",color("text"));
        }
        for(String type:new String[]{"Panel","Viewport","ScrollPane","SplitPane","OptionPane"})UIManager.put(type+".background",color("app"));
        for(String type:new String[]{"Menu","MenuItem","CheckBoxMenuItem","RadioButtonMenuItem"}){
            UIManager.put(type+".selectionBackground",color("tint"));UIManager.put(type+".selectionForeground",color("text"));UIManager.put(type+".disabledForeground",color("muted"));
        }
        UIManager.put("PopupMenu.background",color("card"));
        UIManager.put("Spinner.border",new RoundBorder(1));
        UIManager.put("TextField.inactiveForeground",color("muted"));UIManager.put("ComboBox.disabledForeground",color("muted"));
        UIManager.put("ButtonUI",ButtonUI.class.getName());UIManager.put("ScrollBarUI",ScrollUI.class.getName());UIManager.put("ComboBoxUI",ComboUI.class.getName());
        UIManager.put("TabbedPaneUI",TabsUI.class.getName());UIManager.put("ProgressBarUI",ProgressUI.class.getName());
        UIManager.put("Button.border",BorderFactory.createEmptyBorder(10,15,10,15));UIManager.put("Button.disabledText",color("muted"));
        UIManager.put("TextField.caretForeground",color("text"));UIManager.put("TextField.selectionBackground",color("tint"));UIManager.put("TextField.selectionForeground",color("text"));
        UIManager.put("List.selectionBackground",color("tint"));UIManager.put("List.selectionForeground",color("accent"));
        UIManager.put("TabbedPane.selected",color("tint"));UIManager.put("TabbedPane.contentAreaColor",color("app"));UIManager.put("TabbedPane.focus",color("accent"));
        UIManager.put("ComboBox.selectionBackground",color("tint"));UIManager.put("ComboBox.selectionForeground",color("text"));
        UIManager.put("ProgressBar.foreground",color("accent"));UIManager.put("ProgressBar.background",color("line"));
        UIManager.put("ToolTip.background",color("soft"));UIManager.put("ToolTip.foreground",color("text"));
        UIManager.put("ScrollPane.border",BorderFactory.createEmptyBorder());UIManager.put("SplitPane.border",BorderFactory.createEmptyBorder());
        UIManager.put("TextField.border",new RoundBorder());UIManager.put("ComboBox.border",new RoundBorder());
    }
    public static <T extends JComponent>T role(T component,String background,String foreground){
        if(background!=null){component.putClientProperty("theme.bg",background);component.setBackground(color(background));}
        if(foreground!=null){component.putClientProperty("theme.fg",foreground);component.setForeground(color(foreground));}return component;
    }
    public static JButton primary(JButton button){button.putClientProperty("theme.primary",true);return role(button,"primary","white");}
    public static void apply(Component component){
        if(component instanceof JComponent jc){
            boolean primary=Boolean.TRUE.equals(jc.getClientProperty("theme.primary"));
            if(jc instanceof JButton && jc.getClientProperty("theme.primary")==null){
                Color b=jc.getBackground();primary=b!=null&&(b.equals(new Color(79,70,229))||b.equals(paletteColor("primary",false))||b.equals(paletteColor("primary",true)));
                if(primary){jc.putClientProperty("theme.primary",true);jc.putClientProperty("theme.bg","primary");jc.putClientProperty("theme.fg","white");}
            }
            String bg=(String)jc.getClientProperty("theme.bg"),fg=(String)jc.getClientProperty("theme.fg");
            if(bg==null){bg=infer(jc.getBackground(),false);jc.putClientProperty("theme.bg",bg);}
            if(fg==null){fg=infer(jc.getForeground(),true);jc.putClientProperty("theme.fg",fg);}
            jc.setBackground(color(bg));jc.setForeground(color(fg));
            if(jc instanceof JButton button){if(!(button.getUI() instanceof ButtonUI))button.setUI(new ButtonUI());button.setFocusPainted(false);button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));button.setOpaque(false);button.setBorder(Boolean.TRUE.equals(button.getClientProperty("theme.compact"))?BorderFactory.createEmptyBorder(6,8,6,8):BorderFactory.createEmptyBorder(10,14,10,14));}
            if(jc instanceof JTextComponent text){text.setCaretColor(color("text"));text.setSelectionColor(color("tint"));text.setSelectedTextColor(color("text"));}
            if(jc instanceof JRadioButton radio){radio.setOpaque(false);}
            if(jc instanceof JList<?> list){list.setSelectionBackground(color("tint"));list.setSelectionForeground(color("accent"));}
            if(jc instanceof JScrollPane scroll){scroll.setBorder(BorderFactory.createEmptyBorder());scroll.getVerticalScrollBar().setUI(new ScrollUI());scroll.getHorizontalScrollBar().setUI(new ScrollUI());scroll.getVerticalScrollBar().setUnitIncrement(20);}
            if(jc instanceof JComboBox<?> combo && !Objects.equals(combo.getClientProperty("theme.combo.dark"),dark)){combo.setUI(new ComboUI());combo.putClientProperty("theme.combo.dark",dark);}
            if(jc instanceof JTabbedPane tabs&&!(tabs.getUI() instanceof TabsUI))tabs.setUI(new TabsUI());
            if(jc instanceof JProgressBar bar){bar.setUI(new ProgressUI());bar.setOpaque(false);bar.setBorder(BorderFactory.createEmptyBorder());bar.setForeground(color("accent"));bar.setBackground(color("line"));}
            if(jc.getBorder() instanceof LineBorder || jc.getBorder() instanceof CompoundBorder)jc.setBorder(recolor(jc.getBorder()));
            if(jc instanceof JEditorPane editor)LessonHtml.refreshTheme(editor);
            SubjectColors.apply(jc);
        }
        if(component instanceof Container container)for(Component child:container.getComponents())apply(child);
        component.repaint();
    }
    private static Border recolor(Border border){
        if(border instanceof LineBorder)return new RoundBorder(1);
        if(border instanceof CompoundBorder b)return BorderFactory.createCompoundBorder(recolor(b.getOutsideBorder()),recolor(b.getInsideBorder()));
        return border;
    }
    private static String infer(Color value,boolean foreground){
        if(value==null)return foreground?"text":"app";
        String[] roles=foreground?new String[]{"text","muted","accent","green","warning","danger","classText","studyText"}:new String[]{"app","card","soft","line","tint","greenSoft","warningSoft","classBg","studyBg","todayBg"};
        // A component can first be styled just after the mode changes, while still holding
        // colors from the previous palette. Resolve either palette to the same semantic role.
        for(String role:roles)if(value.equals(paletteColor(role,false))||value.equals(paletteColor(role,true)))return role;
        int rgb=value.getRGB()&0xffffff;
        if(foreground){
            if(rgb==0x64748b||rgb==0x94a3b8||rgb==0x788299)return "muted";
            if(rgb==0x4f46e5||rgb==0x4338ca||rgb==0x312e81)return "accent";
            if(rgb==0x0d9488||rgb==0x166534||rgb==0x0f766e)return "green";
            if(rgb==0x92640e||rgb==0x92400e||rgb==0xb45309)return "warning";
            if(rgb==0xdc2626)return "danger";return "text";
        }
        if(rgb==0xffffff)return "card";
        if(rgb==0xf1f5f9||rgb==0xf5f6fa)return "app";
        if(rgb==0xf8fafc||rgb==0xf8f9fc)return "soft";
        if(rgb==0xe2e8f0||rgb==0xe7eaf2)return "line";
        if(rgb==0xeef2ff||rgb==0xe0e7ff)return "tint";
        if(rgb==0xdcfce7||rgb==0xf0fdfa||rgb==0xbbf7d0||rgb==0xccfbf1)return "greenSoft";
        if(rgb==0xfef3c7||rgb==0xfde68a)return "warningSoft";
        return "card";
    }
    public static class RoundBorder extends AbstractBorder {
        private final int inset;
        public RoundBorder(){this(8);}
        public RoundBorder(int inset){this.inset=inset;}
        @Override public Insets getBorderInsets(Component c){return new Insets(inset,inset,inset,inset);}
        @Override public Insets getBorderInsets(Component c,Insets i){i.set(inset,inset,inset,inset);return i;}
        @Override public void paintBorder(Component c,Graphics graphics,int x,int y,int w,int h){Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(color("line"));g.drawRoundRect(x,y,w-1,h-1,12,12);g.dispose();}
    }
    public static class ButtonUI extends BasicButtonUI {
        public static ComponentUI createUI(JComponent c){return new ButtonUI();}
        @Override public void update(Graphics graphics,JComponent component){
            AbstractButton b=(AbstractButton)component;Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill=b.isEnabled()?b.getBackground():color("soft");
            if(b.getModel().isRollover()&&b.isEnabled())fill=blend(fill,color("text"),dark?.10:.06);
            if(b.getModel().isPressed())fill=blend(fill,color("text"),dark?.18:.12);
            g.setColor(fill);g.fillRoundRect(0,0,b.getWidth(),b.getHeight(),16,16);
            if(!Boolean.TRUE.equals(b.getClientProperty("theme.primary"))&&b.getClientProperty("navKey")==null){g.setColor(color("line"));g.drawRoundRect(0,0,b.getWidth()-1,b.getHeight()-1,16,16);}g.dispose();paint(graphics,component);
        }
        @Override protected void paintText(Graphics g,AbstractButton button,Rectangle rect,String text){g.setFont(button.getFont());g.setColor(button.isEnabled()?button.getForeground():color("muted"));g.drawString(text,rect.x,rect.y+g.getFontMetrics().getAscent());}
    }
    private static Color blend(Color a,Color b,double ratio){return new Color((int)(a.getRed()*(1-ratio)+b.getRed()*ratio),(int)(a.getGreen()*(1-ratio)+b.getGreen()*ratio),(int)(a.getBlue()*(1-ratio)+b.getBlue()*ratio));}
    public static class ScrollUI extends BasicScrollBarUI {
        public static ComponentUI createUI(JComponent c){return new ScrollUI();}
        @Override protected void configureScrollBarColors(){thumbColor=color("line");trackColor=color("card");}
        @Override protected JButton createDecreaseButton(int o){return zero();}
        @Override protected JButton createIncreaseButton(int o){return zero();}
        private JButton zero(){JButton b=new JButton();b.setPreferredSize(new Dimension(0,0));return b;}
        @Override protected void paintThumb(Graphics g,JComponent c,Rectangle r){g.setColor(color("line"));g.fillRoundRect(r.x+2,r.y+2,Math.max(4,r.width-4),Math.max(4,r.height-4),8,8);}
        @Override public Dimension getPreferredSize(JComponent c){return new Dimension(10,10);}
    }
    public static class ComboUI extends BasicComboBoxUI {
        public static ComponentUI createUI(JComponent c){return new ComboUI();}
        @Override protected JButton createArrowButton(){return new BasicArrowButton(SwingConstants.SOUTH,color("card"),color("card"),color("muted"),color("card"));}
    }
    public static class TabsUI extends BasicTabbedPaneUI {
        public static ComponentUI createUI(JComponent c){return new TabsUI();}
        @Override protected Insets getContentBorderInsets(int placement){return new Insets(10,0,0,0);}
        @Override protected Insets getTabInsets(int placement,int index){return new Insets(10,16,10,16);}
        @Override protected void paintContentBorder(Graphics g,int placement,int selected){}
        @Override protected void paintTabBorder(Graphics g,int p,int i,int x,int y,int w,int h,boolean selected){}
        @Override protected void paintFocusIndicator(Graphics g,int p,Rectangle[] r,int i,Rectangle icon,Rectangle text,boolean selected){}
        @Override protected void paintTabBackground(Graphics graphics,int p,int i,int x,int y,int w,int h,boolean selected){Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(color(selected?"tint":"app"));g.fillRoundRect(x,y,w-3,h-3,14,14);g.dispose();}
        @Override protected void paintText(Graphics g,int p,Font font,FontMetrics fm,int index,String title,Rectangle rect,boolean selected){g.setFont(font);g.setColor(color(selected?"accent":"muted"));g.drawString(title,rect.x,rect.y+fm.getAscent());}
    }
    public static class ProgressUI extends BasicProgressBarUI {
        public static ComponentUI createUI(JComponent c){return new ProgressUI();}
        @Override protected void paintDeterminate(Graphics graphics,JComponent component){Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);int y=progressBar.isStringPainted()?component.getHeight()-6:component.getHeight()/2-3;g.setColor(color("line"));g.fillRoundRect(0,y,component.getWidth(),6,6,6);g.setColor(progressBar.getForeground());g.fillRoundRect(0,y,(int)(component.getWidth()*progressBar.getPercentComplete()),6,6,6);if(progressBar.isStringPainted()){g.setFont(component.getFont());g.setColor(color("muted"));g.drawString(progressBar.getString(),4,Math.max(12,y-5));}g.dispose();}
    }
}
