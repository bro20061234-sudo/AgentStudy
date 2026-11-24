package com.studyagent.ui;

import com.studyagent.repository.StudyRepository;
import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/** File actions operate on the selected subject rather than showing another chooser. */
public final class TextbookLibraryPanel extends JPanel {
    private final long chatId;private final StudyRepository repository;
    private final Consumer<String> open,outline,remove;private final JButton add;
    public TextbookLibraryPanel(long chatId,StudyRepository repository,JButton add,Consumer<String> open,Consumer<String> outline,Consumer<String> remove){
        super(new BorderLayout(0,20));this.chatId=chatId;this.repository=repository;this.add=add;this.open=open;this.outline=outline;this.remove=remove;setOpaque(false);refresh();
    }
    public void refresh(){
        removeAll();JPanel heading=UiKit.transparent(new BorderLayout(16,12));JPanel words=UiKit.transparent(new GridLayout(2,1,0,8));words.add(UiKit.label("Thư viện giáo trình",28,true,"text"));words.add(UiKit.label("File gốc và đề cương của từng môn ở cùng một nơi.",14,false,"muted"));heading.add(words);JPanel addAction=UiKit.transparent(new FlowLayout(FlowLayout.RIGHT,0,10));addAction.add(add);heading.add(addAction,BorderLayout.EAST);add(heading,BorderLayout.NORTH);
        JPanel list=UiKit.transparent(new GridBagLayout());GridBagConstraints gc=new GridBagConstraints();gc.gridx=0;gc.weightx=1;gc.fill=GridBagConstraints.HORIZONTAL;gc.insets=new Insets(0,0,18,0);
        var books=repository.getAllTextbooks(chatId);
        if(books.isEmpty()){UiKit.Card empty=new UiKit.Card(new BorderLayout());empty.add(UiKit.label("Chưa có giáo trình. Chọn “Thêm giáo trình” để bắt đầu.",15,false,"muted"));list.add(empty,gc);}
        for(var book:books){String subject=book.getSubjectName();var plans=repository.getWeeklyCurricula(chatId,subject);long ready=plans.stream().filter(p->"READY".equals(p.getContentStatus())).count();
            UiKit.Card item=new UiKit.Card(new BorderLayout(16,18));JPanel details=UiKit.transparent(new GridLayout(0,1,0,10));details.add(UiKit.label(subject,20,true,"text"));details.add(UiKit.label(book.getFileName(),13,false,"muted"));details.add(UiKit.label("Đề cương: "+ready+" / "+plans.size()+" mục sẵn sàng",12,false,ready==plans.size()&&ready>0?"green":"muted"));item.add(details);
            JPanel actions=UiKit.transparent(new FlowLayout(FlowLayout.RIGHT,8,12));actions.add(UiKit.button("Mở file",false,()->open.accept(subject)));actions.add(UiKit.button("Xem đề cương",true,()->outline.accept(subject)));JButton delete=UiKit.button("Xóa",false,()->remove.accept(subject));UiTheme.role(delete,"card","danger");actions.add(delete);item.add(actions,BorderLayout.SOUTH);gc.gridy++;list.add(item,gc);
        }
        gc.gridy++;gc.weighty=1;list.add(Box.createVerticalGlue(),gc);add(UiKit.scroll(list));UiTheme.apply(this);revalidate();repaint();
    }
}
