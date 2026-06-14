@echo off
setlocal

cd /d "%~dp0"

where javaw >nul 2>nul
if errorlevel 1 (
    echo Java Runtime was not found.
    echo Please install Java 8 or later and try again.
    pause
    exit /b 1
)

set "MVN_CMD="
where mvn >nul 2>nul
if not errorlevel 1 (
    set "MVN_CMD=mvn"
) else if exist "%LOCALAPPDATA%\Temp\codex-maven\apache-maven-3.9.10\bin\mvn.cmd" (
    set "MVN_CMD=%LOCALAPPDATA%\Temp\codex-maven\apache-maven-3.9.10\bin\mvn.cmd"
) else if exist "%TEMP%\codex-maven\apache-maven-3.9.10\bin\mvn.cmd" (
    set "MVN_CMD=%TEMP%\codex-maven\apache-maven-3.9.10\bin\mvn.cmd"
)

if "%MVN_CMD%"=="" (
    echo Maven was not found.
    echo Please install Maven or run mvn package once before using this launcher.
    pause
    exit /b 1
)

echo Preparing CSV editor...
call "%MVN_CMD%" -q -DskipTests package dependency:build-classpath "-Dmdep.outputFile=target\classpath.txt"
if errorlevel 1 (
    echo Failed to build the application.
    pause
    exit /b 1
)

if not exist "target\classpath.txt" (
    echo Classpath file was not created.
    pause
    exit /b 1
)

set /p DEP_CP=<"target\classpath.txt"
set "APP_CP=target\classes;%DEP_CP%"

start "CSV Data Editor" javaw -cp "%APP_CP%" com.example.csveditor.app.CsvEditorApp
exit /b 0
