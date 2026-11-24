package com.studyagent;

import com.studyagent.ai.*;
import com.studyagent.parser.*;
import com.studyagent.repository.TextbookFiles;
import com.studyagent.ui.LessonHtml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class LearningContentTest {
    @TempDir Path temp;
    @Test void quotaStopsModelFanoutAndBlocksImmediateRetries() throws Exception {
        AtomicInteger calls=new AtomicInteger();
        var transport=new GeminiTransport((model,payload)->{calls.incrementAndGet();return new GeminiTransport.Response(429,"{}","30");},List.of("first","second"));
        assertTrue(assertThrows(IllegalStateException.class,()->transport.send("{}" )).getMessage().contains("30"));
        assertThrows(IllegalStateException.class,()->transport.send("{}")); assertEquals(1,calls.get());
    }
    @Test void invalidKeyDoesNotTryEveryModel() {
        AtomicInteger calls=new AtomicInteger();
        var transport=new GeminiTransport((model,payload)->{calls.incrementAndGet();return new GeminiTransport.Response(403,"{}",null);},List.of("first","second"));
        assertThrows(IllegalStateException.class,()->transport.send("{}")); assertEquals(1,calls.get());
    }
    @Test void modelDiscoveryIsCachedAfterSuccess() throws Exception {
        List<String> models=new ArrayList<>();
        var transport=new GeminiTransport((model,payload)->{models.add(model);return new GeminiTransport.Response(model.equals("missing")?404:200,"ok",null);},List.of("missing","working"));
        assertEquals("ok",transport.send("one")); assertEquals("ok",transport.send("two"));
        assertEquals(List.of("missing","working","working"),models);
    }
    @Test void retryHintsAndDailyQuotaAreRecognized() {
        String body="{\"error\":{\"details\":[{\"retryDelay\":\"12.4s\"},{\"quotaId\":\"GenerateRequestsPerDayPerProject\"}]}}";
        assertEquals(13,GeminiTransport.retrySeconds(new GeminiTransport.Response(429,body,null)));
        assertTrue(GeminiTransport.isDailyQuota(body)); assertFalse(GeminiTransport.isDailyQuota("requests per minute"));
        assertEquals(60,GeminiTransport.retrySeconds(new GeminiTransport.Response(429,"not json",null)));
    }
    @Test void formattingRendersHeadingsBoldAndListsAndEscapesUntrustedHtml() {
        String html=LessonHtml.document("<script>","## Mục tiêu\n- Nắm **đóng gói**\n- <img src='https://example.com/a'>");
        assertTrue(html.contains("<h2>Mục tiêu</h2>")); assertTrue(html.contains("<b>đóng gói</b>"));
        assertTrue(html.contains("<li>")); assertFalse(html.contains("<script>")); assertFalse(html.contains("<img"));
    }
    @Test void legacyParagraphCanBeReadAsSectionsWithoutApiCall() {
        String html=LessonHtml.document("Buổi 1","Mục tiêu: hiểu lớp. Kiến thức chính: thuộc tính. Bài tập: tạo lớp Student.");
        assertTrue(html.contains("<h2>Kiến thức chính</h2>")); assertTrue(html.contains("<h2>Bài tập</h2>"));
    }
    @Test void sourceAllocationPreservesPhysicalPageReferencesAndLastPage() {
        String book="[Trang PDF 1]\nIntro\n[Trang PDF 2]\nClasses\n[Trang PDF 3]\nObjects\n[Trang PDF 4]\nExercises";
        String first=LessonSources.section(book,1,2),last=LessonSources.section(book,2,2);
        assertTrue(first.contains("Classes")); assertFalse(first.contains("Objects")); assertTrue(last.contains("Exercises"));
        assertTrue(LessonSources.reference(first).contains("1–2")); assertTrue(LessonSources.reference(last).contains("3–4"));
        assertTrue(LessonSources.reference("Plain Word content").contains("Chưa có số trang xác thực"));
    }
    @Test void originalFileArchivePreservesBytesIndependentlyOfSource() throws Exception {
        Path original=temp.resolve("book.pdf"); byte[] bytes={1,2,3,4}; Files.write(original,bytes);
        Path copy=TextbookFiles.archive(original,temp.resolve("library")); Files.delete(original);
        assertArrayEquals(bytes,Files.readAllBytes(copy)); assertTrue(copy.startsWith(temp));
    }
    @Test void paginatedPdfExtractionIncludesPagesBeyondOldSixtyPageLimit() throws Exception {
        Path file=temp.resolve("full.pdf");
        try(PDDocument doc=new PDDocument()) {
            for(int i=1;i<=61;i++) {
                PDPage page=new PDPage();doc.addPage(page);
                try(var stream=new PDPageContentStream(doc,page)) {
                    stream.beginText();stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);stream.newLineAtOffset(30,700);stream.showText("Content page "+i);stream.endText();
                }
            }
            doc.save(file.toFile());
        }
        String text=DocumentParser.extractTextWithPages(file.toFile());
        assertTrue(text.contains("[Trang PDF 61]"));assertTrue(text.contains("Content page 61"));
    }
    @Test void offlinePlansUseSourceExcerptsAndDoNotInventPagesOrRepeatOnlyFirstChapter() {
        var plans=new GeminiService("").generateCurriculumForSessions("OOP","[Trang PDF 1]\nClass and object\n[Trang PDF 2]\nInheritance",2);
        assertEquals(2,plans.size());assertTrue(plans.get(0).getDetailedSyllabus().contains("Class and object"));
        assertTrue(plans.get(1).getDetailedSyllabus().contains("Inheritance"));
        assertTrue(plans.get(1).getDetailedSyllabus().contains("Trang PDF 2"));
        assertTrue(plans.get(1).getDetailedSyllabus().contains("Bản phân bổ cục bộ"));
    }
}
