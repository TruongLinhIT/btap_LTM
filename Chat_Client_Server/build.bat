@echo off
cd /d "%~dp0"
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Java\jdk-27"
if exist out rmdir /s /q out
dir /s /b src\*.java > sources.txt
"%JAVA_HOME%\bin\javac" -encoding UTF-8 -cp "lib\*" -d out @sources.txt
del sources.txt
if errorlevel 1 (echo BUILD FAILED) else (echo BUILD OK)
