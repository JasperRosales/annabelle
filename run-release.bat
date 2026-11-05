@echo off
title TalkCare Desktop Application
cd /d "%~dp0"

:: Launcher for the self-contained jar attached to GitHub releases.
:: Put this file next to talkcare-desktop-<version>.jar and double-click it.

set "APP_JAR="
for %%F in ("talkcare-desktop-*.jar") do set "APP_JAR=%%~fF"
if not defined APP_JAR (
    echo talkcare-desktop-*.jar was not found in this folder.
    echo Download the jar from the GitHub release and keep it next to this file.
    pause
    exit /b 1
)

where java >nul 2>nul
if errorlevel 1 (
    echo Java 25 or newer is required. Install a JDK and make sure java is on PATH.
    pause
    exit /b 1
)

echo Launching %APP_JAR%...
java --enable-native-access=ALL-UNNAMED -jar "%APP_JAR%"
if errorlevel 1 (
    echo.
    echo Launch failed. Review the Java output above for details.
    pause
    exit /b 1
)
