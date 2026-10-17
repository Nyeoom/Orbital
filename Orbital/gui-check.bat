@echo off
rem Launches the app in the foreground for GUI verification, then it gets killed externally.
call "%~dp0tools-env.bat"
call mvn -q -B test-compile dependency:build-classpath "-Dmdep.outputFile=target\cp.txt"
if errorlevel 1 exit /b 1
set /p CP=<target\cp.txt
java -cp "target\classes;target\test-classes;%CP%" com.appbrim.Launcher
