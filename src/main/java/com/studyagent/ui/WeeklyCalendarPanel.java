package com.studyagent.ui;

import com.studyagent.model.*;
import com.studyagent.scheduler.ScheduleBlocks;
import com.studyagent.scheduler.StudyOptimizer;
import javax.swing.*;
import java.awt.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** Compact week view. Display groups retain each original session for editing and progress. */
public final class WeeklyCalendarPanel extends JPanel implements Scrollable {
    private static final int HEADER=54, GUTTER=48;
    private final boolean study;
    private final Consumer<StudySession> learn,edit,toggle;
    private int from,to;
    private final List<Day> days=new ArrayList<>();
    private record Event(String title,String start,String end,String detail,List<StudySession> sessions){
        boolean completed(){return !sessions.isEmpty()&&sessions.stream().allMatch(StudySession::isCompleted);}
    }
    public WeeklyCalendarPanel(boolean study,Consumer<StudySession> learn,Consumer<StudySession> edit,Consumer<StudySession> toggle){
        super(null);this.study=study;this.learn=learn;this.edit=edit;this.toggle=toggle;
        from=study?20:7;to=study?22:18;setOpaque(false);
    }
    public void setWeek(LocalDate monday,LocalDate boundStart,LocalDate boundEnd,List<TimetableEntry> entries,List<StudySession> sessions){
        removeAll();days.clear();from=study?20:7;to=study?22:18;int earliest=24,latest=0;
        for(int i=0;i<7;i++){
            LocalDate date=monday.plusDays(i);List<Event> events=new ArrayList<>();
            boolean inBounds=(boundStart==null||!date.isBefore(boundStart))&&(boundEnd==null||!date.isAfter(boundEnd));
            if(inBounds&&study){
                var active=sessions.stream().filter(s->date.toString().equals(s.getScheduledDate())).toList();
                for(var block:ScheduleBlocks.mergeStudySessions(active)){
                    long done=block.sessions().stream().filter(StudySession::isCompleted).count();
                    String detail=block.sessions().size()>1?block.sessions().size()+" khung giờ · "+done+" đã học":"Chọn bài khi vào học";
                    events.add(new Event(block.subject(),block.start(),block.end(),detail,block.sessions()));
                }
            }
            if(inBounds&&!study){
                var active=entries.stream().filter(e->matchesDay(e.getDayOfWeek(),date.getDayOfWeek())&&StudyOptimizer.isEntryActiveOnDate(e,date)).toList();
                for(var e:ScheduleBlocks.mergeClasses(active))events.add(new Event(e.getSubjectName(),e.getStartTime(),e.getEndTime(),Objects.toString(e.getRoom(),""),List.of()));
            }
            for(var event:events){
                int start=StudyOptimizer.parseMinutes(event.start()),end=StudyOptimizer.parseMinutes(event.end());
                if(start>=0&&end>start){earliest=Math.min(earliest,start/60);latest=Math.max(latest,Math.min(24,(end+59)/60));}
            }
            events.sort(Comparator.comparingInt(e->StudyOptimizer.parseMinutes(e.start())));
            Day day=new Day(date,events);days.add(day);add(day);
        }
        if(latest>earliest){from=study?Math.min(20,earliest):earliest;to=study?Math.max(22,latest):latest;}
        to=Math.max(from+1,to);revalidate();repaint();
    }
    private int gridHeight(){
        int natural=HEADER+(to-from)*(study?224:32);
        // Fit the complete day when the available viewport is shorter, but never stretch
        // a short evening schedule to fill an otherwise tall window.
        if(getParent() instanceof JViewport viewport&&viewport.getExtentSize().height>HEADER+100)
            return Math.min(natural,viewport.getExtentSize().height);
        return natural;
    }
    @Override public void doLayout(){
        int width=Math.max(1,getWidth()-GUTTER),height=Math.min(getHeight(),gridHeight());
        for(int i=0;i<days.size();i++)days.get(i).setBounds(GUTTER+i*width/7,0,Math.max(1,width/7-1),height);
    }
    @Override public Dimension getPreferredSize(){return new Dimension(950,gridHeight());}
    @Override public Dimension getPreferredScrollableViewportSize(){return new Dimension(950,Math.min(470,gridHeight()));}
    @Override public int getScrollableUnitIncrement(Rectangle v,int o,int d){return 24;}
    @Override public int getScrollableBlockIncrement(Rectangle v,int o,int d){return Math.max(24,v.height-48);}
    @Override public boolean getScrollableTracksViewportWidth(){return true;}
    @Override public boolean getScrollableTracksViewportHeight(){return false;}
    @Override protected void paintComponent(Graphics graphics){
        super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();
        g.setColor(UiTheme.color("card"));g.fillRect(0,0,getWidth(),getHeight());
        g.setFont(new Font("Segoe UI",Font.PLAIN,10));g.setColor(UiTheme.color("muted"));
        int height=Math.min(getHeight(),gridHeight()),step=study?30:60;
        for(int minute=from*60;minute<=to*60;minute+=step){
            int y=HEADER+(minute-from*60)*(height-HEADER)/((to-from)*60);
            g.drawString(String.format("%02d:%02d",minute/60,minute%60),4,Math.min(height-2,y+4));
        }
        g.dispose();
    }
    private final class Day extends JPanel {
        private final LocalDate date;
        private final List<Event> events;
        private final List<JButton> buttons=new ArrayList<>();
        Day(LocalDate date,List<Event> events){
            super(null);this.date=date;this.events=events;setOpaque(false);
            for(Event event:events){
                JButton button=new JButton();button.setHorizontalAlignment(SwingConstants.LEFT);
                button.setVerticalAlignment(SwingConstants.CENTER);button.setFont(new Font("Segoe UI",Font.PLAIN,12));
                button.putClientProperty("theme.compact",true);
                SubjectColors.decorate(button,event.title(),true);
                String status=event.sessions().isEmpty()?"":StudyProgress.status(event.sessions(),LocalDateTime.now(StudyProgress.ZONE)).label;
                button.setToolTipText(event.title()+" · "+event.start()+"–"+event.end()+(status.isBlank()?"":" · "+status)+(event.detail().isBlank()?"":" · "+event.detail()));
                if(!event.sessions().isEmpty()){
                    button.addActionListener(a->learn.accept(event.sessions().getFirst()));
                    button.setComponentPopupMenu(sessionMenu(event));
                }else button.addActionListener(a->JOptionPane.showMessageDialog(this,event.title()+"\n"+event.start()+" – "+event.end()+"\n"+event.detail(),"Lịch trên lớp",JOptionPane.PLAIN_MESSAGE));
                buttons.add(button);add(button);
            }
            UiTheme.apply(this);
        }
        private JPopupMenu sessionMenu(Event event){
            JPopupMenu popup=new JPopupMenu();
            JMenuItem start=new JMenuItem("Vào học");start.addActionListener(a->learn.accept(event.sessions().getFirst()));popup.add(start);
            if(event.sessions().size()==1){
                JMenuItem change=new JMenuItem("Sửa khung giờ");change.addActionListener(a->edit.accept(event.sessions().getFirst()));popup.add(change);
            }else{
                JMenu change=new JMenu("Sửa từng ca trong nhóm");
                for(var session:event.sessions()){
                    JMenuItem item=new JMenuItem(session.getStartTime()+" – "+session.getEndTime());
                    item.addActionListener(a->edit.accept(session));change.add(item);
                }
                popup.add(change);
            }
            JMenuItem progress=new JMenuItem(event.completed()?"Đánh dấu chưa học":"Đánh dấu đã học");
            progress.addActionListener(a->{boolean target=!event.completed();for(var session:event.sessions())if(session.isCompleted()!=target)toggle.accept(session);});
            popup.add(progress);UiTheme.apply(popup);return popup;
        }
        @Override public void doLayout(){
            double scale=(getHeight()-HEADER)/(double)((to-from)*60);
            for(int i=0;i<events.size();i++){
                Event event=events.get(i);JButton button=buttons.get(i);
                int start=StudyOptimizer.parseMinutes(event.start()),end=StudyOptimizer.parseMinutes(event.end());
                if(start<0||end<=start){button.setVisible(false);continue;}
                int y=HEADER+(int)((Math.max(from*60,start)-from*60)*scale);
                int height=Math.min(getHeight()-y,Math.max(18,(int)((Math.min(to*60,end)-Math.max(from*60,start))*scale)-2));
                button.setBounds(3,y,Math.max(20,getWidth()-6),height);
                button.setBorder(BorderFactory.createEmptyBorder(2,6,2,6));
                String displayTitle=Objects.toString(event.title(),"");
                if(height<38||study&&height<80){
                    FontMetrics metrics=button.getFontMetrics(button.getFont().deriveFont(Font.BOLD));int available=Math.max(20,getWidth()-24);
                    if(metrics.stringWidth(displayTitle)>available){while(!displayTitle.isEmpty()&&metrics.stringWidth(displayTitle+"…")>available)displayTitle=displayTitle.substring(0,displayTitle.length()-1);displayTitle+="…";}
                }
                String title=LessonHtml.escape(displayTitle),time=LessonHtml.escape(event.start()+"–"+event.end());
                StudyProgress.Status status=event.sessions().isEmpty()?null:StudyProgress.status(event.sessions(),LocalDateTime.now(StudyProgress.ZONE));
                String statusText=status==null?"":status.symbol+" "+status.label;
                String stateColor=UiTheme.hex(status==StudyProgress.Status.COMPLETED?"green":status==StudyProgress.Status.OVERDUE?"danger":"muted");
                String body;
                if(status!=null&&height>=54)body="<b>"+time+"</b><br>"+title+"<br><span style='color:"+stateColor+"'>"+statusText+"</span>";
                else if(status!=null&&height>=36)body="<b>"+title+"</b><br><span style='color:"+stateColor+"'>"+statusText+"</span>";
                else if(status!=null){
                    FontMetrics metrics=button.getFontMetrics(button.getFont());int available=Math.max(0,getWidth()-24-metrics.stringWidth(status.label+" · "));String shortTitle=Objects.toString(event.title(),"");
                    while(!shortTitle.isEmpty()&&metrics.stringWidth(shortTitle+"…")>available)shortTitle=shortTitle.substring(0,shortTitle.length()-1);
                    body="<span style='color:"+stateColor+"'>"+status.label+"</span>"+(shortTitle.isEmpty()?"":" · "+LessonHtml.escape(shortTitle)+"…");
                }else body=height<38?"<b>"+title+"</b>":"<b>"+time+"</b><br>"+title;
                button.setText("<html><div style='width:"+Math.max(35,getWidth()-24)+"px'>"+body+"</div></html>");
            }
        }
        @Override protected void paintComponent(Graphics graphics){
            Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            boolean today=date.equals(LocalDate.now());g.setColor(UiTheme.color(today?"todayBg":"card"));g.fillRect(0,0,getWidth(),getHeight());
            g.setColor(UiTheme.color(today?"tint":"soft"));g.fillRect(0,0,getWidth(),HEADER);
            g.setColor(UiTheme.color("line"));int step=study?30:60;
            for(int minute=from*60;minute<=to*60;minute+=step){int y=HEADER+(minute-from*60)*(getHeight()-HEADER)/((to-from)*60);g.drawLine(0,y,getWidth(),y);}
            g.drawLine(0,HEADER,0,getHeight());
            String day=date.getDayOfWeek()==DayOfWeek.SUNDAY?"CHỦ NHẬT":"THỨ "+(date.getDayOfWeek().getValue()+1);
            g.setFont(new Font("Segoe UI",Font.BOLD,10));g.setColor(UiTheme.color("muted"));g.drawString(day,(getWidth()-g.getFontMetrics().stringWidth(day))/2,18);
            String number=date.format(DateTimeFormatter.ofPattern("dd/MM"));g.setFont(new Font("Segoe UI",Font.BOLD,17));g.setColor(UiTheme.color(today?"accent":"text"));g.drawString(number,(getWidth()-g.getFontMetrics().stringWidth(number))/2,41);g.dispose();
        }
    }
    private static boolean matchesDay(String name,DayOfWeek day){return StudyOptimizer.dayIndex(name)==day.getValue()-1||(day==DayOfWeek.SUNDAY&&"cn".equalsIgnoreCase(Objects.toString(name,"").trim()));}
}
