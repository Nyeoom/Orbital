@echo off
rem Builds a double-clickable Orbital (no Maven/Java needed to RUN it) into dist\Orbital\Orbital.exe
rem Needs: JDK 21 (jpackage ships with it) and Maven. No WiX required: this makes an "app image" folder.
setlocal
if exist "%~dp0tools-env.bat" call "%~dp0tools-env.bat"
cd /d "%~dp0"

echo [1/3] Building self-contained jar...
call mvn -q -B -Ppackage -DskipTests clean package
if errorlevel 1 (echo Build failed. & exit /b 1)

echo [2/3] Staging...
if exist target\package rmdir /s /q target\package
mkdir target\package
for %%F in (target\*-all.jar) do copy /y "%%F" target\package\orbital.jar >nul
if not exist target\package\orbital.jar (echo Could not find the shaded jar in target. & exit /b 1)

echo [3/3] Creating Orbital.exe with jpackage...
if exist dist\Orbital rmdir /s /q dist\Orbital
jpackage --type app-image --name Orbital --app-version 1.0.0 ^
  --input target\package --main-jar orbital.jar --main-class com.appbrim.Launcher ^
  --icon packaging\orbital.ico --dest dist ^
  --java-options "-Dfile.encoding=UTF-8" --vendor Orbital
if errorlevel 1 (echo jpackage failed. & exit /b 1)

echo.
echo Done: dist\Orbital\Orbital.exe   (zip the dist\Orbital folder to share it)
endlocal
