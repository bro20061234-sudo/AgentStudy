import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Consistent personal profile snapshot; only the packaged database gets portable document paths. */
public final class ProfileSnapshot {
    public static void main(String[] args)throws Exception{
        Path source=Path.of(args[0]).toAbsolutePath().normalize(),target=Path.of(args[1]).toAbsolutePath().normalize();
        Files.createDirectories(target.resolve("data/textbooks"));
        Path db=source.resolve("study_agent.db"),copy=target.resolve("study_agent.db");
        int documents=0;
        if(Files.isRegularFile(db)){
            try(var c=DriverManager.getConnection("jdbc:sqlite:"+db);var s=c.prepareStatement("VACUUM INTO ?")){s.setString(1,copy.toString());s.execute();}
            try(var c=DriverManager.getConnection("jdbc:sqlite:"+copy)){
                c.setAutoCommit(false);
                Map<String,String> replacements=new LinkedHashMap<>();
                try(var s=c.createStatement();var rows=s.executeQuery("SELECT id,original_path FROM textbooks WHERE original_path IS NOT NULL")){
                    while(rows.next()){
                        Path original=Path.of(rows.getString(2));if(!original.isAbsolute())original=source.resolve(original).normalize();
                        if(!Files.isRegularFile(original))original=source.resolve("data/textbooks").resolve(original.getFileName());
                        if(Files.isRegularFile(original)){
                            String relative="data/textbooks/"+original.getFileName();
                            Files.copy(original,target.resolve(relative),StandardCopyOption.REPLACE_EXISTING);replacements.put(rows.getString(1),relative);documents++;
                        }
                    }
                }
                try(var s=c.prepareStatement("UPDATE textbooks SET original_path=? WHERE id=?")){for(var entry:replacements.entrySet()){s.setString(1,entry.getValue());s.setString(2,entry.getKey());s.addBatch();}s.executeBatch();}
                c.commit();
                try(var s=c.createStatement();var r=s.executeQuery("PRAGMA integrity_check")){if(!r.next()||!"ok".equalsIgnoreCase(r.getString(1)))throw new IllegalStateException("Database snapshot integrity failed");}
            }
        }
        for(String relative:List.of("config.properties","data/appearance.properties","data/subject-colors.properties")){
            Path file=source.resolve(relative);if(Files.isRegularFile(file)){Files.createDirectories(target.resolve(relative).getParent());Files.copy(file,target.resolve(relative));}
        }
        System.out.println("PROFILE_SNAPSHOT_OK documents="+documents);
    }
}
