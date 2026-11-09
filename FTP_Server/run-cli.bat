@echo off
cd /d "%~dp0"
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Java\jdk-27"
chcp 65001 > nul
echo Dang khoi chay FTP Server (Che do dong lenh CLI)...
"%JAVA_HOME%\bin\java" -Dfile.encoding=UTF-8 -cp "out;lib\*" Main --cli
if errorlevel 1 pause
