package com.studyagent.ui;

import com.studyagent.model.*;
import com.studyagent.repository.ReviewRepository;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.function.BiConsumer;

/** Shared working quiz view for practice and exams; state is owned and persisted by the caller. */
public final class QuestionPlayer extends JPanel {
    private ReviewRepository.State state=new ReviewRepository.State(List.of(),new int[0],false,false);
    private List<Integer> visible=List.of();private int position;private boolean busy;
    private final BiConsumer<Integer,Integer> answer;
    private final Runnable submit,retry,history;
    private JComponent filter;
    private boolean answerPreviewEnabled;
    private final Set<Integer> revealedAnswers=new HashSet<>();
    private String bankTitle="Bộ câu hỏi đã lưu",emptyMessage="Chưa có câu hỏi trong bộ này. Bạn có thể đổi bộ lọc hoặc tạo thêm câu hỏi.";
    public QuestionPlayer(BiConsumer<Integer,Integer> answer,Runnable submit,Runnable retry,Runnable history){super(new BorderLayout(18,0));this.answer=answer;this.submit=submit;this.retry=retry;this.history=history;setOpaque(false);}
    public void resetPosition(){position=0;revealedAnswers.clear();}
    public void setAnswerPreviewEnabled(boolean enabled){answerPreviewEnabled=enabled;if(!enabled)revealedAnswers.clear();}
    public void setFilter(JComponent filter){this.filter=filter;}
    public void setLabels(String title,String empty){bankTitle=title;emptyMessage=empty;}
    public void bind(ReviewRepository.State state,List<Integer> visible,boolean busy){this.state=state;this.visible=List.copyOf(visible);this.busy=busy;position=Math.min(position,Math.max(0,visible.size()-1));render();}
    private void render(){
        removeAll();UiKit.Card main=new UiKit.Card(new BorderLayout(0,18));add(main);JPanel side=UiKit.transparent(new BorderLayout(0,16));side.setPreferredSize(new Dimension(255,200));add(side,BorderLayout.EAST);
        UiKit.Card nav=new UiKit.Card(new BorderLayout(0,14));JPanel heading=UiKit.transparent(new GridLayout(0,1,0,10));heading.add(UiKit.label(bankTitle,16,true,"text"));if(filter!=null)heading.add(filter);nav.add(heading,BorderLayout.NORTH);
        JPanel numbers=UiKit.transparent(new GridLayout(0,5,7,7));
        for(int i=0;i<visible.size();i++){final int p=i;int index=visible.get(i);int chosen=state.answers()[index];boolean correct=chosen>=0&&chosen==ReviewRepository.correctIndex(state.questions().get(index));JButton b=UiKit.button(Integer.toString(i+1),false,()->{position=p;render();});b.putClientProperty("theme.compact",true);
            UiTheme.role(b,state.submitted()?(correct?"greenSoft":"warningSoft"):i==position?"tint":chosen>=0?"greenSoft":"soft",state.submitted()?(correct?"green":"warning"):i==position?"accent":"text");b.setEnabled(!busy);numbers.add(b);}
        JPanel numberWrap=UiKit.transparent(new BorderLayout());numberWrap.add(numbers,BorderLayout.NORTH);var numberScroll=UiKit.scroll(numberWrap);numberScroll.setPreferredSize(new Dimension(200,190));nav.add(numberScroll);
        JPanel actions=UiKit.transparent(new GridLayout(0,1,0,9));long answered=visible.stream().filter(i->state.answers()[i]>=0).count();actions.add(UiKit.label(answered+" / "+visible.size()+" câu đã trả lời",13,false,"muted"));
        JButton send=UiKit.button(state.submitted()?"Làm lại bộ này":"Nộp bài",true,state.submitted()?retry:submit);send.setEnabled(!busy&&!visible.isEmpty());actions.add(send);actions.add(UiKit.button("Lịch sử làm bài",false,history));nav.add(actions,BorderLayout.SOUTH);side.add(nav,BorderLayout.NORTH);
        UiKit.Card note=new UiKit.Card(new BorderLayout());JTextArea hint=text("Câu bỏ trống vẫn được chấm và hiện đáp án sau khi nộp.",14,false,"muted");hint.setRows(3);note.add(hint);side.add(note,BorderLayout.SOUTH);
        if(visible.isEmpty()){main.add(text(emptyMessage,18,false,"text"));}
        else if(state.submitted()){
            var snapshot=QuizLogic.subset(state,visible);int score=ReviewRepository.score(snapshot);main.add(UiKit.label("Kết quả: "+score+" / "+visible.size()+" câu đúng",23,true,"text"),BorderLayout.NORTH);
            var results=LessonHtml.pane();LessonHtml.show(results,"Đáp án và giải thích",QuizResults.markdown(snapshot));main.add(UiKit.scroll(results));
        }else{
            int index=visible.get(position);var q=state.questions().get(index);JPanel intro=UiKit.transparent(new BorderLayout(0,14));
            JPanel questionHeading=UiKit.transparent(new BorderLayout(10,0));questionHeading.add(UiKit.label("CÂU "+(position+1)+" / "+visible.size(),12,true,"muted"));
            if(answerPreviewEnabled){JButton reveal=UiKit.button(revealedAnswers.contains(index)?"Ẩn đáp án":"Xem đáp án",false,()->{if(!revealedAnswers.add(index))revealedAnswers.remove(index);render();});reveal.setEnabled(!busy);questionHeading.add(reveal,BorderLayout.EAST);}
            intro.add(questionHeading,BorderLayout.NORTH);intro.add(text(q.getQuestion(),22,true,"text"));main.add(intro,BorderLayout.NORTH);
            JPanel options=new OptionsPane();ButtonGroup group=new ButtonGroup();
            for(int i=0;i<q.getOptions().size();i++){final int option=i;boolean selected=state.answers()[index]==i;UiKit.Card card=new UiKit.Card(new BorderLayout(12,0));card.setBorder(BorderFactory.createEmptyBorder(12,14,12,14));UiTheme.role(card,selected?"tint":"soft","text");
                JRadioButton radio=new JRadioButton(String.valueOf((char)('A'+i)));radio.setOpaque(false);radio.setFont(new Font("Segoe UI",Font.BOLD,16));radio.setSelected(selected);radio.setEnabled(!busy);group.add(radio);card.add(radio,BorderLayout.WEST);
                String label=q.getOptions().get(i).replaceFirst("(?i)^[A-D][.):]\\s*","");JTextArea content=text(label,16,false,"text");card.add(content);Runnable select=()->{if(!busy){answer.accept(index,option);}};
                radio.addActionListener(e->select.run());MouseAdapter click=new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){select.run();}};card.addMouseListener(click);content.addMouseListener(click);card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));options.add(card);
            }var optionsScroll=UiKit.scroll(options);UiTheme.role(optionsScroll.getViewport(),"card","text");main.add(optionsScroll);
            JPanel bottom=UiKit.transparent(new BorderLayout(8,0));JButton previous=UiKit.button("← Câu trước",false,()->{position--;render();});previous.setEnabled(position>0&&!busy);bottom.add(previous,BorderLayout.WEST);bottom.add(UiKit.label("Câu trả lời tự động lưu",12,false,"green"));JButton next=UiKit.button("Câu tiếp theo  →",true,()->{position++;render();});next.setEnabled(position+1<visible.size()&&!busy);bottom.add(next,BorderLayout.EAST);
            JPanel footer=UiKit.transparent(new BorderLayout(0,12));footer.add(bottom,BorderLayout.SOUTH);
            if(answerPreviewEnabled&&revealedAnswers.contains(index)){
                var explanation=LessonHtml.pane();int correct=ReviewRepository.correctIndex(q);
                String correctAnswer=correct>=0&&correct<q.getOptions().size()?q.getOptions().get(correct):"Chưa có đáp án hợp lệ.";
                if(correct>=0&&correct<q.getOptions().size())correctAnswer=(char)('A'+correct)+" — "+correctAnswer.replaceFirst("(?i)^[A-D][.):]\\s*","");
                String detail=q.getExplanation()==null||q.getExplanation().isBlank()?"Chưa có lời giải cho câu này.":q.getExplanation();
                LessonHtml.show(explanation,"","**Đáp án đúng:** "+correctAnswer+"\n\n**Giải thích:** "+detail);
                explanation.setCaretPosition(0);var scroll=UiKit.scroll(explanation);scroll.setPreferredSize(new Dimension(300,150));footer.add(scroll);
            }
            main.add(footer,BorderLayout.SOUTH);
        }UiTheme.apply(this);revalidate();repaint();
    }
    public static JTextArea text(String value,int size,boolean bold,String role){JTextArea area=new JTextArea(value);area.setEditable(false);area.setLineWrap(true);area.setWrapStyleWord(true);area.setOpaque(false);area.setFont(new Font("Segoe UI",bold?Font.BOLD:Font.PLAIN,size));area.setBorder(BorderFactory.createEmptyBorder(4,0,4,0));UiTheme.role(area,null,role);return area;}
    /** Keep choices readable when the optional explanation uses some of the vertical space. */
    private static final class OptionsPane extends JPanel implements Scrollable {
        OptionsPane(){super(new GridLayout(0,1,0,12));setOpaque(false);}
        @Override public Dimension getPreferredSize(){
            int width=getParent() instanceof JViewport viewport?Math.max(150,viewport.getExtentSize().width):500;
            int rowHeight=64;
            for(var card:getComponents())if(card instanceof Container container)for(var child:container.getComponents())if(child instanceof JTextArea content){content.setSize(Math.max(60,width-90),Short.MAX_VALUE);rowHeight=Math.max(rowHeight,content.getPreferredSize().height+24);}
            return new Dimension(width,rowHeight*getComponentCount()+Math.max(0,getComponentCount()-1)*12);
        }
        @Override public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
        @Override public boolean getScrollableTracksViewportWidth(){return true;}
        @Override public boolean getScrollableTracksViewportHeight(){return getParent() instanceof JViewport viewport&&viewport.getHeight()>getPreferredSize().height;}
        @Override public int getScrollableUnitIncrement(Rectangle visible,int orientation,int direction){return 28;}
        @Override public int getScrollableBlockIncrement(Rectangle visible,int orientation,int direction){return Math.max(28,visible.height-40);}
    }
}
