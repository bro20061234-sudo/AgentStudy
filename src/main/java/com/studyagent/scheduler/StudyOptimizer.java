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
    public static final int MAX_STUDY_MINUTES_PER_SUBJECT_PER_WEEK = 120; // Tối đa 2 tiếng / môn / tuần
    public static final int STUDY_MINUTES_PER_PERIOD = 30; // 30 phút tự học theo mỗi tiết học trên lớp

    private static final int EVENING_START = 19 * 60 + 30;
    private static final int EVENING_CLASS_START = 19 * 60;
    private static final int EVENING_END = 23 * 60;
    private static final int BREAK_AFTER_CLASS = 30;
    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile(
            "(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s*(?:đến|-)\\s*(\\d{1,2}/\\d{1,2}/\\d{2,4})",
            Pattern.CASE_INSENSITIVE);
    private static final String[] DAY_NAMES = {
            "Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ Nhật"
    };

    private final GeminiService geminiService;

    public StudyOptimizer(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    public List<StudySession> generateOptimalSchedule(List<TimetableEntry> schoolSchedule) {
        if (schoolSchedule == null || schoolSchedule.isEmpty()) {
            return new ArrayList<>();
        }

        List<StudySession> suggestions = geminiService.hasApiKey()
                ? geminiService.optimizeStudyScheduleWithAI(schoolSchedule)
                : List.of();
        return generateHeuristicSchedule(schoolSchedule, suggestions);
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
        if (schoolSchedule == null || schoolSchedule.isEmpty()) {
            return new ArrayList<>();
        }

        List<DateRange> dateRanges = schoolSchedule.stream()
                .map(StudyOptimizer::getDateRange)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (dateRanges.isEmpty()) {
            return generateOptimalSchedule(schoolSchedule);
        }

        LocalDate semesterStart = dateRanges.stream().map(DateRange::start).min(LocalDate::compareTo).orElseThrow();
        LocalDate semesterEnd = dateRanges.stream().map(DateRange::end).max(LocalDate::compareTo).orElseThrow();
        List<StudySession> semesterSessions = new ArrayList<>();
        List<StudySession> suggestions = geminiService.hasApiKey()
                ? geminiService.optimizeStudyScheduleWithAI(schoolSchedule)
                : List.of();

        for (LocalDate weekStart = semesterStart.with(DayOfWeek.MONDAY);
             !weekStart.isAfter(semesterEnd);
             weekStart = weekStart.plusWeeks(1)) {
            List<TimetableEntry> entriesForWeek = new ArrayList<>();
            for (TimetableEntry entry : schoolSchedule) {
                LocalDate classDate = dateForDayOfWeek(weekStart, entry.getDayOfWeek());
                if (classDate != null && !classDate.isBefore(semesterStart) && !classDate.isAfter(semesterEnd)
                        && isEntryActiveOnDate(entry, classDate)) {
                    entriesForWeek.add(entry);
                }
            }

            for (StudySession session : generateHeuristicSchedule(entriesForWeek, suggestions)) {
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
            if (y < 100) y += 2000;
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

    private List<StudySession> generateHeuristicSchedule(
            List<TimetableEntry> schoolSchedule, List<StudySession> suggestions) {
        List<List<StudyTask>> tasksByDay = new ArrayList<>();
        for (int day = 0; day < DAY_NAMES.length; day++) {
            tasksByDay.add(new ArrayList<>());
        }

        // Nhóm các buổi học theo môn để tính tổng số tiết trong tuần và áp dụng giới hạn tối đa 2 tiếng (120 phút)/môn/tuần
        Map<String, Integer> totalPeriodsBySubject = new HashMap<>();
        Map<String, List<TimetableEntry>> validEntriesBySubject = new LinkedHashMap<>();

        for (TimetableEntry entry : schoolSchedule) {
            int day = dayIndex(entry.getDayOfWeek());
            int start = parseMinutes(entry.getStartTime());
            int end = parseMinutes(entry.getEndTime());
            if (day < 0 || start < 0 || end <= start) {
                System.err.printf("⚠️ [StudyOptimizer] Bỏ qua lịch học không hợp lệ: %s%n", entry);
                continue;
            }

            if (start >= EVENING_CLASS_START) {
                continue;
            }

            String subjectName = entry.getSubjectName() != null ? entry.getSubjectName().trim() : "Môn học";
            String subjectKey = subjectName.toLowerCase(Locale.ROOT);
            int periods = calculatePeriods(entry);

            totalPeriodsBySubject.put(subjectKey, totalPeriodsBySubject.getOrDefault(subjectKey, 0) + periods);
            validEntriesBySubject.computeIfAbsent(subjectKey, k -> new ArrayList<>()).add(entry);
        }

        // Phân bổ thời gian tự học theo số tiết, tối đa 2 tiếng (120 phút) mỗi môn trên tuần
        for (Map.Entry<String, List<TimetableEntry>> mapEntry : validEntriesBySubject.entrySet()) {
            String subjectKey = mapEntry.getKey();
            List<TimetableEntry> subjectEntries = mapEntry.getValue();
            int totalPeriods = totalPeriodsBySubject.getOrDefault(subjectKey, 1);

            // Mỗi tiết trên lớp tương ứng 30 phút tự học, tối đa 120 phút (2 tiếng) cho môn đó trong tuần
            int totalWeeklyStudyMinutes = Math.min(totalPeriods * STUDY_MINUTES_PER_PERIOD, MAX_STUDY_MINUTES_PER_SUBJECT_PER_WEEK);
            int remainingSubjectMinutes = totalWeeklyStudyMinutes;

            for (int i = 0; i < subjectEntries.size(); i++) {
                TimetableEntry entry = subjectEntries.get(i);
                int day = dayIndex(entry.getDayOfWeek());
                int periods = calculatePeriods(entry);

                int allocatedMinutes;
                if (i == subjectEntries.size() - 1) {
                    allocatedMinutes = remainingSubjectMinutes;
                } else {
                    allocatedMinutes = Math.round((float) periods / totalPeriods * totalWeeklyStudyMinutes);
                    allocatedMinutes = Math.max(15, (allocatedMinutes / 15) * 15);
                    allocatedMinutes = Math.min(allocatedMinutes, remainingSubjectMinutes);
                }
                remainingSubjectMinutes -= allocatedMinutes;

                if (allocatedMinutes > 0) {
                    tasksByDay.get(day).add(new StudyTask(day, entry.getSubjectName(), allocatedMinutes));
                }
            }
        }

        List<StudySession> result = new ArrayList<>();
        List<StudyTask> carryOver = new ArrayList<>();
        for (int offset = 0; offset < DAY_NAMES.length * 2; offset++) {
            int day = offset % DAY_NAMES.length;
            List<StudyTask> todayTasks = new ArrayList<>(carryOver);
            carryOver.clear();
            if (offset < DAY_NAMES.length) {
                todayTasks.addAll(tasksByDay.get(day));
            }

            int cursor = findEveningStart(day, schoolSchedule);
            for (StudyTask task : todayTasks) {
                if (cursor + task.durationMinutes > EVENING_END) {
                    carryOver.add(task);
                    continue;
                }

                StudySession suggestion = findSuggestion(task, suggestions);
                StudySession session = new StudySession();
                session.setDayOfWeek(DAY_NAMES[day]);
                session.setStartTime(formatTime(cursor));
                session.setEndTime(formatTime(cursor + task.durationMinutes));
                session.setSubjectName(task.subjectName);
                session.setTopicOverview(suggestion != null && suggestion.getTopicOverview() != null
                        ? suggestion.getTopicOverview()
                        : "Ôn tập kiến thức đã học trên lớp môn " + task.subjectName);
                session.setPreparationMaterials(suggestion != null && suggestion.getPreparationMaterials() != null
                        ? suggestion.getPreparationMaterials()
                        : "Xem lại bài giảng và làm bài tập của môn học.");
                result.add(session);
                cursor += task.durationMinutes;
            }
        }

        if (!carryOver.isEmpty()) {
            System.err.printf("⚠️ [StudyOptimizer] Còn %d phiên tự học không xếp được trong 7 ngày tiếp theo vì vượt khung 19:30–23:00.%n",
                    carryOver.size());
        }
        return result;
    }

    private int findEveningStart(int day, List<TimetableEntry> schoolSchedule) {
        int latestEveningClassEnd = EVENING_START;
        for (TimetableEntry entry : schoolSchedule) {
            if (dayIndex(entry.getDayOfWeek()) != day) {
                continue;
            }
            int classStart = parseMinutes(entry.getStartTime());
            int classEnd = parseMinutes(entry.getEndTime());
            if (classStart >= 0 && classStart < EVENING_END
                    && classEnd > classStart && classEnd >= EVENING_START) {
                latestEveningClassEnd = Math.max(latestEveningClassEnd, classEnd);
            }
        }
        return latestEveningClassEnd == EVENING_START
                ? EVENING_START
                : latestEveningClassEnd + BREAK_AFTER_CLASS;
    }

    private StudySession findSuggestion(StudyTask task, List<StudySession> suggestions) {
        for (StudySession suggestion : suggestions) {
            if (suggestion.getSubjectName() != null
                    && suggestion.getSubjectName().trim().equalsIgnoreCase(task.subjectName)
                    && dayIndex(suggestion.getDayOfWeek()) == task.sourceDay) {
                return suggestion;
            }
        }
        return null;
    }

    private int dayIndex(String day) {
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
