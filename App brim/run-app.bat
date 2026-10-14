 @echo off
rem Builds (if needed) and launches App Brim.
call "%~dp0tools-env.bat"
call mvn -q -B compile dependency:build-classpath "-Dmdep.outputFile=target\cp.txt"
if errorlevel 1 exit /b 1
set /p CP=<target\cp.txt
start "" javaw -cp "target\classes;%CP%" com.appbrim.Launcher
