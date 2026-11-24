package com.studyagent.scheduler;

import com.studyagent.model.TimetableEntry;
import com.studyagent.model.StudySession;
import java.util.*;

/** Display grouping after date filtering: source periods/date ranges remain untouched. */
public final class ScheduleBlocks {
    private ScheduleBlocks(){}
    public record StudyBlock(String subject,String start,String end,List<StudySession> sessions) {
        public boolean completed(){return sessions.stream().allMatch(StudySession::isCompleted);}
    }

    /** Group saved sessions for display without replacing their IDs or completion history. */
    public static List<StudyBlock> mergeStudySessions(List<StudySession> sessions){
        List<StudySession> sorted=new ArrayList<>(sessions);
        sorted.sort(Comparator.comparing((StudySession s)->Objects.toString(s.getScheduledDate(),""))
                .thenComparingInt(s->StudyOptimizer.dayIndex(s.getDayOfWeek()))
                .thenComparingInt(s->StudyOptimizer.parseMinutes(s.getStartTime())));
        List<StudyBlock> result=new ArrayList<>();
        for(var session:sorted){
            StudyBlock previous=result.isEmpty()?null:result.getLast();
            StudySession first=previous==null?null:previous.sessions().getFirst();
            int start=StudyOptimizer.parseMinutes(session.getStartTime());
            int end=StudyOptimizer.parseMinutes(session.getEndTime());
            int previousEnd=previous==null?-1:StudyOptimizer.parseMinutes(previous.end());
            boolean sameDay=first!=null&&Objects.equals(first.getScheduledDate(),session.getScheduledDate())
                    &&StudyOptimizer.dayIndex(first.getDayOfWeek())==StudyOptimizer.dayIndex(session.getDayOfWeek());
            boolean sameSubject=previous!=null&&Objects.toString(previous.subject(),"").trim()
                    .equalsIgnoreCase(Objects.toString(session.getSubjectName(),"").trim());
            if(sameDay&&sameSubject&&start>=0&&end>start&&previousEnd>=0
                    &&start>=StudyOptimizer.parseMinutes(previous.start())&&start-previousEnd<=15){
                List<StudySession> members=new ArrayList<>(previous.sessions());members.add(session);
                result.set(result.size()-1,new StudyBlock(previous.subject(),previous.start(),
                        end>previousEnd?session.getEndTime():previous.end(),List.copyOf(members)));
            }else result.add(new StudyBlock(session.getSubjectName(),session.getStartTime(),session.getEndTime(),List.of(session)));
        }
        return List.copyOf(result);
    }
    public static List<TimetableEntry> mergeClasses(List<TimetableEntry> active){
        List<TimetableEntry> sorted=new ArrayList<>(active);sorted.sort(Comparator.comparingInt((TimetableEntry e)->StudyOptimizer.dayIndex(e.getDayOfWeek())).thenComparingInt(e->StudyOptimizer.parseMinutes(e.getStartTime())));
        List<TimetableEntry> result=new ArrayList<>();
        for(var e:sorted){var previous=result.isEmpty()?null:result.get(result.size()-1);
            int gap=previous==null?Integer.MAX_VALUE:StudyOptimizer.parseMinutes(e.getStartTime())-StudyOptimizer.parseMinutes(previous.getEndTime());
            if(previous!=null&&StudyOptimizer.dayIndex(previous.getDayOfWeek())>=0
                    &&StudyOptimizer.dayIndex(previous.getDayOfWeek())==StudyOptimizer.dayIndex(e.getDayOfWeek())
                    &&Objects.toString(previous.getSubjectName(),"").trim().equalsIgnoreCase(Objects.toString(e.getSubjectName(),"").trim())
                    &&StudyOptimizer.parseMinutes(e.getStartTime())>=0&&StudyOptimizer.parseMinutes(e.getEndTime())>StudyOptimizer.parseMinutes(e.getStartTime())&&gap<=15){
                if(StudyOptimizer.parseMinutes(e.getEndTime())>StudyOptimizer.parseMinutes(previous.getEndTime()))previous.setEndTime(e.getEndTime());
                if(!Objects.equals(previous.getRoom(),e.getRoom())&&e.getRoom()!=null&&!e.getRoom().isBlank())previous.setRoom(Objects.toString(previous.getRoom(),"")+" → "+e.getRoom());
            }else result.add(new TimetableEntry(e.getDayOfWeek(),e.getStartTime(),e.getEndTime(),e.getSubjectName(),e.getRoom(),e.getNote()));
        }return result;
    }
}
