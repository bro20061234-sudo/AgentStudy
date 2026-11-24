package com.studyagent.ai;

import com.studyagent.model.WeeklyCurriculum;
import com.studyagent.repository.StudyRepository;
import java.util.*;
import java.util.function.Consumer;

/** Import-to-completion pipeline. Commits each successful outline and resumes only missing ones. */
public final class TextbookProcessor {
    public record Result(List<WeeklyCurriculum> plans, int ready, String error) {
        public boolean complete(){return !plans.isEmpty()&&ready==plans.size()&&error==null;}
        public String summary(){
            if(plans.isEmpty())return "Đã lưu giáo trình nhưng chưa xác định được các chương/bài. "+Objects.toString(error,"");
            return "Đã soạn "+ready+"/"+plans.size()+" chương/bài."+(error==null?"":" Tạm dừng: "+error);
        }
    }
    private final StudyRepository repository;
    private final GeminiService ai;
    private static final java.util.concurrent.ConcurrentMap<String,java.util.concurrent.locks.ReentrantLock> JOBS=new java.util.concurrent.ConcurrentHashMap<>();
    public TextbookProcessor(StudyRepository repository,GeminiService ai){this.repository=repository;this.ai=ai;}
    public Result process(long chatId,String subject,Consumer<String> progress){
        var lock=JOBS.computeIfAbsent(chatId+":"+subject.trim().toLowerCase(Locale.ROOT),key->new java.util.concurrent.locks.ReentrantLock());
        if(!lock.tryLock()){
            var plans=repository.getWeeklyCurricula(chatId,subject);
            return new Result(plans,(int)plans.stream().filter(p->"READY".equals(p.getContentStatus())).count(),"Giáo trình này đang được phân tích. Hãy chờ tiến trình hiện tại, không cần gửi lại.");
        }
        try{return run(chatId,subject,progress);}finally{lock.unlock();}
    }
    private Result run(long chatId,String subject,Consumer<String> progress){
        List<WeeklyCurriculum> plans=new ArrayList<>();
        try{
            progress.accept("Đang phân tích cấu trúc chương/bài của "+subject+"…");
            plans=repository.prepareChapters(chatId,subject);
            if(plans.isEmpty()){
                var book=repository.getTextbook(chatId,subject);if(book==null)throw new IllegalStateException("Không tìm thấy giáo trình đã lưu.");
                progress.accept("Đang dùng AI nhận diện cấu trúc tài liệu…");plans=ai.discoverChapters(subject,book.getContent());
                if(plans.isEmpty()||!repository.saveWeeklyCurricula(chatId,subject,plans))throw new IllegalStateException("Chưa lưu được cấu trúc giáo trình.");
                plans=repository.getWeeklyCurricula(chatId,subject);
            }
            var pending=plans.stream().filter(p->!"READY".equals(p.getContentStatus())).toList();
            int ready=plans.size()-pending.size();
            for(int offset=0;offset<pending.size();offset+=2){
                if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Đã dừng xử lý; có thể tiếp tục các mục còn lại.");
                var batch=pending.subList(offset,Math.min(offset+2,pending.size()));
                progress.accept("Đã xong "+ready+"/"+plans.size()+" mục · Đang soạn "+batch.stream().map(WeeklyCurriculum::getChapterTitle).collect(java.util.stream.Collectors.joining(" và "))+"…");
                var outlines=ai.generateChapterOutlines(subject,batch);
                int saved=0;
                for(var plan:batch){String body=outlines.get(plan.getWeekNumber());if(body!=null){repository.saveChapterOutline(chatId,plan,body);plan.setDetailedSyllabus(body);plan.setContentStatus("READY");ready++;saved++;}}
                progress.accept("Đã lưu "+ready+"/"+plans.size()+" đề cương.");
                if(saved!=batch.size())throw new IllegalStateException("AI trả thiếu đề cương trong nhóm vừa xử lý. Bấm tiếp tục để soạn các mục còn thiếu.");
            }
            return new Result(plans,ready,null);
        }catch(RuntimeException e){return new Result(plans,(int)plans.stream().filter(p->"READY".equals(p.getContentStatus())).count(),e.getMessage());}
    }
}
