package com.studyagent.scheduler;

import com.studyagent.ai.GeminiService;
import com.studyagent.model.StudySession;
import com.studyagent.model.TimetableEntry;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StudyOptimizer {
    public static final int MAX_STUDY_MINUTES_PER_SUBJECT_PER_DAY = 60;
    public static final int STUDY_MINUTES_PER_PERIOD = 15; // 15 phút tự học theo mỗi tiết học trên lớp
    private static final int CLASS_DEMAND_CUTOFF = 18 * 60;

    private static final int EVENING_START = 20 * 60;

    private static final int EVENING_END = 22 * 60;

    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile(
            "(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s*(?:đến|-)\\s*(\\d{1,2}/\\d{1,2}/\\d{2,4})",
            Pattern.CASE_INSENSITIVE);
    private static final String[] DAY_NAMES = {
            "Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ Nhật"
    };


    public StudyOptimizer(GeminiService geminiService) {
        // Lịch tự học chỉ cần xếp môn và giờ bằng thuật toán cục bộ; không gọi AI.
    }

    public List<StudySession> generateOptimalSchedule(List<TimetableEntry> schoolSchedule) {
        if (schoolSchedule == null || schoolSchedule.isEmpty()) {
            return new ArrayList<>();
        }

        return generateHeuristicSchedule(schoolSchedule);
    }

    public List<StudySession> generateOptimalScheduleForWeek(List<TimetableEntry> schoolSchedule, LocalDate targetDate) {
        if (schoolSchedule == null || schoolSchedule.isEmpty()) {
            return new ArrayList<>();
        }
        if (targetDate == null) {
            return generateOptimalSchedule(schoolSchedule);
        }

        List<TimetableEntry> activeEntries = new ArrayList<>();
        for (TimetableEntry entry : schoolSchedule) {
            if (isEntryActiveInWeek(entry, targetDate)) {
                activeEntries.add(entry);
            }
        }
        return generateOptimalSchedule(activeEntries);
    }

    public List<StudySession> generateSemesterSchedule(List<TimetableEntry> schoolSchedule) {
        return generateSemesterSchedule(schoolSchedule, null, null);
    }

    /**
     * Sinh lịch tự học theo kỳ.
     * @param overrideStart ngày bắt đầu kỳ (ưu tiên hơn khoảng ngày trong note TKB); null = tự suy từ note
     * @param overrideEnd   ngày kết thúc kỳ; null = tự suy từ note
     */
    public List<StudySession> generateSemesterSchedule(List<TimetableEntry> schoolSchedule,
                                                       LocalDate overrideStart,
                                                       LocalDate overrideEnd) {
        if (schoolSchedule == null || schoolSchedule.isEmpty()) {
            return new ArrayList<>();
        }

        LocalDate semesterStart;
        LocalDate semesterEnd;

        if (overrideStart != null && overrideEnd != null) {
            if (overrideEnd.isBefore(overrideStart)) {
                throw new IllegalArgumentException("Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.");
            }
            semesterStart = overrideStart;
            semesterEnd = overrideEnd;
        } else {
            List<DateRange> dateRanges = schoolSchedule.stream()
                    .map(StudyOptimizer::getDateRange)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (dateRanges.isEmpty()) {
                // Không có khoảng ngày → chỉ sinh 1 tuần mẫu (không gán ngày cụ thể)
                return generateOptimalSchedule(schoolSchedule);
            }
            semesterStart = dateRanges.stream().map(DateRange::start).min(LocalDate::compareTo).orElseThrow();
            semesterEnd = dateRanges.stream().map(DateRange::end).max(LocalDate::compareTo).orElseThrow();
        }

        LocalDate firstWeek = semesterStart.with(DayOfWeek.MONDAY);
        LocalDate lastWeek = semesterEnd.with(DayOfWeek.MONDAY);

        List<StudySession> semesterSessions = new ArrayList<>();
        for (LocalDate weekStart = firstWeek;
             !weekStart.isAfter(lastWeek);
             weekStart = weekStart.plusWeeks(1)) {
            List<TimetableEntry> entriesForWeek = new ArrayList<>();
            for (TimetableEntry entry : schoolSchedule) {
                LocalDate classDate = dateForDayOfWeek(weekStart, entry.getDayOfWeek());
                if (classDate != null && !classDate.isBefore(semesterStart) && !classDate.isAfter(semesterEnd)
                        && isEntryActiveOnDate(entry, classDate)) {
                    entriesForWeek.add(entry);
                }
            }

            boolean[] allowedDays=new boolean[7];
            for(int day=0;day<7;day++){LocalDate date=weekStart.plusDays(day);allowedDays[day]=!date.isBefore(semesterStart)&&!date.isAfter(semesterEnd);}
            for (StudySession session : generateHeuristicSchedule(entriesForWeek,allowedDays)) {
                LocalDate studyDate = dateForDayOfWeek(weekStart, session.getDayOfWeek());
                if (studyDate != null && !studyDate.isBefore(semesterStart) && !studyDate.isAfter(semesterEnd)) {
                    session.setScheduledDate(studyDate.toString());
                    semesterSessions.add(session);
                }
            }
        }
        return semesterSessions;
    }

    public static boolean isEntryActiveOnDate(TimetableEntry entry, LocalDate targetDate) {
        if (entry == null || targetDate == null) return false;
        DateRange range = getDateRange(entry);
        return range == null || (!targetDate.isBefore(range.start()) && !targetDate.isAfter(range.end()));
    }

    public static boolean isEntryActiveInWeek(TimetableEntry entry, LocalDate targetDate) {
        if (entry == null) return false;
        if (targetDate == null) return true;
        DateRange range = getDateRange(entry);
        if (range == null) return true;
        LocalDate weekStart = targetDate.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = targetDate.with(DayOfWeek.SUNDAY);
        return !weekStart.isAfter(range.end()) && !weekEnd.isBefore(range.start());
    }

    private static DateRange getDateRange(TimetableEntry entry) {
        String note = entry == null ? null : entry.getNote();
        if (note == null || note.isBlank()) return null;
        Matcher matcher = DATE_RANGE_PATTERN.matcher(note);
        if (!matcher.find()) return null;
        LocalDate start = parseDate(matcher.group(1));
        LocalDate end = parseDate(matcher.group(2));
        return start == null || end == null || end.isBefore(start) ? null : new DateRange(start, end);
    }

    private LocalDate dateForDayOfWeek(LocalDate weekStart, String dayName) {
        int day = dayIndex(dayName);
        return day < 0 ? null : weekStart.plusDays(day);
    }

    public static LocalDate parseDate(String dateStr) {
        if (dateStr == null) return null;
        String s = dateStr.trim();
        String[] parts = s.split("/");
        if (parts.length != 3) return null;
        try {
            int d = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            if (parts[2].length()<=2 && y < 100) y += 2000;
            return LocalDate.of(y, m, d);
        } catch (Exception e) {
            return null;
        }
    }

    public static int calculatePeriods(TimetableEntry entry) {
        if (entry == null) return 1;
        if (entry.getNote() != null) {
            Pattern p = Pattern.compile("(?i)(\\d+)\\s*tiết");
            Matcher m = p.matcher(entry.getNote());
            if (m.find()) {
                try {
                    return Integer.parseInt(m.group(1));
                } catch (NumberFormatException ignored) {}
            }
        }
        int start = parseMinutes(entry.getStartTime());
        int end = parseMinutes(entry.getEndTime());
        if (start < 0 || end <= start) return 1;
        int diff = end - start;
        if (diff <= 65) return 1;
        if (diff <= 130) return 2;
        if (diff <= 190) return 3;
        if (diff <= 250) return 4;
        return Math.max(1, (diff + 30) / 60);
    }

    private List<StudySession> generateHeuristicSchedule(List<TimetableEntry> schoolSchedule) {
        boolean[] allowed=new boolean[7];java.util.Arrays.fill(allowed,true);
        return generateHeuristicSchedule(schoolSchedule,allowed);
    }

    private List<StudySession> generateHeuristicSchedule(List<TimetableEntry> schoolSchedule,boolean[] allowedDays) {
        // Quarter-hour slots keep every study minute inside 20:00–22:00 and outside class time.
        boolean[][] free=new boolean[7][8];for(int day=0;day<7;day++)java.util.Arrays.fill(free[day],allowedDays[day]);
        List<Map<String,Integer>> allocated=new ArrayList<>();for(int day=0;day<7;day++)allocated.add(new HashMap<>());
        List<Map<String,StudyTask>> demand=new ArrayList<>();for(int day=0;day<7;day++)demand.add(new LinkedHashMap<>());
        for(var entry:schoolSchedule){
            int day=dayIndex(entry.getDayOfWeek()),start=parseMinutes(entry.getStartTime()),end=parseMinutes(entry.getEndTime());
            if(day<0||start<0||end<=start||entry.getSubjectName()==null||entry.getSubjectName().isBlank())continue;
            for(int slot=0;slot<8;slot++){int slotStart=EVENING_START+slot*15;if(start<slotStart+15&&end>slotStart)free[day][slot]=false;}
            // Evening classes still block occupied time, but do not generate self-study demand.
            if(start>=CLASS_DEMAND_CUTOFF)continue;
            String key=entry.getSubjectName().trim().toLowerCase(Locale.ROOT);var previous=demand.get(day).get(key);
            int periods=Math.max(0,calculatePeriods(entry));
            if(end>CLASS_DEMAND_CUTOFF)periods=(int)Math.ceil(periods*(CLASS_DEMAND_CUTOFF-start)/(double)(end-start));
            int minutes=periods*STUDY_MINUTES_PER_PERIOD;
            demand.get(day).put(key,new StudyTask(day,entry.getSubjectName().trim(),minutes+(previous==null?0:previous.durationMinutes())));
        }
        List<StudySession> result=new ArrayList<>();
        for(int source=0;source<7;source++)for(var task:demand.get(source).values()){
            int remaining=task.durationMinutes();
            for(int offset=0;offset<7&&remaining>0;offset++){
                int day=(source+offset)%7;
                String subjectKey=task.subjectName().trim().toLowerCase(Locale.ROOT);
                int allowance=MAX_STUDY_MINUTES_PER_SUBJECT_PER_DAY-allocated.get(day).getOrDefault(subjectKey,0);
                if(allowance<=0)continue;
                for(int slot=0;slot<8&&remaining>0;){
                    if(allowance<=0)break;
                    if(!free[day][slot]){slot++;continue;}
                    int first=slot;while(slot<8&&free[day][slot]&&remaining>0&&allowance>0){free[day][slot]=false;remaining-=15;allowance-=15;allocated.get(day).merge(subjectKey,15,Integer::sum);slot++;}
                    StudySession session=new StudySession(DAY_NAMES[day],formatTime(EVENING_START+first*15),formatTime(EVENING_START+slot*15),task.subjectName(),"","");
                    result.add(session);
                }
            }
            if(remaining>0)throw new IllegalStateException("Không đủ giờ trống 20:00–22:00, tối đa 60 phút/môn/ngày, để xếp thêm "+remaining+" phút môn "+task.subjectName()+". Hãy giảm khối lượng hoặc mở rộng khoảng ngày.");
        }
        result.sort(java.util.Comparator.comparingInt((StudySession s)->dayIndex(s.getDayOfWeek())).thenComparing(StudySession::getStartTime));
        List<StudySession> merged=new ArrayList<>();
        for(var session:result){
            var last=merged.isEmpty()?null:merged.get(merged.size()-1);
            if(last!=null&&last.getDayOfWeek().equals(session.getDayOfWeek())&&last.getSubjectName().equalsIgnoreCase(session.getSubjectName())&&last.getEndTime().equals(session.getStartTime()))last.setEndTime(session.getEndTime());
            else merged.add(session);
        }
        return merged;
    }
    public static int dayIndex(String day) {
        if (day == null) {
            return -1;
        }
        String normalized = Normalizer.normalize(day.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        return switch (normalized) {
            case "thu 2", "thu hai", "monday", "mon" -> 0;
            case "thu 3", "thu ba", "tuesday", "tue", "tues" -> 1;
            case "thu 4", "thu tu", "wednesday", "wed" -> 2;
            case "thu 5", "thu nam", "thursday", "thu" -> 3;
            case "thu 6", "thu sau", "friday", "fri" -> 4;
            case "thu 7", "thu bay", "saturday", "sat" -> 5;
            case "chu nhat", "sunday", "sun" -> 6;
            default -> -1;
        };
    }

    public static int parseMinutes(String time) {
        if (time == null) {
            return -1;
        }
        try {
            String[] parts = time.trim().split(":");
            if (parts.length != 2) {
                return -1;
            }
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59) {
                return -1;
            }
            return hours * 60 + minutes;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String formatTime(int minutes) {
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }

    private record StudyTask(int sourceDay, String subjectName, int durationMinutes) {}
    private record DateRange(LocalDate start, LocalDate end) {}
}
