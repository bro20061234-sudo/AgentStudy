package com.studyagent.parser;

import com.studyagent.ai.GeminiService;
import com.studyagent.model.TimetableEntry;
import java.io.File;
import java.nio.file.Files;
import java.util.*;

/** File extraction feeds structured entries directly to scheduling, without a second AI pass. */
public final class TimetableFiles {
    private final GeminiService ai;
    public TimetableFiles(GeminiService ai){this.ai=ai;}

    public List<TimetableEntry> read(File[] files)throws Exception{
        if(files==null||files.length==0)throw new IllegalArgumentException("Chưa chọn file thời khóa biểu.");
        List<TimetableEntry> entries=new ArrayList<>();
        Map<String,List<byte[]>> images=new LinkedHashMap<>();
        for(File file:files){
            String name=file.getName().toLowerCase(Locale.ROOT);
            byte[] bytes=Files.readAllBytes(file.toPath());
            if(name.endsWith(".pdf")){
                var parsed=ai.extractTimetableFromPdf(bytes);
                if(parsed==null||parsed.isEmpty())throw new IllegalStateException("Chưa đọc được buổi học trong "+file.getName()+". Hãy kiểm tra nội dung hoặc dùng bản PDF/ảnh rõ hơn.");
                entries.addAll(parsed);
            }else if(name.endsWith(".docx")){
                String text=DocumentParser.extractTextFromDocx(bytes);
                if(text.isBlank())throw new IllegalStateException("Không đọc được nội dung file "+file.getName());
                var parsed=ai.extractTimetableFromText(text);
                if(parsed==null||parsed.isEmpty())throw new IllegalStateException("Không tìm thấy buổi học trong "+file.getName());
                entries.addAll(parsed);
            }else{
                String mime=name.endsWith(".png")?"image/png":name.endsWith(".webp")?"image/webp":name.endsWith(".jpg")||name.endsWith(".jpeg")?"image/jpeg":null;
                if(mime==null)throw new IllegalArgumentException("File không hỗ trợ: "+file.getName()+". Chọn PDF, Word (.docx) hoặc ảnh PNG/JPG/WebP.");
                images.computeIfAbsent(mime,key->new ArrayList<>()).add(bytes);
            }
        }
        for(var group:images.entrySet()){
            var parsed=ai.extractTimetableFromMultipleImages(group.getValue(),group.getKey());
            if(parsed==null||parsed.isEmpty())throw new IllegalStateException("Chưa đọc được buổi học trong ảnh đã chọn. Hãy dùng ảnh rõ, đủ hàng và cột.");
            entries.addAll(parsed);
        }
        return entries;
    }
}
