package com.studyagent;

import com.studyagent.model.*;
import com.studyagent.repository.ReviewRepository;
import com.studyagent.ui.QuizResults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QuestionBankTest {
    @TempDir Path temp;
    ReviewRepository repo(){return new ReviewRepository("jdbc:sqlite:"+temp.resolve("bank.db"));}
    QuizQuestion question(String title){return new QuizQuestion(title,List.of("A. One","B. Two","C. Three","D. Four"),"A","Explanation for "+title);}
    @Test void bankImportsOldPracticeDeduplicatesAndSeparatesUsersAndSubjects(){
        var r=repo();var q=question("Legacy");q.setSubjectName("A");
        r.save(1,"lesson",new ReviewRepository.State(List.of(q),new int[]{-1},false,false));
        assertEquals(1,r.bank(1,"A").size());r.addToBank(1,"A","lesson",List.of(question("Legacy")));
        assertEquals(1,repo().bank(1,"A").size());assertTrue(r.bank(2,"A").isEmpty());assertTrue(r.bank(1,"B").isEmpty());
    }
    @Test void filteredSubmissionKeepsFullBankAndImmutableHistoryIncludingBlanks(){
        var r=repo();var questions=List.of(question("Right"),question("Wrong"),question("Blank"));
        var state=new ReviewRepository.State(questions,new int[]{0,2,-1},false,false);
        var needsReview=QuizLogic.indices(state,true);assertEquals(List.of(1,2),needsReview);
        var attempt=QuizLogic.subset(state,needsReview);r.submit(1,"lesson",state,attempt);
        assertEquals(3,r.load(1,"lesson").questions().size());var history=r.history(1,"lesson").getFirst();
        assertEquals(2,history.total());assertEquals(0,history.correct());
        r.save(1,"lesson",new ReviewRepository.State(questions,new int[]{0,0,0},true,false));
        var saved=repo().attempt(1,history.id());assertArrayEquals(new int[]{2,-1},saved.answers());
        var result=QuizResults.markdown(saved);assertTrue(result.contains("Chưa trả lời"));assertTrue(result.contains("Explanation for Blank"));assertTrue(result.contains("Đáp án đúng"));
        assertThrows(IllegalArgumentException.class,()->r.attempt(2,history.id()));
    }
    @Test void randomExamHasRequestedUniqueCountAllModeUsesEntireBankAndDraftSurvivesGrowth(){
        var r=repo();r.addToBank(1,"A",null,List.of(question("1"),question("2"),question("3")));
        var bank=r.bank(1,"A");var selected=QuizLogic.choose(bank,2,false,new Random(7));assertEquals(2,new HashSet<>(selected).size());
        assertEquals(3,QuizLogic.choose(bank,1,true,new Random()).size());
        assertThrows(IllegalArgumentException.class,()->QuizLogic.choose(bank,4,false,new Random()));
        String key=ReviewRepository.examKey("A");r.save(1,key,new ReviewRepository.State(selected,new int[]{0,-1},false,false));
        r.addToBank(1,"A",null,List.of(question("4")));assertEquals(4,r.bank(1,"A").size());
        assertEquals(2,repo().load(1,key).questions().size());assertArrayEquals(new int[]{0,-1},repo().load(1,key).answers());
        r.submit(1,key,r.load(1,key));assertEquals(1,r.history(1,key).size());assertTrue(r.history(1,"lesson").isEmpty());
    }
}
