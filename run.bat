@echo off
cd /d "%~dp0"
set "PATH=C:\Users\Dell\.maven\maven-3.9.16\bin;%PATH%"
call mvn compile exec:java
        