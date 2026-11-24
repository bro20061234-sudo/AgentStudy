package com.studyagent;

import java.nio.file.Path;

/** Native launches resolve data beside the executable, independently of shortcut working folders. */
public final class AppPaths {
    private AppPaths(){}
    public static Path root(){return Path.of(System.getProperty("studyagent.home",System.getProperty("user.dir"))).toAbsolutePath().normalize();}
    public static Path resolve(String first,String... rest){return root().resolve(Path.of(first,rest)).normalize();}
}
