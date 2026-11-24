@echo off
cd /d "%~dp0"
if not exist "target\ui-preview" mkdir "target\ui-preview"
javac -encoding UTF-8 -d "target\ui-preview" "src\main\java\com\studyagent\ui\preview\StudyUiPreview.java"
if errorlevel 1 exit /b 1
java -cp "target\ui-preview" com.studyagent.ui.preview.StudyUiPreview
