package com.studyagent.ui;

import com.studyagent.repository.ReviewRepository;
import javax.swing.*;
import java.awt.*;
import java.time.format.DateTimeFormatter;

/** Immutable attempt snapshots show every question, including those left blank. */
public final class QuizResults {
    private QuizResults(){}
    public static String markdown(ReviewRepository.State state){
        int correct=ReviewRepository.score(state),total=state.questions().size(),blank=0;for(int answer:state.answers())if(answer<0)blank++;
        StringBuilder body=new StringBuilder("**"+correct+" / "+total+" câu đúng** · "+blank+" câu chưa làm\n\n");
        for(int i=0;i<total;i++){var q=state.questions().get(i);int selected=i<state.answers().length?state.answers()[i]:-1;boolean right=selected>=0&&selected==ReviewRepository.correctIndex(q);
            body.append("## Câu ").append(i+1).append(right?" · Đúng":selected<0?" · Chưa làm":" · Sai").append("\n").append(q.getQuestion()).append("\n\n");
            for(String option:q.getOptions())body.append("- ").append(option).append('\n');
            body.append("\n**Bạn chọn:** ").append(selected<0?"Chưa trả lời":q.getOptions().get(selected)).append("\n\n**Đáp án đúng:** ").append(q.getOptions().get(ReviewRepository.correctIndex(q)))
                    .append("\n\n**Giải thích:** ").append(java.util.Objects.toString(q.getExplanation(),"Chưa có giải thích.")).append("\n\n");
        }return body.toString();
    }
    public static void showHistory(Component parent,ReviewRepository reviews,long chatId,String key){
        var attempts=reviews.history(chatId,key);if(attempts.isEmpty()){JOptionPane.showMessageDialog(parent,"Chưa có bài đã nộp.");return;}
        String[] labels=attempts.stream().map(a->{String date;try{date=java.time.LocalDateTime.parse(a.completedAt()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));}catch(RuntimeException e){date=a.completedAt();}return date+" · "+a.correct()+"/"+a.total()+" đúng";}).toArray(String[]::new);
        JList<String> list=new JList<>(labels);list.setSelectedIndex(0);JPanel panel=new JPanel(new BorderLayout(0,12));panel.add(new JScrollPane(list));
        JButton view=UiKit.button("Xem bài làm và giải thích",true,()->{int selected=list.getSelectedIndex();if(selected>=0)show(parent,reviews.attempt(chatId,attempts.get(selected).id()),"Bài làm đã lưu");});panel.add(view,BorderLayout.SOUTH);panel.setPreferredSize(new Dimension(580,350));UiTheme.apply(panel);JOptionPane.showMessageDialog(parent,panel,"Lịch sử làm bài",JOptionPane.PLAIN_MESSAGE);
    }
    public static void show(Component parent,ReviewRepository.State state,String title){var editor=LessonHtml.pane();LessonHtml.show(editor,title,markdown(state));var scroll=UiKit.scroll(editor);scroll.setPreferredSize(new Dimension(850,600));UiTheme.apply(scroll);JOptionPane.showMessageDialog(parent,scroll,title,JOptionPane.PLAIN_MESSAGE);}
}
