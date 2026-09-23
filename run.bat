@echo off
set "JAVA_HOME=C:\Users\Dell\.jdk\jdk-25.0.2"
set "PATH=%JAVA_HOME%\bin;C:\Users\Dell\.maven\maven-3.9.16\bin;%PATH%"
call mvn compile exec:java
        