@echo off
setlocal

rem ── Build GanjaGate NeoForge mod ───────────────────────────────────
rem Uses JDK 21 from the launcher runtime

set JAVA_HOME=S:\Games\LegacyLauncher_portable\jre\java-runtime-delta\windows-x64\java-runtime-delta

echo [GanjaGate] Building with JDK 21: %JAVA_HOME%
call .\gradlew.bat jar --no-daemon

if %ERRORLEVEL% neq 0 (
    echo [GanjaGate] BUILD FAILED!
    pause
    exit /b 1
)

echo.
echo [GanjaGate] Build successful!
echo [GanjaGate] JAR location: build\libs\ganja-gate-*.jar
echo.

rem ── Copy to MC server and client mods folders ──────────────────
set MC_MODS=s:\Games\LegacyLauncher_portable\game\home\neoforge-21.1.247\mods
del /Q "%MC_MODS%\ganja-gate-*.jar" 2>nul
copy /Y build\libs\ganja-gate-*.jar "%MC_MODS%\"
echo [GanjaGate] Deployed to %MC_MODS%

set CLIENT_MODS=s:\Games\GanjaCraft Launcher\game\mods
if exist "%CLIENT_MODS%" (
    del /Q "%CLIENT_MODS%\ganja-gate-*.jar" 2>nul
    copy /Y build\libs\ganja-gate-*.jar "%CLIENT_MODS%\"
    echo [GanjaGate] Deployed to %CLIENT_MODS%
)

pause
