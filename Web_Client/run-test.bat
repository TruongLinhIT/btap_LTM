@echo off
cd /d "%~dp0"
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Java\jdk-27"
chcp 65001 > nul
call build.bat
if errorlevel 1 exit /b 1
echo Dang chay bo kiem thu tu dong Google Chrome Web Client...
"%JAVA_HOME%\bin\java" -Dfile.encoding=UTF-8 -cp "out" test.BrowserServerTest
