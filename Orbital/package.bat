@echo off
rem Builds Orbital for Windows (no Java needed to RUN the result).
rem
rem   package.bat [version] [installer]
rem     version    app version, digits and dots only (default 1.0.0)
rem     installer  also build dist\Orbital-Setup-VERSION.exe (needs WiX Toolset 3.x on PATH)
rem
rem Output: dist\Orbital\Orbital.exe  (portable folder)  [+ dist\Orbital-Setup-VERSION.exe]
rem Needs JDK 21 (jpackage ships with it) and Maven.
setlocal
set "VERSION=%~1"
if "%VERSION%"=="" set "VERSION=1.0.0"
rem Local convenience (JDK/Maven paths). CI sets CI=true and brings its own toolchain.
if not defined CI if exist "%~dp0tools-env.bat" call "%~dp0tools-env.bat"
cd /d "%~dp0"

set "MVNFLAGS=-B -Ppackage -DskipTests"
if not defined CI set "MVNFLAGS=%MVNFLAGS% -q"

rem Modules the trimmed runtime must contain. jpackage's own detection misses jdk.crypto.ec
rem (without it every HTTPS call to lineupsvalorant.com fails the TLS handshake) and a few others.
set "MODULES=java.base,java.desktop,java.logging,java.naming,java.net.http,java.prefs,java.sql,java.xml,jdk.unsupported,jdk.unsupported.desktop,jdk.crypto.ec,jdk.crypto.cryptoki"

echo [1/4] Building self-contained jar...
call mvn %MVNFLAGS% clean package
if errorlevel 1 goto fail

echo [2/4] Staging...
if exist target\package rmdir /s /q target\package
mkdir target\package
set "FOUND="
for %%F in (target\*-all.jar) do (
  copy /y "%%F" target\package\orbital.jar >nul
  set "FOUND=1"
)
if not defined FOUND (
  echo Could not find the shaded jar in target.
  goto fail
)

echo [3/4] Creating Orbital.exe with jpackage...
if exist dist\Orbital rmdir /s /q dist\Orbital
jpackage --type app-image --name Orbital --app-version %VERSION% ^
  --input target\package --main-jar orbital.jar --main-class com.appbrim.Launcher ^
  --icon packaging\orbital.ico --dest dist --vendor Orbital ^
  --add-modules "%MODULES%" --java-options "-Dfile.encoding=UTF-8"
if errorlevel 1 goto fail

if /i not "%~2"=="installer" goto done

echo [4/4] Creating installer...
if exist "dist\Orbital-Setup-%VERSION%.exe" del "dist\Orbital-Setup-%VERSION%.exe"
jpackage --type exe --app-image dist\Orbital --name Orbital --app-version %VERSION% ^
  --dest dist --vendor Orbital --win-menu --win-shortcut --win-per-user-install --win-dir-chooser ^
  --win-upgrade-uuid e80c4456-4cc8-44c7-a293-8d3939c5eebd
if errorlevel 1 goto fail
ren "dist\Orbital-%VERSION%.exe" "Orbital-Setup-%VERSION%.exe"
if errorlevel 1 goto fail

:done
echo.
echo Done: dist\Orbital\Orbital.exe
if exist "dist\Orbital-Setup-%VERSION%.exe" echo       dist\Orbital-Setup-%VERSION%.exe
endlocal
exit /b 0

:fail
echo.
echo Build failed - see the messages above.
endlocal
exit /b 1
