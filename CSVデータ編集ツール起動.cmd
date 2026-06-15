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

where java >nul 2>nul
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

echo Closing existing CSV editor if it is running...
java -cp "%APP_CP%" com.example.csveditor.app.CsvEditorApp --shutdown-existing
set "CLIENT_CLOSE_RESULT=%ERRORLEVEL%"
set "TEMP_PS1=%TEMP%\csv-editor-close-%RANDOM%-%RANDOM%.ps1"
> "%TEMP_PS1%" echo $ErrorActionPreference = 'Stop'
>>"%TEMP_PS1%" echo $mainClass = 'com.example.csveditor.app.CsvEditorApp'
>>"%TEMP_PS1%" echo $filter = 'Name = ''javaw.exe'' OR Name = ''java.exe'''
>>"%TEMP_PS1%" echo Add-Type @"
>>"%TEMP_PS1%" echo using System;
>>"%TEMP_PS1%" echo using System.Runtime.InteropServices;
>>"%TEMP_PS1%" echo public static class CsvEditorWindowClose {
>>"%TEMP_PS1%" echo     public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);
>>"%TEMP_PS1%" echo     [DllImport("user32.dll")]
>>"%TEMP_PS1%" echo     public static extern bool EnumWindows(EnumWindowsProc lpEnumFunc, IntPtr lParam);
>>"%TEMP_PS1%" echo     [DllImport("user32.dll")]
>>"%TEMP_PS1%" echo     public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint processId);
>>"%TEMP_PS1%" echo     [DllImport("user32.dll")]
>>"%TEMP_PS1%" echo     public static extern bool IsWindowVisible(IntPtr hWnd);
>>"%TEMP_PS1%" echo     [DllImport("user32.dll", SetLastError = true)]
>>"%TEMP_PS1%" echo     public static extern bool PostMessage(IntPtr hWnd, uint Msg, IntPtr wParam, IntPtr lParam);
>>"%TEMP_PS1%" echo }
>>"%TEMP_PS1%" echo "@
>>"%TEMP_PS1%" echo function Send-WmCloseToProcess([int]$processId) {
>>"%TEMP_PS1%" echo     [CsvEditorWindowClose]::EnumWindows({
>>"%TEMP_PS1%" echo         param([IntPtr]$hWnd, [IntPtr]$lParam)
>>"%TEMP_PS1%" echo         [uint32]$windowProcessId = 0
>>"%TEMP_PS1%" echo         [void][CsvEditorWindowClose]::GetWindowThreadProcessId($hWnd, [ref]$windowProcessId)
>>"%TEMP_PS1%" echo         if ($windowProcessId -eq [uint32]$processId -and [CsvEditorWindowClose]::IsWindowVisible($hWnd)) {
>>"%TEMP_PS1%" echo             [void][CsvEditorWindowClose]::PostMessage($hWnd, 0x0010, [IntPtr]::Zero, [IntPtr]::Zero)
>>"%TEMP_PS1%" echo         }
>>"%TEMP_PS1%" echo         return $true
>>"%TEMP_PS1%" echo     }, [IntPtr]::Zero) ^| Out-Null
>>"%TEMP_PS1%" echo }
>>"%TEMP_PS1%" echo $processes = @(Get-CimInstance Win32_Process -Filter $filter ^| Where-Object { $_.CommandLine -like ('*' + $mainClass + '*') })
>>"%TEMP_PS1%" echo if ($processes.Count -eq 0) { exit 0 }
>>"%TEMP_PS1%" echo if ($env:CLIENT_CLOSE_RESULT -ne '0') {
>>"%TEMP_PS1%" echo     foreach ($process in $processes) {
>>"%TEMP_PS1%" echo         try {
>>"%TEMP_PS1%" echo             $appProcess = [Diagnostics.Process]::GetProcessById([int]$process.ProcessId)
>>"%TEMP_PS1%" echo             $appProcess.Refresh()
>>"%TEMP_PS1%" echo             if (-not $appProcess.HasExited) {
>>"%TEMP_PS1%" echo                 if ($appProcess.MainWindowHandle -eq 0) {
>>"%TEMP_PS1%" echo                     Start-Sleep -Milliseconds 300
>>"%TEMP_PS1%" echo                     $appProcess.Refresh()
>>"%TEMP_PS1%" echo                 }
>>"%TEMP_PS1%" echo                 [void]$appProcess.CloseMainWindow()
>>"%TEMP_PS1%" echo                 Send-WmCloseToProcess ([int]$process.ProcessId)
>>"%TEMP_PS1%" echo             }
>>"%TEMP_PS1%" echo         } catch { }
>>"%TEMP_PS1%" echo     }
>>"%TEMP_PS1%" echo }
>>"%TEMP_PS1%" echo $processIds = @($processes ^| ForEach-Object { [int]$_.ProcessId })
>>"%TEMP_PS1%" echo $deadline = (Get-Date).AddSeconds(20)
>>"%TEMP_PS1%" echo do {
>>"%TEMP_PS1%" echo     Start-Sleep -Milliseconds 500
>>"%TEMP_PS1%" echo     $remaining = @(Get-Process -Id $processIds -ErrorAction SilentlyContinue ^| Where-Object { -not $_.HasExited })
>>"%TEMP_PS1%" echo } while ($remaining.Count -gt 0 -and (Get-Date) -lt $deadline)
>>"%TEMP_PS1%" echo if ($remaining.Count -gt 0) { exit 2 }
>>"%TEMP_PS1%" echo exit 0

powershell -NoProfile -ExecutionPolicy Bypass -File "%TEMP_PS1%"
set "CLOSE_RESULT=%ERRORLEVEL%"
del "%TEMP_PS1%" >nul 2>nul
if not "%CLOSE_RESULT%"=="0" (
    powershell -NoProfile -ExecutionPolicy Bypass -Command "$message = -join ([char[]](26082,23384,12450,12503,12522,12434,38281,12376,12425,12428,12414,12379,12435,12391,12375,12383,12290)); Write-Host $message"
    pause
    exit /b 1
)

start "CSV Data Editor" javaw -cp "%APP_CP%" com.example.csveditor.app.CsvEditorApp
exit /b 0
