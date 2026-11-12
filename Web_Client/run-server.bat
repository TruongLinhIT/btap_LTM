@echo off
cd /d "%~dp0"
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Java\jdk-27"
chcp 65001 > nul
echo Dang khoi dong Web Server doc lap (Cong 8080)...
"%JAVA_HOME%\bin\java" -Dfile.encoding=UTF-8 -cp "out;out\production\Web_Server" http1.httpServer
if errorlevel 1 pause
