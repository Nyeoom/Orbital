@echo off
rem Runs the live-API smoke test.
call "%~dp0tools-env.bat"
call mvn -q -B test-compile dependency:build-classpath "-Dmdep.outputFile=target\cp.txt"
if errorlevel 1 exit /b 1
set /p CP=<target\cp.txt
java -cp "target\classes;target\test-classes;%CP%" com.appbrim.SmokeTest
