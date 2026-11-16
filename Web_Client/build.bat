@echo off
cd /d "%~dp0"
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Java\jdk-27"
if not exist out mkdir out
del /s /q out\*.class >nul 2>&1
dir /s /b src\*.java > sources.txt
echo Dang bien dich Google Chrome Web Client (HTTP/1.1)...
"%JAVA_HOME%\bin\javac" -encoding UTF-8 -d out @sources.txt
set BUILD_ERR=%ERRORLEVEL%
if exist sources.txt del sources.txt
if %BUILD_ERR% NEQ 0 (
    echo =================================
    echo BIEN DICH THAT BAI! - Ma loi: %BUILD_ERR%
    echo =================================
    exit /b %BUILD_ERR%
) else (
    echo =================================
    echo BIEN DICH THANH CONG - BUILD OK
    echo =================================
)
