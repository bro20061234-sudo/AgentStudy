package com.studyagent;

import com.studyagent.repository.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class AppPathsTest {
    @TempDir Path temp;
    @Test void nativeDataRootDoesNotDependOnLaunchWorkingDirectory()throws Exception{
        String previous=System.getProperty("studyagent.home");
        try{
            System.setProperty("studyagent.home",temp.toString());
            assertEquals(temp.resolve("config.properties"),AppPaths.resolve("config.properties"));
            try(var c=DatabaseManager.getConnection();var s=c.createStatement()){s.execute("CREATE TABLE native_check(id INTEGER)");}
            assertTrue(Files.isRegularFile(temp.resolve("study_agent.db")));
            assertEquals(temp.resolve("data/textbooks"),AppPaths.resolve("data","textbooks"));
        }finally{if(previous==null)System.clearProperty("studyagent.home");else System.setProperty("studyagent.home",previous);}
    }
}
