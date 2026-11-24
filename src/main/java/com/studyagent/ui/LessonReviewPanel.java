package com.studyagent.ui;

import com.studyagent.ai.GeminiService;
import com.studyagent.model.*;
import com.studyagent.repository.*;
import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;

/** Per-lesson practice bank and durable draft, with a one-question player. */
public class LessonReviewPanel extends JPanel {
    private final long chatId;private final StudyRepository study;private final ReviewRepository reviews;private final GeminiService ai;
    private final JComboBox<String> subjects=new JComboBox<>();
    private final JComboBox<WeeklyCurriculum> lessons=new JComboBox<>();
    private final JComboBox<String> filter=new JComboBox<>(new String[]{"Toàn bộ câu hỏi","Câu sai / chưa làm"});
    private final JSpinner count=new JSpinner(new SpinnerNumberModel(12,1,30,1));
    private final JButton generate=UiKit.button("Tạo thêm câu hỏi",true,()->generate());
    private final JButton complete=UiKit.button("Đánh dấu đã học",false,()->toggleComplete());
    private String sessionSubject;
    private Runnable sessionAction;
    private final JButton completeSession=UiKit.button("Hoàn thành ca học",false,()->{if(sessionAction!=null)sessionAction.run();});
    private final JLabel status=UiKit.label(" ",13,false,"muted");
    private final QuestionPlayer player;
    private WeeklyCurriculum selected;private String key;private ReviewRepository.State state=new ReviewRepository.State(List.of(),new int[0],false,false);
    private List<Integer> visible=List.of();private boolean updating,busy;
    public LessonReviewPanel(long chatId,StudyRepository study,GeminiService ai,JButton ignored){this(chatId,study,ai,ignored,new ReviewRepository());}
    LessonReviewPanel(long chatId,StudyRepository study,GeminiService ai,JButton ignored,ReviewRepository reviews){
        super(new BorderLayout(0,18));this.chatId=chatId;this.study=study;this.ai=ai;this.reviews=reviews;setOpaque(false);
        player=new QuestionPlayer(this::answer,this::submit,this::retry,()->guarded(()->{if(key!=null)QuizResults.showHistory(this,reviews,chatId,key);}));player.setFilter(filter);player.setAnswerPreviewEnabled(true);
        JPanel header=UiKit.transparent(new BorderLayout(10,16));JPanel words=UiKit.transparent(new GridLayout(2,1,0,7));words.add(UiKit.label("Ôn tập theo bài",28,true,"text"));words.add(UiKit.label("Chọn câu hỏi đã lưu, luyện từng câu và xem lời giải sau khi nộp.",15,false,"muted"));header.add(words,BorderLayout.NORTH);
        JPanel controls=UiKit.transparent(new GridLayout(2,1,0,10));JPanel selection=UiKit.transparent(new FlowLayout(FlowLayout.LEFT,8,0));subjects.setPreferredSize(new Dimension(235,39));lessons.setPreferredSize(new Dimension(440,39));selection.add(subjects);selection.add(lessons);
        lessons.setRenderer(new DefaultListCellRenderer(){@Override public Component getListCellRendererComponent(JList<?> l,Object value,int i,boolean active,boolean focus){JLabel label=(JLabel)super.getListCellRendererComponent(l,value,i,active,focus);if(value instanceof WeeklyCurriculum p){String text=p.getChapterTitle();label.setText(text.length()>65?text.substring(0,62)+"…":text);label.setToolTipText(text);}return label;}});
        JPanel actions=UiKit.transparent(new FlowLayout(FlowLayout.LEFT,8,0));actions.add(UiKit.label("Số câu tạo thêm",13,false,"text"));count.setPreferredSize(new Dimension(74,38));actions.add(count);actions.add(generate);
        actions.add(UiKit.button("Xem đề cương",false,()->{if(selected!=null){var editor=LessonHtml.pane();LessonHtml.show(editor,selected.getChapterTitle(),selected.getDetailedSyllabus());var scroll=UiKit.scroll(editor);scroll.setPreferredSize(new Dimension(850,600));UiTheme.apply(scroll);JOptionPane.showMessageDialog(this,scroll,"Đề cương bài học",JOptionPane.PLAIN_MESSAGE);}}));actions.add(complete);
        actions.add(completeSession);completeSession.setVisible(false);
        controls.add(selection);controls.add(actions);header.add(controls);add(header,BorderLayout.NORTH);add(player);add(status,BorderLayout.SOUTH);
        subjects.addActionListener(e->{if(!updating)loadLessons(-1);});lessons.addActionListener(e->{if(!updating)open();});filter.addActionListener(e->{if(!updating){visible=QuizLogic.indices(state,filter.getSelectedIndex()==1);player.resetPosition();render();}});refresh();
    }
    public void refreshTheme(){render();}
    public void setStudySessionAction(String subject,Runnable action){sessionSubject=subject;sessionAction=action;render();}
    public void selectLesson(String subject,int number){if(busy)return;refresh();subjects.setSelectedItem(subject);loadLessons(number);}
    public void refresh(){if(busy)return;guarded(()->{Object previous=subjects.getSelectedItem();int number=selected==null?-1:selected.getWeekNumber();updating=true;try{subjects.removeAllItems();for(var book:study.getAllTextbooks(chatId))subjects.addItem(book.getSubjectName());if(previous!=null)subjects.setSelectedItem(previous);}finally{updating=false;}loadLessons(number);});}
    private void loadLessons(int preferred){guarded(()->{updating=true;try{lessons.removeAllItems();String subject=(String)subjects.getSelectedItem();if(subject!=null)for(var plan:study.prepareChapters(chatId,subject))lessons.addItem(plan);for(int i=0;i<lessons.getItemCount();i++)if(lessons.getItemAt(i).getWeekNumber()==preferred)lessons.setSelectedIndex(i);}finally{updating=false;}open();});}
    private void open(){guarded(()->{selected=(WeeklyCurriculum)lessons.getSelectedItem();var book=selected==null?null:study.getTextbook(chatId,selected.getSubjectName());key=book==null?null:lessonKey(book.getSubjectName(),book.getContent(),selected);state=key==null?new ReviewRepository.State(List.of(),new int[0],false,false):reviews.load(chatId,key);
        if(key!=null&&!state.questions().isEmpty())reviews.addToBank(chatId,selected.getSubjectName(),key,state.questions());
        visible=QuizLogic.indices(state,filter.getSelectedIndex()==1);player.resetPosition();render();});}
    private void render(){generate.setEnabled(selected!=null&&!busy);complete.setEnabled(selected!=null&&!busy);subjects.setEnabled(!busy);lessons.setEnabled(!busy);count.setEnabled(!busy);filter.setEnabled(!busy);complete.setText(state.completed()?"Đã học bài · Bỏ đánh dấu":"Đánh dấu đã học bài");completeSession.setVisible(sessionAction!=null&&selected!=null&&selected.getSubjectName().equalsIgnoreCase(sessionSubject));completeSession.setEnabled(!busy);player.bind(state,visible,busy);status.setText(busy?"Đang tạo câu hỏi từ nguồn của bài…":state.questions().size()+" câu đã lưu · Câu trả lời tự lưu · Có thể nộp khi còn câu bỏ trống.");UiTheme.apply(this);revalidate();repaint();}
    private void answer(int index,int answer){guarded(()->{int[] values=state.answers().clone();values[index]=answer;persist(new ReviewRepository.State(state.questions(),values,false,state.completed()));render();});}
    private void submit(){guarded(()->{if(key==null||visible.isEmpty()||state.submitted())return;var next=new ReviewRepository.State(state.questions(),state.answers(),true,state.completed());reviews.submit(chatId,key,next,QuizLogic.subset(next,visible));state=next;render();});}
    private void retry(){guarded(()->{int[] answers=state.answers().clone();for(int index:visible)answers[index]=-1;persist(new ReviewRepository.State(state.questions(),answers,false,state.completed()));player.resetPosition();render();});}
    private void toggleComplete(){guarded(()->{if(key!=null){persist(new ReviewRepository.State(state.questions(),state.answers(),state.submitted(),!state.completed()));render();}});}
    private void persist(ReviewRepository.State next){reviews.save(chatId,key,next);state=next;}
    private void generate(){if(selected==null||busy)return;WeeklyCurriculum plan=selected;String lessonKey=key;var previous=state;int requested=(int)count.getValue();busy=true;render();
        new SwingWorker<ReviewRepository.State,Void>(){
            protected ReviewRepository.State doInBackground(){
                var generated=ai.generateQuizQuestions(plan.getSubjectName(),plan.getWeekNumber(),"SESSION_REVIEW",plan.getChapterTitle()+"\n"+plan.getDetailedSyllabus()+"\nNguồn:\n"+com.studyagent.parser.LessonSources.sample(Objects.toString(plan.getSourceText(),""),12000)+"\nKhông lặp các câu: "+previous.questions().stream().map(QuizQuestion::getQuestion).collect(java.util.stream.Collectors.joining("; ")),requested);
                ReviewRepository.validate(generated);reviews.addToBank(chatId,plan.getSubjectName(),lessonKey,generated);List<QuizQuestion> combined=new ArrayList<>(previous.questions());Set<String> ids=new HashSet<>();for(var q:combined)ids.add(ReviewRepository.questionId(plan.getSubjectName(),q));for(var q:generated)if(ids.add(ReviewRepository.questionId(plan.getSubjectName(),q)))combined.add(q);
                if(combined.size()==previous.questions().size())throw new IllegalStateException("AI chỉ trả câu trùng. Các câu đã lưu vẫn được giữ.");int[] values=Arrays.copyOf(previous.answers(),combined.size());Arrays.fill(values,previous.answers().length,values.length,-1);var next=new ReviewRepository.State(combined,values,false,previous.completed());reviews.save(chatId,lessonKey,next);return next;
            }
            protected void done(){busy=false;try{state=get();updating=true;filter.setSelectedIndex(0);updating=false;visible=QuizLogic.indices(state,false);render();}catch(Exception e){render();error(e.getCause()==null?e:e.getCause());}}
        }.execute();
    }
    private void guarded(Runnable action){try{action.run();}catch(RuntimeException e){error(e);}}
    private void error(Throwable e){JOptionPane.showMessageDialog(this,e.getMessage(),"Chưa thể hoàn tất",JOptionPane.WARNING_MESSAGE);}
    public static String lessonKey(String subject,String textbook,WeeklyCurriculum plan){try{String data=new com.google.gson.Gson().toJson(List.of(subject.trim().toLowerCase(Locale.ROOT),Objects.toString(textbook,""),plan.getWeekNumber(),Objects.toString(plan.getChapterTitle(),""),Objects.toString(plan.isChapter()?plan.getSourceText():plan.getDetailedSyllabus(),"")));return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
