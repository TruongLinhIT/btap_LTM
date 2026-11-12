@echo off
cd /d "%~dp0"
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Java\jdk-27"
chcp 65001 > nul
echo Dang khoi chay LTM Web Browser (Trinh duyet Web Client)...
"%JAVA_HOME%\bin\java" -Dfile.encoding=UTF-8 -cp "out;out\production\Web_Server" browser.WebBrowser
if errorlevel 1 pause
