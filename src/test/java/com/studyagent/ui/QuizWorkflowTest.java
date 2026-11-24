package com.studyagent.ui;

import com.studyagent.ai.GeminiService;
import com.studyagent.model.*;
import com.studyagent.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class QuizWorkflowTest {
    @TempDir Path temp;
    static class Study extends StudyRepository {
        final WeeklyCurriculum plan=new WeeklyCurriculum(1,"A",1,"Chapter","Outline");
        final TextbookItem book=new TextbookItem("book",1,"A","a.pdf","Source","");
        Study(){super(false);}
        @Override public List<TextbookItem> getAllTextbooks(long id){return List.of(book);}
        @Override public TextbookItem getTextbook(long id,String subject){return book;}
        @Override public List<WeeklyCurriculum> prepareChapters(long id,String subject){return List.of(plan);}
        @Override public List<String> getDistinctSubjects(long id){return List.of("A");}
    }
    static void call(Object target,String name,Class<?>[] types,Object... args)throws Exception{var method=target.getClass().getDeclaredMethod(name,types);method.setAccessible(true);method.invoke(target,args);}
    static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    List<QuizQuestion> questions(){return List.of(new QuizQuestion("First",List.of("One","Two","Three","Four"),"A","First explanation"),new QuizQuestion("Second",List.of("One","Two","Three","Four"),"B","Second explanation"));}
    @Test void actualPracticePanelSubmitsBlankAnswersAndFiltersWrongQuestions()throws Exception{
        var r=new ReviewRepository("jdbc:sqlite:"+temp.resolve("practice.db"));var study=new Study();String key=LessonReviewPanel.lessonKey("A","Source",study.plan);
        r.save(1,key,new ReviewRepository.State(questions(),new int[]{-1,-1},false,false));
        SwingUtilities.invokeAndWait(()->{try{
            var panel=new LessonReviewPanel(1,study,new GeminiService(""),null,r);
            call(panel,"answer",new Class<?>[]{int.class,int.class},0,0);call(panel,"submit",new Class<?>[0]);
            assertTrue(r.load(1,key).submitted());assertEquals(1,r.history(1,key).getFirst().correct());
            ((JComboBox<?>)field(panel,"filter")).setSelectedIndex(1);
            assertEquals(List.of(1),field(panel,"visible"));
            call(panel,"retry",new Class<?>[0]);assertArrayEquals(new int[]{0,-1},r.load(1,key).answers());
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    @Test void actualExamPanelSamplesWithoutAiAndSavesDetailedPartialResult()throws Exception{
        var r=new ReviewRepository("jdbc:sqlite:"+temp.resolve("exam.db"));r.addToBank(1,"A","lesson",questions());
        SwingUtilities.invokeAndWait(()->{try{
            var panel=new ExamPanel(1,new Study(),new GeminiService(""),r);
            ((JComboBox<?>)field(panel,"mode")).setSelectedIndex(1);call(panel,"begin",new Class<?>[0]);
            String key=ReviewRepository.examKey("A");assertEquals(2,r.load(1,key).questions().size());
            call(panel,"submit",new Class<?>[0]);var attempt=r.history(1,key).getFirst();
            assertEquals(0,attempt.correct());assertEquals(2,attempt.total());assertArrayEquals(new int[]{-1,-1},r.attempt(1,attempt.id()).answers());
            call(panel,"retry",new Class<?>[0]);assertFalse(r.load(1,key).submitted());assertEquals(1,r.history(1,key).size());
        }catch(Exception e){throw new RuntimeException(e);}});
    }
}
