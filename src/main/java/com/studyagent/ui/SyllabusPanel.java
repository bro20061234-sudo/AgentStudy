package com.studyagent.ui;

import com.studyagent.ai.GeminiService;
import com.studyagent.model.WeeklyCurriculum;
import com.studyagent.repository.StudyRepository;
import java.awt.*;
import java.util.List;
import java.util.function.*;
import javax.swing.*;

/** Textbook chapters are learning units; calendar slots only reserve study time. */
public class SyllabusPanel extends JPanel {
    private final StudyRepository repository;
    private final GeminiService ai;
    private final long chatId;
    private final JComboBox<String> subjects = new JComboBox<>();
    private final DefaultListModel<WeeklyCurriculum> model = new DefaultListModel<>();
    private final JList<WeeklyCurriculum> lessons = new JList<>(model) {
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
    };
    private final JEditorPane content = LessonHtml.pane();
    private final JButton generate = new JButton("Soạn đề cương chương"), practice = new JButton("Luyện tập bài này  →"), open = new JButton("Mở file giáo trình"), refreshButton = new JButton("Làm mới");
    private final JButton analyzeAll = new JButton("Phân tích giáo trình");
    private final JLabel status = new JLabel(" ");
    private final JButton manage=new JButton("...");
    private final JProgressBar progress=new JProgressBar(0,100);
    private final JLabel catalogInfo=UiKit.label(" ",13,false,"muted"),paperBadge=UiKit.label(" ",12,true,"green");
    private boolean updating, busy;
    private final Consumer<String> openFile;
    private final BiConsumer<String,Integer> openReview;

