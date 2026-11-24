package com.studyagent.ui;

import com.studyagent.ai.GeminiService;
import com.studyagent.model.*;
import com.studyagent.parser.TimetableFiles;
import com.studyagent.repository.StudyRepository;
import com.studyagent.scheduler.StudyOptimizer;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class TimetableImportTest {
    @TempDir Path temp;
    File pdf()throws Exception{
        Path path=temp.resolve("schedule.pdf");
        try(var doc=new PDDocument()){
            var page=new PDPage();doc.addPage(page);
            try(var out=new PDPageContentStream(doc,page)){
                out.beginText();out.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),8);out.newLineAtOffset(30,740);
                out.showText("INT1319 Operating Systems 01-01 3 D24CQCN01-B 2 2 3 509-A3 Teacher 26/10/26 - 26/10/26");out.endText();
            }
            doc.save(path.toFile());
        }return path.toFile();
    }
    static class MemoryStudy extends StudyRepository {
        volatile List<TimetableEntry> entries=List.of();volatile List<StudySession> sessions=List.of();
        MemoryStudy(){super(false);}
        @Override public List<TimetableEntry> getTimetableEntries(long id){return entries;}
        @Override public List<StudySession> getStudySessions(long id){return sessions;}
        @Override public void saveTimetableEntries(long id,List<TimetableEntry> value){entries=List.copyOf(value);}
        @Override public void saveStudySessions(long id,List<StudySession> value){sessions=List.copyOf(value);}
        @Override public List<TextbookItem> getAllTextbooks(long id){return List.of();}
        @Override public List<String> getDistinctSubjects(long id){return entries.stream().map(TimetableEntry::getSubjectName).distinct().toList();}
    }
    static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    @Test void readablePdfSchedulesAndOpensCalendarOnFirstImportWithoutReparsingText()throws Exception{
        File file=pdf();var memory=new MemoryStudy();AtomicInteger pdfCalls=new AtomicInteger();
        var ai=new GeminiService(""){
            @Override public List<TimetableEntry> extractTimetableFromPdf(byte[] bytes){pdfCalls.incrementAndGet();return super.extractTimetableFromPdf(bytes);}
            @Override public List<TimetableEntry> extractTimetableFromText(String text){throw new AssertionError("PDF must not be converted back to text and parsed a second time");}
        };
        AtomicReference<StudyDesktopUI> window=new AtomicReference<>();
        try{
            SwingUtilities.invokeAndWait(()->{try{
                var ui=new StudyDesktopUI(ai,memory,new StudyOptimizer(ai));window.set(ui);
                ((JTextField)field(ui,"boundFromField")).setText("26/10/2026");((JTextField)field(ui,"boundToField")).setText("01/11/2026");
                ui.importTimetableFiles(new File[]{file});
            }catch(Exception e){throw new RuntimeException(e);}});
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);AtomicBoolean done=new AtomicBoolean();
            while(!done.get()&&System.nanoTime()<deadline){
                SwingUtilities.invokeAndWait(()->{try{done.set("schedule".equals(field(window.get(),"activeNavKey")));}catch(Exception e){throw new RuntimeException(e);}});
                if(!done.get())Thread.sleep(30);
            }
            assertTrue(done.get(),"First PDF import should navigate directly to the calendar");assertEquals(1,pdfCalls.get());
            assertEquals(1,memory.entries.size());assertEquals(1,memory.sessions.size());
            assertEquals("20:00",memory.sessions.getFirst().getStartTime());assertEquals("20:45",memory.sessions.getFirst().getEndTime());
            assertEquals("2026-10-26",memory.sessions.getFirst().getScheduledDate());
            SwingUtilities.invokeAndWait(()->{try{
                var ui=window.get();assertFalse(((JTextArea)field(ui,"timetableInput")).getText().isBlank());
                List<JButton> nav=(List<JButton>)field(ui,"navButtons");assertTrue(nav.stream().noneMatch(b->"curriculum".equals(b.getClientProperty("navKey"))));
                assertEquals(7,nav.size());
            }catch(Exception e){throw new RuntimeException(e);}});
        }finally{if(window.get()!=null)SwingUtilities.invokeAndWait(()->window.get().dispose());}
    }
    @Test void emptyPdfIsRejectedWithoutReplacingPreviouslySavedSchedule()throws Exception{
        File file=pdf();var r=new MemoryStudy();var previous=new TimetableEntry("Thứ 2","07:00","09:00","Old","","");r.entries=List.of(previous);
        var ai=new GeminiService(""){@Override public List<TimetableEntry> extractTimetableFromPdf(byte[] bytes){return List.of();}};
        assertThrows(IllegalStateException.class,()->new TimetableFiles(ai).read(new File[]{file}));assertSame(previous,r.entries.getFirst());
    }
    @Test void filePickerBatchesMixedImageFormatsOncePerMimeAndKeepsPdfMetadata()throws Exception{
        File pdf=pdf();File png=Files.write(temp.resolve("one.png"),new byte[]{1}).toFile();File jpeg=Files.write(temp.resolve("two.jpg"),new byte[]{2}).toFile();
        List<String> calls=new ArrayList<>();var source=new TimetableEntry("Thứ 2","07:00","09:00","PDF","A1","2 tiết | 26/10/26 - 01/11/26");
        var ai=new GeminiService(""){
            @Override public List<TimetableEntry> extractTimetableFromPdf(byte[] bytes){return List.of(source);}
            @Override public List<TimetableEntry> extractTimetableFromMultipleImages(List<byte[]> bytes,String mime){calls.add(mime);return List.of(new TimetableEntry("Thứ 3","07:00","09:00",mime,"",""));}
        };
        var read=new TimetableFiles(ai).read(new File[]{pdf,png,jpeg});assertEquals(3,read.size());assertSame(source,read.getFirst());assertEquals(List.of("image/png","image/jpeg"),calls);
    }
    @Test void newInputCardsKeepTextEntryOptionalAndTriggerTheRealButtons()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            AtomicInteger fileCalls=new AtomicInteger(),bookCalls=new AtomicInteger();
            JButton files=new JButton(),books=new JButton(),analyze=new JButton();files.addActionListener(e->fileCalls.incrementAndGet());books.addActionListener(e->bookCalls.incrementAndGet());
            var input=new InputPanel(files,books,analyze,new JTextArea());files.doClick();books.doClick();assertEquals(1,fileCalls.get());assertEquals(1,bookCalls.get());
            try{JPanel editor=(JPanel)field(input,"editor");assertFalse(editor.isVisible());((JButton)field(input,"textToggle")).doClick();assertTrue(editor.isVisible());}catch(Exception e){throw new RuntimeException(e);}
        });
    }
}
