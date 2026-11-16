@echo off
cd /d "%~dp0"
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Java\jdk-27"
chcp 65001 > nul
call build.bat
if errorlevel 1 exit /b 1
echo Dang khoi chay HTTP/1.1 Server tren cong 8080...
"%JAVA_HOME%\bin\java" -Dfile.encoding=UTF-8 -cp "out" http1.httpServer 8080
if errorlevel 1 pause