    public SyllabusPanel(long chatId, StudyRepository repository, GeminiService ai, Consumer<String> openFile, BiConsumer<String,Integer> openReview) {
        this(chatId,repository,ai,openFile,openReview,null);
    }
    public SyllabusPanel(long chatId, StudyRepository repository, GeminiService ai, Consumer<String> openFile, BiConsumer<String,Integer> openReview, Consumer<String> deleteFile) {
        super(new BorderLayout(14,14)); this.chatId=chatId;this.repository=repository;this.ai=ai;this.openFile=openFile;this.openReview=openReview;
        setOpaque(false);setBorder(BorderFactory.createEmptyBorder());
        JPanel header=new JPanel(new BorderLayout(8,12));header.setOpaque(false);
        JPanel intro=UiKit.transparent(new GridLayout(2,1,0,8));intro.add(UiKit.label("Đề cương giáo trình",28,true,"text"));intro.add(UiKit.label("Học theo chương, bài trong tài liệu. Chọn nội dung rồi chuyển sang luyện tập.",15,false,"muted"));header.add(intro,BorderLayout.NORTH);
        JPanel controls=UiKit.transparent(new BorderLayout(12,0));JPanel selection=UiKit.transparent(new FlowLayout(FlowLayout.LEFT,8,0));subjects.setPreferredSize(new Dimension(260,40));selection.add(subjects);selection.add(refreshButton);controls.add(selection);
        JPanel actions=UiKit.transparent(new FlowLayout(FlowLayout.RIGHT,8,0));actions.add(open);actions.add(analyzeAll);
        if(deleteFile!=null){manage.setToolTipText("Quản lý tài liệu môn học");manage.addActionListener(e->{JPopupMenu menu=new JPopupMenu();JMenuItem remove=new JMenuItem("Xóa tài liệu và đề cương môn này");remove.addActionListener(a->{String subject=(String)subjects.getSelectedItem();if(subject!=null)deleteFile.accept(subject);});menu.add(remove);UiTheme.apply(menu);menu.show(manage,0,manage.getHeight());});actions.add(manage);}
        controls.add(actions,BorderLayout.EAST);header.add(controls,BorderLayout.SOUTH);add(header,BorderLayout.NORTH);        lessons.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);lessons.setFixedCellHeight(72);
        lessons.setSelectionBackground(new Color(238,242,255));lessons.setSelectionForeground(new Color(67,56,202));
        lessons.setCellRenderer(new DefaultListCellRenderer(){
            @Override public Component getListCellRendererComponent(JList<?> l,Object value,int index,boolean selected,boolean focus){
                var cell=(JLabel)super.getListCellRendererComponent(l,value,index,selected,focus);var p=(WeeklyCurriculum)value;
                String title=p.getChapterTitle(); if(title.length()>42)title=title.substring(0,39)+"…";
                cell.setText("<html><b>"+LessonHtml.escape(title)+"</b><br><span style='color:"+UiTheme.hex("muted")+"'>"+("READY".equals(p.getContentStatus())?"Đã lưu đề cương AI":"Chưa soạn đề cương AI")+"</span></html>");
                cell.setBorder(BorderFactory.createEmptyBorder(10,12,10,12));cell.setFont(new Font("Segoe UI",Font.PLAIN,13));cell.setToolTipText(p.getChapterTitle());return cell;
            }
        });
        UiKit.Card contents=new UiKit.Card(new BorderLayout(0,16));JPanel catalog=UiKit.transparent(new GridLayout(3,1,0,8));catalog.add(UiKit.label("NỘI DUNG MÔN HỌC",12,true,"muted"));catalog.add(catalogInfo);progress.setPreferredSize(new Dimension(180,8));catalog.add(progress);contents.add(catalog,BorderLayout.NORTH);contents.add(UiKit.scroll(lessons));contents.setMinimumSize(new Dimension(240,200));
        UiKit.Card paper=new UiKit.Card(new BorderLayout(0,14));paper.add(paperBadge,BorderLayout.NORTH);paper.add(UiKit.scroll(content));
        JPanel paperActions=UiKit.transparent(new BorderLayout(12,0));paperActions.add(generate,BorderLayout.WEST);paperActions.add(practice,BorderLayout.EAST);paper.add(paperActions,BorderLayout.SOUTH);paper.setMinimumSize(new Dimension(430,200));
        var split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,contents,paper);split.setDividerLocation(290);split.setResizeWeight(.26);split.setDividerSize(16);split.setBorder(BorderFactory.createEmptyBorder());split.setOpaque(false);add(split);
        add(status,BorderLayout.SOUTH);        subjects.addActionListener(e->{if(!updating)load();});lessons.addListSelectionListener(e->{if(!e.getValueIsAdjusting()&&!updating)render();});
        open.addActionListener(e->{String s=(String)subjects.getSelectedItem();if(s!=null)openFile.accept(s);});
        practice.addActionListener(e->{var p=lessons.getSelectedValue();if(p!=null)openReview.accept(p.getSubjectName(),p.getWeekNumber());});
        UiTheme.primary(analyzeAll);UiTheme.primary(practice);
        refreshButton.addActionListener(e->refresh());generate.addActionListener(e->generate());analyzeAll.addActionListener(e->analyzeAll());refresh();
    }
    public void selectSubject(String subject){refresh();subjects.setSelectedItem(subject);}
    public void refreshTheme(){render();}
    public void refresh(){
        if(busy)return;Object previous=subjects.getSelectedItem();updating=true;
        try{subjects.removeAllItems();for(var book:repository.getAllTextbooks(chatId))subjects.addItem(book.getSubjectName());if(previous!=null)subjects.setSelectedItem(previous);}
        finally{updating=false;}load();
    }
    private void load(){
        int previous=lessons.getSelectedValue()==null?1:lessons.getSelectedValue().getWeekNumber();updating=true;
        try{
            model.clear();String subject=(String)subjects.getSelectedItem();
            if(subject!=null)for(var plan:repository.prepareChapters(chatId,subject))model.addElement(plan);
            if(!model.isEmpty()){int target=0;for(int i=0;i<model.size();i++)if(model.get(i).getWeekNumber()==previous)target=i;lessons.setSelectedIndex(target);}
        }catch(RuntimeException e){JOptionPane.showMessageDialog(this,e.getMessage(),"Không đọc được danh sách chương",JOptionPane.WARNING_MESSAGE);}
        finally{updating=false;}render();
    }
    private void render(){
        var p=lessons.getSelectedValue();boolean hasBook=subjects.getSelectedItem()!=null;
        practice.setEnabled(p!=null&&!busy);open.setEnabled(hasBook&&!busy);generate.setEnabled(p!=null&&!busy);refreshButton.setEnabled(!busy);analyzeAll.setEnabled(hasBook&&!busy);
        manage.setEnabled(hasBook&&!busy);
        generate.setText(p!=null&&"READY".equals(p.getContentStatus())?"Soạn lại mục đã chọn":"Soạn riêng mục đã chọn");
        LessonHtml.show(content,p==null?"Phân tích giáo trình":p.getChapterTitle(),p==null?(hasBook?"Chọn “Phân tích giáo trình” để soạn đề cương các chương/bài. Những mục đã xong sẽ được giữ lại.":"Thêm giáo trình để tự động tạo đề cương và bắt đầu học."):p.getDetailedSyllabus());content.setCaretPosition(0);
        int ready=0;for(int i=0;i<model.size();i++)if("READY".equals(model.get(i).getContentStatus()))ready++;
        catalogInfo.setText(ready+" / "+model.size()+" đề cương sẵn sàng");paperBadge.setText(p!=null&&"READY".equals(p.getContentStatus())?"ĐỀ CƯƠNG ĐÃ LƯU":"CHƯA SOẠN ĐỀ CƯƠNG");
        String subject=(String)subjects.getSelectedItem();progress.setValue(model.isEmpty()?0:ready*100/model.size());
        if(subject!=null){SubjectColors.decorate(progress,subject,false);SubjectColors.decorate(catalogInfo,subject,false);SubjectColors.decorate(paperBadge,subject,false);SubjectColors.decorate(lessons,subject,false);}
        status.setText(busy?"Đang phân tích và lưu đề cương…":"Đã soạn "+ready+"/"+model.size()+" chương/bài · Chọn nội dung để học.");
        UiTheme.apply(this);
        if(subject!=null){lessons.setSelectionBackground(SubjectColors.background(subject));lessons.setSelectionForeground(SubjectColors.foreground(subject));}
    }
    public void analyzeAll(){
        String subject=(String)subjects.getSelectedItem();if(subject==null||busy)return;
        busy=true;subjects.setEnabled(false);lessons.setEnabled(false);render();
        new SwingWorker<com.studyagent.ai.TextbookProcessor.Result,String>(){
            protected com.studyagent.ai.TextbookProcessor.Result doInBackground(){return new com.studyagent.ai.TextbookProcessor(repository,ai).process(chatId,subject,message->publish(message));}
            protected void process(List<String> messages){load();if(!messages.isEmpty())status.setText(messages.get(messages.size()-1));}
            protected void done(){
                busy=false;subjects.setEnabled(true);lessons.setEnabled(true);
                try{var result=get();load();status.setText(result.summary());if(!result.complete())JOptionPane.showMessageDialog(SyllabusPanel.this,result.summary()+"\nCác đề cương đã xong vẫn được giữ lại. Có thể bấm tiếp tục sau.","Phân tích chưa hoàn tất",JOptionPane.WARNING_MESSAGE);}
                catch(Exception e){render();JOptionPane.showMessageDialog(SyllabusPanel.this,e.getCause()==null?e.getMessage():e.getCause().getMessage());}
            }
        }.execute();
    }
    private void generate(){
        var chapter=lessons.getSelectedValue();if(chapter==null||busy)return;
        busy=true;subjects.setEnabled(false);lessons.setEnabled(false);render();
        new SwingWorker<String,Void>(){
            protected String doInBackground(){String body=ai.generateChapterOutline(chapter.getSubjectName(),chapter);repository.saveChapterOutline(chatId,chapter,body);return body;}
            protected void done(){
                busy=false;subjects.setEnabled(true);lessons.setEnabled(true);
                try{get();load();}
                catch(Exception e){render();JOptionPane.showMessageDialog(SyllabusPanel.this,e.getCause()==null?e.getMessage():e.getCause().getMessage(),"Chưa soạn được đề cương",JOptionPane.WARNING_MESSAGE);}
            }
        }.execute();
    }
}
