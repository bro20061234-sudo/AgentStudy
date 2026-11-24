package com.studyagent.ui;

import javax.swing.*;
import java.awt.*;

public final class UiKit {
    private UiKit(){}
    public static class Card extends JPanel {
        public Card(LayoutManager layout){super(layout);setOpaque(false);UiTheme.role(this,"card","text");setBorder(BorderFactory.createEmptyBorder(20,22,20,22));}
        @Override protected void paintComponent(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(getBackground());g.fillRoundRect(0,0,getWidth(),getHeight(),24,24);g.dispose();super.paintComponent(graphics);}
    }
    public static JLabel label(String value,int size,boolean bold,String color){JLabel l=new JLabel(value);l.setFont(new Font("Segoe UI",bold?Font.BOLD:Font.PLAIN,size));UiTheme.role(l,null,color);return l;}
    public static JButton button(String value,boolean primary,Runnable action){JButton b=new JButton(value);b.setFont(new Font("Segoe UI",Font.BOLD,13));if(primary)UiTheme.primary(b);else UiTheme.role(b,"card","text");b.addActionListener(e->action.run());return b;}
    public static JPanel transparent(LayoutManager layout){JPanel p=new JPanel(layout);p.setOpaque(false);return p;}
    public static JScrollPane scroll(Component component){JScrollPane pane=new JScrollPane(component);pane.setBorder(BorderFactory.createEmptyBorder());pane.getVerticalScrollBar().setUnitIncrement(20);return pane;}
}
