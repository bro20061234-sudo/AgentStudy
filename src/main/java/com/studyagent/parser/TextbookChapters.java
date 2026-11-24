package com.studyagent.parser;

import com.studyagent.model.WeeklyCurriculum;
import java.util.*;
import java.util.regex.*;

/** Real chapter boundaries, independent from calendar slots. Ignores TOC and prose mentions. */
public final class TextbookChapters {
    private TextbookChapters() {}
    private record Heading(int number, String title, int offset, int level) {}
    private static final Pattern HEADING = Pattern.compile(
            "(?imu)^\\h*((?:chương|chƣơng|chuong|chapter|bài|bai|lesson|unit|phần|phan|part))\\h+(\\d{1,3}|[ivxlcdm]{1,8}|nhập\\h+môn|mở\\h+đầu|introduction)(?=\\h|[:.\\-–]|$)\\h*[:.\\-–]?\\h*([^\\r\\n]*)$");

    public static boolean isUnresolved(WeeklyCurriculum plan) {
        return plan.getChapterTitle() == null || plan.getChapterTitle().contains("chưa nhận diện") || "UNRESOLVED".equals(plan.getContentStatus());
    }

    public static List<WeeklyCurriculum> detect(String subject, String book) {
        if (book == null || book.isBlank()) throw new IllegalArgumentException("Không có nội dung giáo trình.");
        book = book.replace('\u00a0', ' ');
        List<Heading> candidates = new ArrayList<>(); Matcher matcher = HEADING.matcher(book);
        while (matcher.find()) {
            String label=matcher.group(1), title=matcher.group(3).strip();
            int number = number(matcher.group(2));
            if (number < 0 || number > 100) continue;
            if (title.isBlank()) {
                String following=book.substring(matcher.end(),Math.min(book.length(),matcher.end()+700));
                StringBuilder joined=new StringBuilder();
                for(String line:following.lines().toList()) {
                    line=line.strip();if(line.isBlank())continue;
                    if(joined.isEmpty()) {joined.append(line);continue;}
                    // Uppercase wrapped chapter names and date ranges belong to the same title.
                    boolean date=line.matches("^\\(?\\d{4}\\h*[-–]\\h*\\d{4}\\)?.*");
                    boolean uppercase=line.equals(line.toUpperCase(Locale.ROOT)) && line.codePoints().anyMatch(Character::isLetter);
                    if(line.matches("(?iu)^(?:MỤC TIÊU|VỀ KIẾN THỨC|I[.) ].*|\\[Trang PDF.*|CHƯƠNG.*|BÀI.*)$")||(!date&&!uppercase)||joined.length()+line.length()>230)break;
                    joined.append(' ').append(line);
                }
                title=joined.toString();
                // Figure labels (e.g. 'Chương 4 / 500 MB') and empty chapter labels are not chapters.
                if (title.isEmpty() || !Character.isLetter(title.codePointAt(0)) || title.startsWith("[Trang PDF")) continue;
            }
            if (title.matches(".*[.·…]{3,}.*") || title.matches(".*\\t+\\h*\\d+\\h*$")) continue;
            boolean upperLabel=label.equals(label.toUpperCase(Locale.ROOT));
            boolean upperTitle=title.equals(title.toUpperCase(Locale.ROOT));
            if (title.length()>240 || (!upperLabel && !upperTitle && title.matches("(?iu).*(?:bao gồm|trình bày|trống và|cũng|sẽ được|đã được|này|của chương).*"))) continue;
            if (title.matches("(?iu)^(?:bao gồm|trình bày|trống|cũng|là|sẽ|được)\\b.*")) continue;
            if (title.length()<3 || title.codePoints().noneMatch(Character::isLetter)) continue;
            String kind=label.toLowerCase(Locale.ROOT);
            int level=kind.matches("chương|chƣơng|chuong|chapter")?0:kind.matches("bài|bai|lesson|unit")?1:2;
            String prefix=level==0?"Chương":level==1?"Bài":"Phần";
            String separator=title.matches("(?iu)^và\\h*\\d+.*")?" ":": ";
            String labelNumber=number==0?"Chương nhập môn":prefix+" "+number;
            candidates.add(new Heading(number,labelNumber+separator+title.replaceAll("\\h+"," ").strip(),matcher.start(),level));
        }
        // First real occurrence of each number, in ascending order. TOC entries were removed above.
        List<Heading> chapters=new ArrayList<>(); int last=-1;
        int level=candidates.stream().mapToInt(Heading::level).min().orElse(0);
        for (Heading h:candidates) if(h.level()==level && h.number()>last) {chapters.add(h);last=h.number();}
        List<WeeklyCurriculum> plans=new ArrayList<>();
        if(chapters.isEmpty()) {
            return plans;
        }
        for(int i=0;i<chapters.size();i++) {
            Heading h=chapters.get(i);int end=i+1<chapters.size()?chapters.get(i+1).offset():book.length();
            // Include the actual page containing the chapter heading, even when it starts mid-page.
            Matcher pages=LessonSources.PAGE.matcher(book);String page="";
            while(pages.find()&&pages.start()<=h.offset())page=pages.group();
            String source=(page.isBlank()?"":page+"\n")+book.substring(h.offset(),end);
            plans.add(plan(subject,h.number(),h.title(),source,false));
        }
        return plans;
    }
    public static WeeklyCurriculum plan(String subject,int number,String title,String source,boolean uncertain) {
        WeeklyCurriculum p=new WeeklyCurriculum();p.setSubjectName(subject);p.setWeekNumber(number);p.setChapterTitle(title);
        p.setSourceText(source);p.setContentStatus("DRAFT");
        StringBuilder content=new StringBuilder("## Nội dung chương\n");
        if(uncertain)content.append("- Chưa nhận diện được tiêu đề chương trong văn bản. Chưa tự chia sách thành nhiều bài; bạn vẫn có thể đọc file gốc.\n");
        else content.append("- Bài học này tương ứng một chương của giáo trình, độc lập với số ca tự học trên lịch.\n");
        List<String> sections=source.lines().map(String::strip)
                .filter(s->s.matches("^\\d+\\.\\d+(?:\\.\\d+)*[.)]?\\h+\\S.*")&&!s.matches(".*[.·…]{3,}.*"))
                .distinct().limit(14).toList();
        if(!sections.isEmpty()){content.append("\n## Các mục trong chương\n");for(String s:sections)content.append("- ").append(s).append('\n');}
        content.append("\n## Đề cương AI\n- Đang chờ soạn tự động. Nếu quá trình trước bị ngắt, bấm “Phân tích / tiếp tục giáo trình”; các mục đã xong không cần soạn lại. Nội dung học độc lập với số ca trên lịch.");
        p.setDetailedSyllabus(LessonSources.withReference(content.toString(),source));return p;
    }
    private static int number(String value) {
        if(value.matches("(?iu)nhập\\h+môn|mở\\h+đầu|introduction"))return 0;
        if(value.matches("\\d+"))return Integer.parseInt(value);
        if(!value.matches("(?i)M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})"))return -1;
        int total=0,previous=0;String roman=value.toUpperCase(Locale.ROOT);
        for(int i=roman.length()-1;i>=0;i--){int digit=switch(roman.charAt(i)){case 'I'->1;case 'V'->5;case 'X'->10;case 'L'->50;case 'C'->100;case 'D'->500;case 'M'->1000;default->0;};total+=digit<previous?-digit:digit;previous=digit;}
        return total;
    }
}
