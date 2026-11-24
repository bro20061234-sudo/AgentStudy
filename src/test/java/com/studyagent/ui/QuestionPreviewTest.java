package com.studyagent.ui;

import com.studyagent.model.QuizQuestion;
import com.studyagent.repository.ReviewRepository;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class QuestionPreviewTest {
    ReviewRepository.State draft(){return new ReviewRepository.State(List.of(
            new QuizQuestion("First question",List.of("One","Two","Three","Four"),"B","Explanation of first question"),
            new QuizQuestion("Second question",List.of("One","Two","Three","Four"),"A","Explanation of second question")),new int[]{-1,-1},false,false);}
    static List<Component> descendants(Container root){List<Component> result=new ArrayList<>();for(var child:root.getComponents()){result.add(child);if(child instanceof Container c)result.addAll(descendants(c));}return result;}
    static JButton button(Container root,String label){return descendants(root).stream().filter(c->c instanceof JButton b&&b.getText().equals(label)).map(c->(JButton)c).findFirst().orElseThrow();}
    static String explanations(Container root){return descendants(root).stream().filter(c->c instanceof JEditorPane).map(c->{try{var d=((JEditorPane)c).getDocument();return d.getText(0,d.getLength());}catch(Exception e){throw new RuntimeException(e);}}).reduce("",(a,b)->a+b);}
    static void layout(Container root){root.doLayout();for(var child:root.getComponents())if(child instanceof Container c)layout(c);}
    @Test void previewIsOptionalCanBeHiddenAndNeverAnswersOrSubmitsAQuestion()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            var answers=new AtomicInteger();var submits=new AtomicInteger();var player=new QuestionPlayer((i,a)->answers.incrementAndGet(),submits::incrementAndGet,()->{},()->{});
            player.setAnswerPreviewEnabled(true);var state=draft();player.bind(state,List.of(0,1),false);
            assertEquals("",explanations(player));button(player,"Xem đáp án").doClick();
            assertTrue(explanations(player).contains("B — Two"));assertTrue(explanations(player).contains("Explanation of first question"));
            player.setSize(1000,540);layout(player);layout(player);
            var choiceText=descendants(player).stream().filter(c->c instanceof JTextArea t&&t.getText().equals("One")).findFirst().orElseThrow();assertTrue(choiceText.getHeight()>=20,"Choice text must remain readable while explanation is open");
            assertArrayEquals(new int[]{-1,-1},state.answers());assertEquals(0,answers.get());assertEquals(0,submits.get());assertFalse(state.submitted());
            button(player,"Ẩn đáp án").doClick();assertEquals("",explanations(player));assertNotNull(button(player,"Xem đáp án"));
        });
    }
    @Test void eachQuestionHasIndependentPreviewAndNewLessonOrRetryStartsHidden()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            var player=new QuestionPlayer((i,a)->{},()->{},()->{},()->{});player.setAnswerPreviewEnabled(true);var state=draft();player.bind(state,List.of(0,1),false);
            button(player,"Xem đáp án").doClick();button(player,"Câu tiếp theo  →").doClick();assertEquals("",explanations(player));
            button(player,"Xem đáp án").doClick();assertTrue(explanations(player).contains("Explanation of second question"));assertFalse(explanations(player).contains("Explanation of first question"));
            button(player,"← Câu trước").doClick();assertTrue(explanations(player).contains("Explanation of first question"));
            player.resetPosition();player.bind(state,List.of(0,1),false);assertEquals("",explanations(player));
        });
    }
    @Test void examPlayerDoesNotRevealAnswersBeforeSubmissionAndSubmittedResultsStillIncludeBlanks()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            var player=new QuestionPlayer((i,a)->{},()->{},()->{},()->{});var draft=draft();player.bind(draft,List.of(0,1),false);
            assertTrue(descendants(player).stream().noneMatch(c->c instanceof JButton b&&b.getText().equals("Xem đáp án")));
            player.bind(new ReviewRepository.State(draft.questions(),draft.answers(),true,false),List.of(0,1),false);
            assertTrue(explanations(player).contains("Explanation of first question"));assertTrue(explanations(player).contains("Explanation of second question"));assertTrue(explanations(player).contains("Chưa trả lời"));
        });
    }
}
