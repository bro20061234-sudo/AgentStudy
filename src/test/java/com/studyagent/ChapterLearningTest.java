package com.studyagent;

import com.studyagent.parser.*;
import com.studyagent.ui.LessonReviewPanel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChapterLearningTest {
    @Test void vietnameseWrappedHistoryHeadingsIncludeIntroductionAndDoNotTreatVaoAsRomanFive(){
        String book="[Trang PDF 3]\nChương nhập môn\nĐỐI TƯỢNG, CHỨC NĂNG, NHIỆM VỤ,\nNỘI DUNG VÀ PHƯƠNG PHÁP NGHIÊN CỨU\nGiới thiệu môn học.\n"
                +"[Trang PDF 15]\nChương 1\nĐẢNG CỘNG SẢN VIỆT NAM RA ĐỜI VÀ LÃNH ĐẠO\nĐẤU TRANH GIÀNH CHÍNH QUYỀN (1930 - 1945)\nMỤC TIÊU\nGiai đoạn thứ nhất.\n"
                +"[Trang PDF 61]\nChương 2\nĐẢNG LÃNH ĐẠO HAI CUỘC KHÁNG CHIẾN,\nHOÀN THÀNH GIẢI PHÓNG DÂN TỘC, THỐNG NHẤT ĐẤT NƯỚC\n(1945 - 1975)\nMỤC TIÊU\nGiai đoạn thứ hai.\n"
                +"phần vào chiến thắng của quân dân miền Nam\n[Trang PDF 120]\nChương 3\nĐẢNG LÃNH ĐẠO CẢ NƯỚC QUÁ ĐỘ LÊN\nCHỦ NGHĨA XÃ HỘI VÀ TIẾN HÀNH CÔNG CUỘC ĐỔI MỚI\n(1975 - 2018)\nMỤC TIÊU\nGiai đoạn thứ ba.";
        var chapters=TextbookChapters.detect("Lịch sử Đảng",book);
        assertEquals(4,chapters.size());assertEquals(0,chapters.get(0).getWeekNumber());
        assertTrue(chapters.get(1).getChapterTitle().contains("ĐẢNG CỘNG SẢN"));
        assertTrue(chapters.get(1).getChapterTitle().contains("1930 - 1945"));
        assertTrue(chapters.get(2).getChapterTitle().contains("1945 - 1975"));
        assertFalse(chapters.get(1).getSourceText().contains("Giai đoạn thứ ba"));
        assertTrue(TextbookChapters.detect("Test","phần vào chiến thắng của quân dân miền Nam").isEmpty());
    }
    @Test void recognizesRealChaptersNotIntroductionTableOfContentsOrMemoryPartitionLabels() {
        String book="[Trang PDF 1]\nChương 1 bao gồm những khái niệm chung của hệ điều hành.\n"
                +"Chương 2 trình bày về quản lý tiến trình.\nCHƯƠNG 1:\nGIỚI THIỆU CHUNG ............ 8\nCHƯƠNG 2:\nQUẢN LÝ TIẾN TRÌNH ........ 40\n"
                +"[Trang PDF 8]\nCHƯƠNG 1: GIỚI THIỆU CHUNG\n1.1. CÁC THÀNH PHẦN\nNội dung chương một.\n"
                +"[Trang PDF 40]\nCHƯƠNG 2: QUẢN LÝ TIẾN TRÌNH\n2.1. KHÁI NIỆM\nNội dung chương hai.\n"
                +"Chương 4\n500 MB\nChương 3\n300 MB\nChương 2\n200 MB\nChương 1\n150 MB\n";
        var chapters=TextbookChapters.detect("Hệ điều hành",book);
        assertEquals(2,chapters.size());assertEquals("Chương 1: GIỚI THIỆU CHUNG",chapters.get(0).getChapterTitle());
        assertTrue(chapters.get(0).getSourceText().contains("Nội dung chương một"));
        assertFalse(chapters.get(0).getSourceText().contains("Nội dung chương hai"));
        assertTrue(chapters.get(1).getSourceText().contains("Nội dung chương hai"));
        assertTrue(chapters.get(0).getDetailedSyllabus().contains("1.1. CÁC THÀNH PHẦN"));
        assertTrue(chapters.stream().allMatch(p->p.isChapter()&&"DRAFT".equals(p.getContentStatus())));
    }
    @Test void romanNumeralsAndSplitHeadingsAreRecognized() {
        String book="[Trang PDF 3]\nCHƯƠNG I\n\u00a0\nTỔNG QUAN\nNội dung đầu.\n[Trang PDF 9]\nChapter II: Processes\nChapter contents.";
        var chapters=TextbookChapters.detect("OS",book);
        assertEquals(2,chapters.size());assertEquals(1,chapters.get(0).getWeekNumber());assertEquals(2,chapters.get(1).getWeekNumber());
        assertTrue(chapters.get(0).getChapterTitle().contains("TỔNG QUAN"));
        assertTrue(LessonSources.reference(chapters.get(1).getSourceText()).contains("9"));
    }
    @Test void unknownChapterBoundariesAreExplicitNotSplitIntoArbitraryCalendarSlots() {
        var chapters=TextbookChapters.detect("Môn học","Nội dung không có tiêu đề chương.");
        assertTrue(chapters.isEmpty(),"Không được báo giả một chương; pipeline sẽ dùng AI nhận diện cấu trúc.");
    }
    @Test void recognizesLessonsAsLearningUnitsAndPreservesCombinedLessonTitle() {
        String book="[Trang PDF 1]\nBài 1: Khái niệm chung ........ 11\nBài 2: Kiến trúc ........ 19\n"
                +"[Trang PDF 12]\nBài 1: Khái niệm chung về cơ sở dữ liệu\nDữ liệu và hệ quản trị.\n"
                +"[Trang PDF 20]\nBài 2: Kiến trúc cơ sở dữ liệu\nCác mức trừu tượng.\n"
                +"[Trang PDF 28]\nBài 3 và 4: Các mô hình dữ liệu\nMô hình quan hệ.\n";
        var plans=TextbookChapters.detect("Cơ sở dữ liệu",book);
        assertEquals(3,plans.size());assertTrue(plans.get(0).getChapterTitle().startsWith("Bài 1:"));
        assertEquals("Bài 3 và 4: Các mô hình dữ liệu",plans.get(2).getChapterTitle());
        assertTrue(plans.get(1).getSourceText().contains("Các mức trừu tượng"));
    }
    @Test void chapterProgressAndQuizKeySurviveOutlineGenerationAndFormattingChanges() {
        var chapter=TextbookChapters.detect("OOP","CHƯƠNG 1: CLASS\n1.1. Objects\nNội dung.").get(0);
        String before=LessonReviewPanel.lessonKey("OOP","book",chapter);
        chapter.setDetailedSyllabus("## Đề cương AI\n- Nội dung đã soạn.");chapter.setContentStatus("READY");
        assertEquals(before,LessonReviewPanel.lessonKey("OOP","book",chapter));
        chapter.setSourceText(chapter.getSourceText()+"Nguồn đã cập nhật");
        assertNotEquals(before,LessonReviewPanel.lessonKey("OOP","book",chapter));
    }
}
