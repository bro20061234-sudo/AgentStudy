package com.studyagent;

import com.studyagent.ai.*;
import com.studyagent.model.WeeklyCurriculum;
import com.studyagent.parser.TextbookChapters;
import com.studyagent.repository.StudyRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextbookProcessorTest {
    @Test void mathEscapesArePreservedWithoutAcceptingTruncatedResponses(){
        var parsed=AiJson.array("[{\"outline\":\"Phép chọn \\sigma và gạch \\_\"}]");
        assertEquals("Phép chọn \\sigma và gạch \\_",parsed.get(0).getAsJsonObject().get("outline").getAsString());
        assertThrows(RuntimeException.class,()->AiJson.array("[{\"outline\":\"chưa xong"));
    }
    static class MemoryRepository extends StudyRepository {
        List<WeeklyCurriculum> plans=new ArrayList<>();
        String text="Bài 1: Nhập môn\nNội dung 1\nBài 2: Mô hình\nNội dung 2\nBài 3: Truy vấn\nNội dung 3\nBài 4: Chuẩn hóa\nNội dung 4";
        MemoryRepository(){super(false);}
        @Override public TextbookItem getTextbook(long id,String subject){return new TextbookItem("book",id,subject,"book.pdf",text,"today");}
        @Override public List<WeeklyCurriculum> getWeeklyCurricula(long id,String subject){return new ArrayList<>(plans);}
        @Override public boolean saveWeeklyCurricula(long id,String subject,List<WeeklyCurriculum> incoming){plans=new ArrayList<>(incoming);for(var p:plans)p.setId(UUID.randomUUID().toString());return true;}
        @Override public void saveChapterOutline(long id,WeeklyCurriculum plan,String body){
            var saved=plans.stream().filter(p->p.getId().equals(plan.getId())).findFirst().orElseThrow();saved.setDetailedSyllabus(body);saved.setContentStatus("READY");
        }
    }
    static class FakeAi extends GeminiService {
        final List<List<Integer>> calls=new ArrayList<>();int failOn=-1;boolean partial;int discoveries;
        FakeAi(){super("");}
        @Override public Map<Integer,String> generateChapterOutlines(String subject,List<WeeklyCurriculum> chapters){
            calls.add(chapters.stream().map(WeeklyCurriculum::getWeekNumber).toList());
            if(calls.size()==failOn)throw new IllegalStateException("quota reached");
            Map<Integer,String> result=new LinkedHashMap<>();for(var p:chapters){result.put(p.getWeekNumber(),"## Kiến thức chính\n- Nội dung "+p.getWeekNumber());if(partial)break;}return result;
        }
        @Override public List<WeeklyCurriculum> discoverChapters(String subject,String book){discoveries++;return List.of(TextbookChapters.plan(subject,1,"Nội dung chính",book,false));}
    }
    @Test void importAutomaticallyFinishesAllUnitsInSmallBatchesAndReopeningUsesCache(){
        var repo=new MemoryRepository();var ai=new FakeAi();var processor=new TextbookProcessor(repo,ai);List<String> updates=new ArrayList<>();
        var result=processor.process(1,"DB",updates::add);
        assertTrue(result.complete());assertEquals(4,result.ready());assertEquals(List.of(List.of(1,2),List.of(3,4)),ai.calls);
        assertFalse(updates.isEmpty());assertTrue(processor.process(1,"DB",s->{}).complete());assertEquals(2,ai.calls.size());
    }
    @Test void quotaStopsProcessingAndResumeOnlyRequestsUnfinishedUnits(){
        var repo=new MemoryRepository();var ai=new FakeAi();ai.failOn=2;var processor=new TextbookProcessor(repo,ai);
        var interrupted=processor.process(1,"DB",s->{});assertFalse(interrupted.complete());assertEquals(2,interrupted.ready());assertTrue(interrupted.error().contains("quota"));
        ai.failOn=-1;var resumed=processor.process(1,"DB",s->{});assertTrue(resumed.complete());assertEquals(List.of(3,4),ai.calls.get(2));assertEquals(3,ai.calls.size());
    }
    @Test void staleReadyOutlineWithWrongBoundariesIsReplacedInsteadOfReusedForever(){
        var repo=new MemoryRepository();var wrong=TextbookChapters.plan("DB",5,"Phần 5: ào chiến thắng","Nguồn sai kéo đến cuối sách",false);
        wrong.setContentStatus("READY");wrong.setDetailedSyllabus("Đề cương trộn nội dung");
        repo.saveWeeklyCurricula(1,"DB",List.of(wrong));var ai=new FakeAi();
        var result=new TextbookProcessor(repo,ai).process(1,"DB",s->{});
        assertTrue(result.complete());assertEquals(4,result.plans().size());
        assertTrue(result.plans().stream().noneMatch(p->p.getChapterTitle().startsWith("Phần 5")));
        assertEquals(List.of(List.of(1,2),List.of(3,4)),ai.calls);
    }
    @Test void missingStructureUsesAiDiscoveryInsteadOfCreatingFakeOneChapterSuccess(){
        var repo=new MemoryRepository();repo.text="Không có tiêu đề rõ.";var ai=new FakeAi();
        var result=new TextbookProcessor(repo,ai).process(1,"DB",s->{});
        assertEquals(1,ai.discoveries);assertTrue(result.complete());assertEquals("Nội dung chính",result.plans().get(0).getChapterTitle());
    }
    @Test void incompleteAiResponseSavesGoodUnitsAndReportsPendingUnits(){
        var repo=new MemoryRepository();var ai=new FakeAi();ai.partial=true;var processor=new TextbookProcessor(repo,ai);
        var result=processor.process(1,"DB",s->{});assertFalse(result.complete());assertEquals(1,result.ready());assertNotNull(result.error());
        ai.partial=false;assertTrue(processor.process(1,"DB",s->{}).complete());assertEquals(List.of(2,3),ai.calls.get(1));
    }
    @Test void discoveryFailureDoesNotReportOneRecognizedChapter(){
        var repo=new MemoryRepository();repo.text="Không có tiêu đề rõ.";
        var ai=new FakeAi(){@Override public List<WeeklyCurriculum> discoverChapters(String s,String b){throw new IllegalStateException("request timed out");}};
        var result=new TextbookProcessor(repo,ai).process(1,"DB",s->{});
        assertFalse(result.complete());assertEquals(0,result.plans().size());assertTrue(result.summary().contains("chưa xác định"));
    }
}
