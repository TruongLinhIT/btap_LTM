@echo off
cd /d "%~dp0"
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Java\jdk-27"
start "" "%JAVA_HOME%\bin\javaw" -Dfile.encoding=UTF-8 -cp "out;lib\*" client.ClientApp
