package com.studyagent.ui;

import com.studyagent.model.*;
import com.studyagent.repository.*;
import javax.swing.*;
import java.awt.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.*;

/** Dashboard derived only from saved data; no sample metrics and no AI calls. */
public final class HomePanel extends JPanel {
    private final long chatId;
    private final StudyRepository repository;
    private final ReviewRepository reviews;
    private final BiConsumer<String,Integer> learn;
    private final Consumer<String> navigate;
    private final Supplier<FocusTimeRepository.Summary> focusTime;
    private JLabel todayTime,totalTime;
    public HomePanel(long chatId,StudyRepository repository,BiConsumer<String,Integer> learn,Consumer<String> navigate){
        this(chatId,repository,learn,navigate,()->new FocusTimeRepository().summary(chatId,LocalDate.now()));
    }
    public HomePanel(long chatId,StudyRepository repository,BiConsumer<String,Integer> learn,Consumer<String> navigate,Supplier<FocusTimeRepository.Summary> focusTime){
        super(new BorderLayout());this.chatId=chatId;this.repository=repository;this.reviews=new ReviewRepository();this.learn=learn;this.navigate=navigate;this.focusTime=focusTime;setOpaque(false);refresh();
    }
    public void refresh(){
        removeAll();JPanel stack=UiKit.transparent(new GridBagLayout());GridBagConstraints gc=new GridBagConstraints();gc.gridx=0;gc.weightx=1;gc.fill=GridBagConstraints.HORIZONTAL;gc.insets=new Insets(0,0,20,0);
        JPanel title=UiKit.transparent(new BorderLayout(8,8));JPanel words=UiKit.transparent(new GridLayout(2,1,0,7));words.add(UiKit.label("Hôm nay, mình học gì?",28,true,"text"));words.add(UiKit.label("Lịch học, tiến độ và tài liệu đã lưu của bạn.",14,false,"muted"));title.add(words);
        title.add(UiKit.label(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, dd/MM",Locale.forLanguageTag("vi-VN"))),12,false,"muted"),BorderLayout.EAST);addRow(stack,title,gc);
        var books=repository.getAllTextbooks(chatId);Map<String,List<WeeklyCurriculum>> curricula=new LinkedHashMap<>();Map<String,Integer> completed=new LinkedHashMap<>();
        WeeklyCurriculum next=null;int total=0,done=0,ready=0;
        for(var book:books){var plans=repository.getWeeklyCurricula(chatId,book.getSubjectName());curricula.put(book.getSubjectName(),plans);int count=0;
            for(var plan:plans){total++;if("READY".equals(plan.getContentStatus()))ready++;
                boolean finished=reviews.load(chatId,LessonReviewPanel.lessonKey(book.getSubjectName(),book.getContent(),plan)).completed();
                if(finished){done++;count++;}else if(next==null)next=plan;
            }completed.put(book.getSubjectName(),count);
        }
        UiKit.Card hero=new UiKit.Card(new BorderLayout(20,8));UiTheme.role(hero,"tint","text");JPanel heroText=UiKit.transparent(new GridLayout(3,1,0,8));heroText.add(UiKit.label(next==null?"KHÔNG GIAN HỌC TẬP":"BÀI HỌC TIẾP THEO",11,true,"accent"));
        heroText.add(UiKit.label(next==null?(books.isEmpty()?"Bắt đầu với tài liệu của bạn":"Bạn đã hoàn thành các bài đã thêm"):next.getSubjectName(),24,true,"text"));
        heroText.add(UiKit.label(next==null?"Thêm giáo trình hoặc chọn một môn để ôn lại.":shorten(next.getChapterTitle(),88),14,false,"muted"));hero.add(heroText);
        WeeklyCurriculum selected=next;JButton resume=UiKit.button(next==null?"Thêm tài liệu":"Tiếp tục học  →",true,()->{if(selected==null)navigate.accept("input");else learn.accept(selected.getSubjectName(),selected.getWeekNumber());});
        JPanel action=UiKit.transparent(new FlowLayout(FlowLayout.RIGHT,0,22));action.add(resume);hero.add(action,BorderLayout.EAST);addRow(stack,hero,gc);
        var sessions=repository.getStudySessions(chatId);String today=LocalDate.now().toString();long todayCount=sessions.stream().filter(s->today.equals(s.getScheduledDate())&&!s.isCompleted()).count();
        JPanel stats=UiKit.transparent(new GridLayout(1,4,16,0));stats.add(stat("CA TỰ HỌC HÔM NAY",Long.toString(todayCount),"Chọn nội dung khi vào học"));stats.add(stat("TIẾN ĐỘ HỌC",done+" / "+total,ready+" đề cương sẵn sàng"));
        todayTime=UiKit.label("0 giây",25,true,"text");totalTime=UiKit.label("0 giây",25,true,"text");
        stats.add(timeStat("THỜI GIAN HỌC HÔM NAY",todayTime,"Pomodoro tập trung đã chạy"));stats.add(timeStat("TỔNG THỜI GIAN HỌC",totalTime,"Không tính nghỉ và tạm dừng"));refreshFocusTime();addRow(stack,stats,gc);
        JPanel subjectHeader=UiKit.transparent(new BorderLayout());subjectHeader.add(UiKit.label("Các môn của bạn",18,true,"text"));subjectHeader.add(UiKit.button("Xem đề cương  →",false,()->navigate.accept("syllabus")),BorderLayout.EAST);addRow(stack,subjectHeader,gc);
        if(books.isEmpty()){
            UiKit.Card empty=new UiKit.Card(new BorderLayout(10,12));empty.add(UiKit.label("Chưa có giáo trình. Thêm tài liệu để tạo đề cương và ôn tập.",14,false,"muted"));empty.add(UiKit.button("Thêm tài liệu",true,()->navigate.accept("input")),BorderLayout.EAST);addRow(stack,empty,gc);
        }else{
            JPanel subjects=UiKit.transparent(new GridLayout(0,3,18,18));
            for(var book:books){String subject=book.getSubjectName();var plans=curricula.get(subject);UiKit.Card card=new UiKit.Card(new BorderLayout(8,12));JPanel details=UiKit.transparent(new GridLayout(0,1,0,10));
                JLabel name=UiKit.label(shorten(subject,34),17,true,"text");name.setToolTipText(subject);details.add(name);details.add(UiKit.label(plans.size()+" chương/bài · "+completed.get(subject)+" đã học",12,false,"muted"));
                SubjectColors.decorate(name,subject,false);
                JProgressBar bar=new JProgressBar(0,Math.max(1,plans.size()));bar.setValue(completed.get(subject));bar.setPreferredSize(new Dimension(100,6));SubjectColors.decorate(bar,subject,false);details.add(bar);card.add(details);
                int number=plans.isEmpty()?1:plans.get(0).getWeekNumber();card.add(UiKit.button("Mở môn  →",false,()->learn.accept(subject,number)),BorderLayout.SOUTH);subjects.add(card);
            }addRow(stack,subjects,gc);
        }
        var upcoming=sessions.stream().filter(s->!s.isCompleted()&&s.getScheduledDate()!=null).filter(s->{try{return LocalDateTime.of(LocalDate.parse(s.getScheduledDate()),LocalTime.parse(s.getEndTime())).isAfter(LocalDateTime.now());}catch(RuntimeException e){return false;}})
                .min(Comparator.comparing(StudySession::getScheduledDate).thenComparing(StudySession::getStartTime)).orElse(null);
        UiKit.Card schedule=new UiKit.Card(new BorderLayout(20,10));JPanel scheduleText=UiKit.transparent(new GridLayout(0,1,0,8));scheduleText.add(UiKit.label("Lịch tự học tiếp theo",16,true,"text"));
        scheduleText.add(UiKit.label(upcoming==null?"Chưa có ca sắp tới. Bạn vẫn có thể học bất cứ lúc nào.":upcoming.getSubjectName()+"  ·  "+LocalDate.parse(upcoming.getScheduledDate()).format(DateTimeFormatter.ofPattern("dd/MM"))+"  ·  "+upcoming.getStartTime()+" – "+upcoming.getEndTime(),14,false,"muted"));schedule.add(scheduleText);schedule.add(UiKit.button("Xem lịch  →",false,()->navigate.accept("schedule")),BorderLayout.EAST);addRow(stack,schedule,gc);
        gc.gridy++;gc.weighty=1;gc.insets=new Insets(0,0,0,0);stack.add(Box.createVerticalGlue(),gc);
        add(UiKit.scroll(stack));UiTheme.apply(this);revalidate();repaint();
    }
    private static UiKit.Card stat(String title,String value,String hint){UiKit.Card c=new UiKit.Card(new BorderLayout(0,10));c.add(UiKit.label(title,11,true,"muted"),BorderLayout.NORTH);c.add(UiKit.label(value,29,true,"text"));c.add(UiKit.label(hint,12,false,"muted"),BorderLayout.SOUTH);return c;}
    private static UiKit.Card timeStat(String title,JLabel value,String hint){UiKit.Card c=new UiKit.Card(new BorderLayout(0,10));c.add(UiKit.label(title,11,true,"muted"),BorderLayout.NORTH);c.add(value);c.add(UiKit.label(hint,12,false,"muted"),BorderLayout.SOUTH);return c;}
    public void refreshFocusTime(){if(todayTime==null)return;var time=focusTime.get();todayTime.setText(FocusTimeTracker.format(time.todaySeconds()));totalTime.setText(FocusTimeTracker.format(time.totalSeconds()));}
    private static void addRow(JPanel stack,Component child,GridBagConstraints gc){gc.gridy++;stack.add(child,gc);}
    private static String shorten(String text,int n){return text.length()>n?text.substring(0,n-1)+"…":text;}
}
