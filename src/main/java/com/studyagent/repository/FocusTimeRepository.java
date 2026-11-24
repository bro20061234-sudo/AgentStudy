package com.studyagent.repository;

import java.sql.*;
import java.time.LocalDate;
import java.util.Map;

/** Actual focused seconds, kept independently from countdown resets and lesson completion. */
public final class FocusTimeRepository {
    public record Summary(long todaySeconds,long totalSeconds){}
    private final String url;
    public FocusTimeRepository(){this(null);}
    public FocusTimeRepository(String url){
        this.url=url;
        try(var c=connection();var s=c.createStatement()){
            s.execute("CREATE TABLE IF NOT EXISTS focus_time(chat_id INTEGER NOT NULL,study_date TEXT NOT NULL,seconds INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(chat_id,study_date))");
        }catch(SQLException e){throw new IllegalStateException("Không khởi tạo được thời gian học",e);}
    }
    private Connection connection()throws SQLException{return url==null?DatabaseManager.getConnection():DriverManager.getConnection(url);}
    public void add(long chatId,Map<LocalDate,Long> seconds){
        if(seconds.isEmpty())return;
        try(var c=connection()){
            c.setAutoCommit(false);
            try(var s=c.prepareStatement("INSERT INTO focus_time(chat_id,study_date,seconds) VALUES(?,?,?) ON CONFLICT(chat_id,study_date) DO UPDATE SET seconds=focus_time.seconds+excluded.seconds")){
                for(var entry:seconds.entrySet())if(entry.getValue()>0){s.setLong(1,chatId);s.setString(2,entry.getKey().toString());s.setLong(3,entry.getValue());s.addBatch();}
                s.executeBatch();c.commit();
            }catch(SQLException e){c.rollback();throw e;}
        }catch(SQLException e){throw new IllegalStateException("Không lưu được thời gian học",e);}
    }
    public Summary summary(long chatId,LocalDate today){
        try(var c=connection();var s=c.prepareStatement("SELECT COALESCE(SUM(CASE WHEN study_date=? THEN seconds ELSE 0 END),0),COALESCE(SUM(seconds),0) FROM focus_time WHERE chat_id=?")){
            s.setString(1,today.toString());s.setLong(2,chatId);try(var r=s.executeQuery()){return r.next()?new Summary(r.getLong(1),r.getLong(2)):new Summary(0,0);}
        }catch(SQLException e){throw new IllegalStateException("Không đọc được thời gian học",e);}
    }
}
