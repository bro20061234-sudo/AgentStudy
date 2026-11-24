package com.studyagent.ui;

import javax.swing.*;
import java.awt.*;

/** Two entry points match the approved mockup, with an optional inline text editor. */
public final class InputPanel extends JPanel {
    private final JPanel editor;
    private final JButton textToggle=UiKit.button("Nhập bằng văn bản",false,this::toggleEditor);

    public InputPanel(JButton timetableFile,JButton textbookFile,JButton analyze,JTextArea text){
        this(timetableFile,textbookFile,analyze,text,null,null);
    }
    public InputPanel(JButton timetableFile,JButton textbookFile,JButton analyze,JTextArea text,
                      java.util.function.Consumer<java.io.File[]> timetableDrop,
                      java.util.function.Consumer<java.io.File> textbookDrop){
        super(new BorderLayout());setOpaque(false);
        JPanel stack=UiKit.transparent(new GridBagLayout());GridBagConstraints gc=new GridBagConstraints();
        gc.gridx=0;gc.weightx=1;gc.fill=GridBagConstraints.HORIZONTAL;gc.insets=new Insets(0,0,20,0);
        JPanel intro=UiKit.transparent(new GridLayout(2,1,0,8));intro.add(UiKit.label("Thêm vào không gian học tập",28,true,"text"));
        intro.add(UiKit.label("Bắt đầu bằng thời khóa biểu hoặc giáo trình, đề cương môn học của bạn.",15,false,"muted"));addRow(stack,intro,gc);

        timetableFile.setText("Chọn file thời khóa biểu");UiTheme.primary(timetableFile);
        textbookFile.setText("+ Thêm giáo trình / đề cương");UiTheme.primary(textbookFile);
        JPanel cards=UiKit.transparent(new GridLayout(1,2,22,0));
        JPanel timetableCard=sourceCard("01 · LỊCH HỌC","Thời khóa biểu","Kéo thả file vào đây hoặc bấm chọn file. Ứng dụng đọc lịch trên lớp và tạo lịch tự học ngay sau khi nhập.","PDF · PNG/JPG/WEBP · Word (.docx)",timetableFile,textToggle,"accent");
        JPanel textbookCard=sourceCard("02 · TÀI LIỆU","Giáo trình / đề cương","Kéo thả một tài liệu vào đây hoặc bấm thêm. Chọn môn học để tự phân tích chương, bài và lưu đề cương.","PDF · Word (.docx)",textbookFile,null,"green");
        if(timetableDrop!=null) FileDropHandler.install(timetableCard,new FileDropHandler(timetableFile,java.util.Set.of("pdf","docx","png","jpg","jpeg","webp"),Integer.MAX_VALUE,timetableDrop));
        if(textbookDrop!=null) FileDropHandler.install(textbookCard,new FileDropHandler(textbookFile,java.util.Set.of("pdf","docx"),1,files->textbookDrop.accept(files[0])));
        cards.add(timetableCard);cards.add(textbookCard);addRow(stack,cards,gc);

        editor=new UiKit.Card(new BorderLayout(0,12));editor.add(UiKit.label("Nhập thời khóa biểu bằng văn bản",18,true,"text"),BorderLayout.NORTH);
        text.setLineWrap(true);text.setWrapStyleWord(true);text.setFont(new Font("Segoe UI",Font.PLAIN,15));text.setRows(7);UiTheme.role(text,"soft","text");text.setBorder(BorderFactory.createEmptyBorder(12,14,12,14));
        editor.add(UiKit.scroll(text));
        JPanel actions=UiKit.transparent(new FlowLayout(FlowLayout.RIGHT,8,0));
        actions.add(UiKit.button("Xóa nội dung",false,()->text.setText("")));
        actions.add(UiKit.button("Dùng mẫu",false,()->text.setText("Thứ 2:\n- 07:00-09:00 Cơ sở dữ liệu (A2-301)\n\nThứ 3:\n- 13:00-16:00 Hệ điều hành (B1-204)")));
        analyze.setText("Phân tích và tạo lịch");UiTheme.primary(analyze);actions.add(analyze);editor.add(actions,BorderLayout.SOUTH);editor.setVisible(false);addRow(stack,editor,gc);

        UiKit.Card guide=new UiKit.Card(new GridLayout(0,1,0,10));UiTheme.role(guide,"soft","text");
        guide.add(UiKit.label("Mọi thứ được kết nối",17,true,"text"));
        guide.add(UiKit.label("Thời khóa biểu → Ca tự học → Chọn chương / bài → Ôn tập",15,true,"accent"));
        guide.add(QuestionPlayer.text("Các tiết trên lớp từ 18:00 không tạo lịch tự học. Các tiết trước đó tương ứng 15 phút tự học/tiết, trong khoảng 20:00–22:00; tối đa 1 giờ/môn/ngày.",14,false,"muted"));addRow(stack,guide,gc);
        gc.gridy++;gc.weighty=1;gc.fill=GridBagConstraints.BOTH;stack.add(Box.createVerticalGlue(),gc);
        add(UiKit.scroll(stack));UiTheme.apply(this);
    }
    private JPanel sourceCard(String number,String title,String description,String formats,JButton primary,JButton secondary,String accent){
        UiKit.Card card=new UiKit.Card(new BorderLayout(0,24));
        JPanel words=UiKit.transparent(new BorderLayout(0,18));
        JPanel headings=UiKit.transparent(new GridLayout(0,1,0,18));headings.add(UiKit.label(number,12,true,accent));headings.add(UiKit.label(title,23,true,"text"));words.add(headings,BorderLayout.NORTH);
        JTextArea detail=QuestionPlayer.text(description,15,false,"muted");detail.setRows(4);words.add(detail);words.add(UiKit.label(formats,12,false,"muted"),BorderLayout.SOUTH);card.add(words);
        JPanel actions=UiKit.transparent(new GridLayout(0,1,0,10));actions.add(primary);if(secondary!=null)actions.add(secondary);card.add(actions,BorderLayout.SOUTH);card.setPreferredSize(new Dimension(450,335));return card;
    }
    private void toggleEditor(){editor.setVisible(!editor.isVisible());textToggle.setText(editor.isVisible()?"Ẩn phần nhập văn bản":"Nhập bằng văn bản");revalidate();repaint();}
    private static void addRow(JPanel stack,Component child,GridBagConstraints gc){gc.gridy++;stack.add(child,gc);}
}
