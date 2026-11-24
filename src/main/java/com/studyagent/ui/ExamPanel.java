package com.studyagent.ui;

import com.studyagent.ai.GeminiService;
import com.studyagent.model.*;
import com.studyagent.repository.*;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/** Exams sample the persistent subject bank; starting/retrying an exam never calls AI. */
public final class ExamPanel extends JPanel {
    private final long chatId;private final StudyRepository study;private final ReviewRepository reviews;private final GeminiService ai;
    private final JComboBox<String> subjects=new JComboBox<>();private final JComboBox<String> mode=new JComboBox<>(new String[]{"Ngẫu nhiên","Toàn bộ câu hỏi"});
    private final JSpinner examCount=new JSpinner(new SpinnerNumberModel(20,1,1000,1)),newCount=new JSpinner(new SpinnerNumberModel(12,1,30,1));
    private final JLabel total=UiKit.label("Ngân hàng: 0 câu",14,true,"text"),status=UiKit.label(" ",13,false,"muted");
    private final JButton begin=UiKit.button("Tạo đề / Bắt đầu",true,()->begin()),generate=UiKit.button("Tạo thêm câu hỏi",false,()->generate());
    private final QuestionPlayer player;
    private String subject,key;private boolean updating,busy;private List<QuizQuestion> bank=List.of();
    private ReviewRepository.State state=new ReviewRepository.State(List.of(),new int[0],false,false);
    public ExamPanel(long chatId,StudyRepository study,GeminiService ai){this(chatId,study,ai,new ReviewRepository());}
    ExamPanel(long chatId,StudyRepository study,GeminiService ai,ReviewRepository reviews){
        super(new BorderLayout(0,18));this.chatId=chatId;this.study=study;this.ai=ai;this.reviews=reviews;setOpaque(false);
        player=new QuestionPlayer(this::answer,this::submit,this::retry,()->guarded(()->{if(key!=null)QuizResults.showHistory(this,reviews,chatId,key);}));
        player.setLabels("Câu hỏi trong đề","Chọn môn, số câu rồi bấm Tạo đề / Bắt đầu. Các câu hỏi sẽ được lấy từ ngân hàng đã lưu.");
        JPanel header=UiKit.transparent(new BorderLayout(0,14));JPanel words=UiKit.transparent(new GridLayout(2,1,0,7));words.add(UiKit.label("Đề thi",28,true,"text"));words.add(UiKit.label("Tổng hợp các câu đã tạo của môn. Chọn ngẫu nhiên hoặc làm toàn bộ, không cần tạo lại bằng AI.",15,false,"muted"));header.add(words,BorderLayout.NORTH);
        JPanel controls=UiKit.transparent(new GridLayout(2,1,0,10));JPanel setup=UiKit.transparent(new FlowLayout(FlowLayout.LEFT,8,0));subjects.setPreferredSize(new Dimension(240,39));mode.setPreferredSize(new Dimension(190,39));examCount.setPreferredSize(new Dimension(72,38));setup.add(subjects);setup.add(mode);setup.add(UiKit.label("Số câu",14,false,"text"));setup.add(examCount);setup.add(begin);setup.add(total);
        JPanel extra=UiKit.transparent(new FlowLayout(FlowLayout.LEFT,8,0));extra.add(UiKit.label("Bổ sung ngân hàng",14,false,"muted"));newCount.setPreferredSize(new Dimension(72,38));extra.add(newCount);extra.add(generate);extra.add(UiKit.button("Lịch sử làm bài",false,()->guarded(()->{if(key!=null)QuizResults.showHistory(this,reviews,chatId,key);})));
        controls.add(setup);controls.add(extra);header.add(controls);add(header,BorderLayout.NORTH);add(player);add(status,BorderLayout.SOUTH);
        subjects.addActionListener(e->{if(!updating)load();});mode.addActionListener(e->render());refresh();
    }
    public void refreshTheme(){render();}
    public void refresh(){if(busy)return;guarded(()->{Object previous=subjects.getSelectedItem();updating=true;try{subjects.removeAllItems();Set<String> names=new LinkedHashSet<>();for(var b:study.getAllTextbooks(chatId))names.add(b.getSubjectName());names.addAll(study.getDistinctSubjects(chatId));for(String name:names)subjects.addItem(name);if(previous!=null)subjects.setSelectedItem(previous);}finally{updating=false;}load();});}
    private void load(){guarded(()->{subject=(String)subjects.getSelectedItem();key=subject==null?null:ReviewRepository.examKey(subject);bank=subject==null?List.of():reviews.bank(chatId,subject);state=key==null?new ReviewRepository.State(List.of(),new int[0],false,false):reviews.load(chatId,key);int count=Math.min((int)examCount.getValue(),Math.max(1,bank.size()));examCount.setModel(new SpinnerNumberModel(count,1,Math.max(1,bank.size()),1));player.resetPosition();render();});}
    private void render(){subjects.setEnabled(!busy);mode.setEnabled(!busy);examCount.setEnabled(!busy&&mode.getSelectedIndex()==0&&!bank.isEmpty());begin.setEnabled(subject!=null&&!bank.isEmpty()&&!busy);generate.setEnabled(subject!=null&&!busy);newCount.setEnabled(!busy);total.setText("Ngân hàng: "+bank.size()+" câu");player.bind(state,QuizLogic.indices(state,false),busy);status.setText(busy?"Đang bổ sung câu hỏi vào ngân hàng…":state.questions().isEmpty()?"Chọn cách lấy câu rồi bấm Tạo đề / Bắt đầu.":state.questions().size()+" câu trong đề hiện tại · Câu trả lời tự lưu · Nộp bài để xem tất cả lời giải.");UiTheme.apply(this);revalidate();repaint();}
    private void begin(){guarded(()->{if(key==null)return;if(!state.submitted()&&Arrays.stream(state.answers()).anyMatch(a->a>=0)&&JOptionPane.showConfirmDialog(this,"Đề mới sẽ thay bài đang làm. Các bài đã nộp vẫn còn trong lịch sử. Tiếp tục?","Tạo đề mới",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        var selected=QuizLogic.choose(bank,(int)examCount.getValue(),mode.getSelectedIndex()==1,new Random());persist(new ReviewRepository.State(selected,QuizLogic.blank(selected.size()),false,false));player.resetPosition();render();});}
    private void answer(int index,int choice){guarded(()->{int[] answers=state.answers().clone();answers[index]=choice;persist(new ReviewRepository.State(state.questions(),answers,false,false));render();});}
    private void submit(){guarded(()->{if(state.questions().isEmpty()||state.submitted())return;var submitted=new ReviewRepository.State(state.questions(),state.answers(),true,false);reviews.submit(chatId,key,submitted);state=submitted;render();});}
    private void retry(){guarded(()->{persist(new ReviewRepository.State(state.questions(),QuizLogic.blank(state.questions().size()),false,false));player.resetPosition();render();});}
    private void persist(ReviewRepository.State next){reviews.save(chatId,key,next);state=next;}
    private void generate(){if(subject==null||busy)return;String selected=subject;int count=(int)newCount.getValue();busy=true;render();
        new SwingWorker<Integer,Void>(){
            protected Integer doInBackground(){var plans=study.getWeeklyCurricula(chatId,selected);if(plans.isEmpty())throw new IllegalStateException("Môn này chưa có đề cương. Hãy thêm giáo trình trước khi tạo câu mới.");String context=plans.stream().map(p->p.getChapterTitle()+"\n"+com.studyagent.parser.LessonSources.sample(Objects.toString(p.getSourceText(),p.getDetailedSyllabus()),Math.max(350,15000/plans.size()))).collect(java.util.stream.Collectors.joining("\n\n"));
                var questions=ai.generateQuizQuestions(selected,0,"SESSION_REVIEW","Tạo câu hỏi tổng hợp theo các nguồn của môn:\n"+context,count);ReviewRepository.validate(questions);reviews.addToBank(chatId,selected,null,questions);return questions.size();}
            protected void done(){busy=false;try{int added=get();bank=reviews.bank(chatId,selected);int old=(int)examCount.getValue();examCount.setModel(new SpinnerNumberModel(Math.min(old,Math.max(1,bank.size())),1,Math.max(1,bank.size()),1));render();status.setText("Đã nhận "+added+" câu. Ngân hàng có "+bank.size()+" câu sau khi bỏ trùng; tạo đề mới để sử dụng.");}catch(Exception e){render();error(e.getCause()==null?e:e.getCause());}}
        }.execute();
    }
    private void guarded(Runnable action){try{action.run();}catch(RuntimeException e){error(e);}}
    private void error(Throwable error){JOptionPane.showMessageDialog(this,error.getMessage(),"Chưa thể hoàn tất",JOptionPane.WARNING_MESSAGE);}
}
