@echo off
set "JAVA_HOME=C:\PROGRA~1\Java\jdk-21.0.12.1"
set "PATH=%JAVA_HOME%\bin;C:\Users\Dell\.maven\maven-3.9.16\bin;%PATH%"
call mvn %*
