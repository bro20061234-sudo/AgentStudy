package com.studyagent.model;

import com.studyagent.scheduler.ScheduleBlocks;
import java.time.*;
import java.util.List;

/** Time-derived status also catches missed sessions while the computer was switched off. */
public final class StudyProgress {
    public static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    public enum Status {
        COMPLETED("Hoàn thành","✓"),UPCOMING("Chưa đến lịch","○"),DUE("Đến giờ học","!"),IN_PROGRESS("Đang học","▶"),OVERDUE("Quá hạn","!");
        public final String label,symbol;
        Status(String label,String symbol){this.label=label;this.symbol=symbol;}
    }
    public record Summary(long completedDue,long due,long overdue,long upcoming,long completedEarly){}
    private StudyProgress(){}
    public static LocalDateTime start(StudySession s){return LocalDateTime.of(LocalDate.parse(s.getScheduledDate()),LocalTime.parse(s.getStartTime()));}
    public static LocalDateTime end(StudySession s){return LocalDateTime.of(LocalDate.parse(s.getScheduledDate()),LocalTime.parse(s.getEndTime()));}
    public static boolean started(List<StudySession> members){return members.stream().anyMatch(s->s.hasStarted()||s.isCompleted());}
    public static Status status(List<StudySession> members,LocalDateTime now){
        if(members.stream().allMatch(StudySession::isCompleted))return Status.COMPLETED;
        LocalDateTime start;
        try{start=members.stream().map(StudyProgress::start).min(LocalDateTime::compareTo).orElseThrow();}catch(RuntimeException e){return Status.UPCOMING;}
        boolean started=started(members);
        if(started){LocalDateTime end=members.stream().map(StudyProgress::end).max(LocalDateTime::compareTo).orElse(start);return now.isBefore(end)?Status.IN_PROGRESS:Status.OVERDUE;}
        if(now.isBefore(start))return Status.UPCOMING;
        return now.isBefore(start.plusMinutes(10))?Status.DUE:Status.OVERDUE;
    }
    public static Summary summary(List<StudySession> sessions,LocalDateTime now){
        long completed=0,due=0,overdue=0,upcoming=0,early=0;
        for(var block:ScheduleBlocks.mergeStudySessions(sessions)){
            Status state=status(block.sessions(),now);boolean reached;
            try{reached=!now.isBefore(start(block.sessions().getFirst()));}catch(RuntimeException e){reached=false;}
            if(reached){due++;if(state==Status.COMPLETED)completed++;}
            else if(state==Status.COMPLETED)early++;else upcoming++;
            if(state==Status.OVERDUE)overdue++;
        }
        return new Summary(completed,due,overdue,upcoming,early);
    }
}
