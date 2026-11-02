@echo off
title TalkCare Desktop Application
echo ===================================================
echo   TalkCare - Mental Health ^& Justice Platform
echo   Frontend: JavaFX WebView (HTML5/CSS3)
echo   Backend:  PostgreSQL
echo ===================================================

cd /d "%~dp0"

:: Prefer an installed JDK 25 so Maven and JavaFX use the same runtime.
set "CONFIGURED_JAVA_HOME=%JAVA_HOME%"
set "JAVA_HOME="
for /d %%D in ("C:\Program Files\Java\jdk-25*") do (
    if exist "%%~fD\bin\java.exe" set "JAVA_HOME=%%~fD"
)
for /d %%D in ("%USERPROFILE%\.jdk\jdk-25*") do (
    if not defined JAVA_HOME if exist "%%~fD\bin\java.exe" set "JAVA_HOME=%%~fD"
)
if not defined JAVA_HOME if defined CONFIGURED_JAVA_HOME if exist "%CONFIGURED_JAVA_HOME%\bin\java.exe" set "JAVA_HOME=%CONFIGURED_JAVA_HOME%"
if not defined JAVA_HOME (
    echo Java 25 is required. Install JDK 25 or set JAVA_HOME to a JDK 25 installation.
    pause
    exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"
set "JAVA_VERSION="
for /f "tokens=3" %%V in ('"%JAVA_HOME%\bin\java.exe" -version 2^>^&1') do if not defined JAVA_VERSION set "JAVA_VERSION=%%~V"
if not "%JAVA_VERSION:~0,2%"=="25" (
    echo Java 25 is required. Detected Java version: %JAVA_VERSION%
    pause
    exit /b 1
)

where mvn >nul 2>nul
if errorlevel 1 (
    echo Maven was not found in PATH. Install Maven and try again.
    pause
    exit /b 1
)

echo Launching TalkCare with Java %JAVA_VERSION%...
call mvn javafx:run
if errorlevel 1 (
    echo.
    echo Launch failed. Review the Maven output above for details.
    pause
    exit /b 1
)
pause
