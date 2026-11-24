package com.studyagent.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Click-to-select calendar, preserving JTextField compatibility for saved date ranges. */
public final class DatePicker extends JTextField {
    private static final DateTimeFormatter FORMAT=DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private JPopupMenu popup;
    public DatePicker(){
        setEditable(false);setColumns(9);setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));setToolTipText("Bấm để chọn ngày");UiTheme.role(this,"card","text");
        addMouseListener(new MouseAdapter(){@Override public void mousePressed(MouseEvent e){if(SwingUtilities.isLeftMouseButton(e)&&isEnabled())open();}});
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE,0),"calendar");getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"calendar");getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN,InputEvent.ALT_DOWN_MASK),"calendar");
        getActionMap().put("calendar",new AbstractAction(){@Override public void actionPerformed(ActionEvent e){if(isEnabled())open();}});
    }
    public LocalDate getDate(){try{return LocalDate.parse(getText(),FORMAT);}catch(RuntimeException e){return LocalDate.now(com.studyagent.model.StudyProgress.ZONE);}}
    public void setDate(LocalDate date){setText(date.format(FORMAT));}
    @Override protected void paintComponent(Graphics graphics){super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(UiTheme.color("muted"));int x=getWidth()-23,y=getHeight()/2-6;g.drawRoundRect(x,y,14,13,3,3);g.drawLine(x,y+4,x+14,y+4);g.drawLine(x+4,y-2,x+4,y+2);g.drawLine(x+10,y-2,x+10,y+2);g.dispose();}
    private void open(){if(popup!=null&&popup.isVisible()){popup.setVisible(false);return;}popup=createPopup();popup.show(this,0,getHeight());}
    JPopupMenu createPopup(){
        LocalDate selected=getDate();JPopupMenu menu=new JPopupMenu();JPanel root=new UiKit.Card(new BorderLayout(0,10));root.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        JComboBox<String> months=new JComboBox<>();for(int m=1;m<=12;m++)months.addItem("Tháng "+m);months.setSelectedIndex(selected.getMonthValue()-1);
        JSpinner year=new JSpinner(new SpinnerNumberModel(selected.getYear(),1,9999,1));year.setEditor(new JSpinner.NumberEditor(year,"0000"));year.setPreferredSize(new Dimension(82,30));
        JPanel days=UiKit.transparent(new GridLayout(0,7,4,4));
        Runnable rebuild=()->{
            days.removeAll();for(String name:new String[]{"T2","T3","T4","T5","T6","T7","CN"})days.add(UiKit.label(name,11,true,"muted"));
            YearMonth month=YearMonth.of((int)year.getValue(),months.getSelectedIndex()+1);int offset=month.atDay(1).getDayOfWeek().getValue()-1;
            for(int i=0;i<offset;i++)days.add(new JLabel(""));
            for(int d=1;d<=month.lengthOfMonth();d++){
                LocalDate date=month.atDay(d);JButton button=UiKit.button(Integer.toString(d),date.equals(selected),()->{setDate(date);menu.setVisible(false);postActionEvent();});button.putClientProperty("theme.compact",true);button.setPreferredSize(new Dimension(34,30));button.setToolTipText(date.format(FORMAT));days.add(button);
            }
            UiTheme.apply(root);days.revalidate();days.repaint();menu.pack();
        };
        JButton previous=UiKit.button("‹",false,()->{YearMonth month=YearMonth.of((int)year.getValue(),months.getSelectedIndex()+1).minusMonths(1);if(month.getYear()>=1){year.setValue(month.getYear());months.setSelectedIndex(month.getMonthValue()-1);}});
        JButton next=UiKit.button("›",false,()->{YearMonth month=YearMonth.of((int)year.getValue(),months.getSelectedIndex()+1).plusMonths(1);if(month.getYear()<=9999){year.setValue(month.getYear());months.setSelectedIndex(month.getMonthValue()-1);}});
        previous.putClientProperty("theme.compact",true);next.putClientProperty("theme.compact",true);
        JPanel header=UiKit.transparent(new FlowLayout(FlowLayout.CENTER,5,0));header.add(previous);header.add(months);header.add(year);header.add(next);root.add(header,BorderLayout.NORTH);root.add(days);
        root.add(UiKit.button("Hôm nay",false,()->{setDate(LocalDate.now(com.studyagent.model.StudyProgress.ZONE));menu.setVisible(false);postActionEvent();}),BorderLayout.SOUTH);
        months.addActionListener(e->rebuild.run());year.addChangeListener(e->rebuild.run());menu.add(root);rebuild.run();return menu;
    }
}
