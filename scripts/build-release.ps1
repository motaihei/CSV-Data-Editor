param(
    [string]$Version = "1.0.0",
    [switch]$SkipTests,
    [switch]$SkipSmoke
)

$ErrorActionPreference = "Stop"

$AppName = "CSV-Data-Editor"
$RepoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$DistDir = Join-Path $RepoRoot "dist"
$TargetDir = Join-Path $RepoRoot "target"
$JarName = "$AppName-$Version.jar"
$ZipName = "$AppName-$Version-win-x64.zip"
$AppImageInputDir = Join-Path $TargetDir "jpackage-input"
$AppImageOutputDir = Join-Path $TargetDir "jpackage-output"
$PortableDir = Join-Path $DistDir $AppName
$JarOutputPath = Join-Path $DistDir $JarName
$ZipOutputPath = Join-Path $DistDir $ZipName
$JarBackupDir = Join-Path $DistDir ".backup"
$PortableBackupDir = Join-Path $PortableDir ".backup"

function Resolve-CommandPath {
    param(
        [string]$CommandName,
        [string[]]$FallbackPaths = @()
    )

    $command = Get-Command $CommandName -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    foreach ($fallback in $FallbackPaths) {
        $expanded = [Environment]::ExpandEnvironmentVariables($fallback)
        if (Test-Path -LiteralPath $expanded) {
            return $expanded
        }
    }

    throw "$CommandName was not found."
}

function Remove-DirectoryIfInsideRepo {
    param([string]$Path)

    $fullPath = [System.IO.Path]::GetFullPath($Path)
    if (-not $fullPath.StartsWith($RepoRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to delete outside repository: $fullPath"
    }
    if (Test-Path -LiteralPath $fullPath) {
        Remove-Item -LiteralPath $fullPath -Recurse -Force
    }
}

function Initialize-BackupDirectory {
    param([string]$Path)

    New-Item -ItemType Directory -Path $Path | Out-Null
    Set-Content -LiteralPath (Join-Path $Path "README.txt") `
        -Value "CSV backup files are stored in this directory." `
        -Encoding ASCII
}

Push-Location $RepoRoot
try {
    $maven = Resolve-CommandPath "mvn" @(
        "%LOCALAPPDATA%\Temp\codex-maven\apache-maven-3.9.10\bin\mvn.cmd",
        "%TEMP%\codex-maven\apache-maven-3.9.10\bin\mvn.cmd"
    )
    $java = Resolve-CommandPath "java"
    $jpackage = Resolve-CommandPath "jpackage"

    Write-Host "Using Maven: $maven"
    Write-Host "Using Java: $java"
    Write-Host "Using jpackage: $jpackage"

    Remove-DirectoryIfInsideRepo $DistDir
    New-Item -ItemType Directory -Path $DistDir | Out-Null

    $mavenArgs = @("clean", "-Drelease.version=$Version")
    if (-not $SkipTests) {
        $mavenArgs += "test"
    }
    $mavenArgs += "package"
    & $maven @mavenArgs

    $builtJar = Join-Path $TargetDir $JarName
    if (-not (Test-Path -LiteralPath $builtJar)) {
        throw "Executable jar was not created: $builtJar"
    }
    Copy-Item -LiteralPath $builtJar -Destination $JarOutputPath
    Initialize-BackupDirectory $JarBackupDir

    Remove-DirectoryIfInsideRepo $AppImageInputDir
    Remove-DirectoryIfInsideRepo $AppImageOutputDir
    New-Item -ItemType Directory -Path $AppImageInputDir | Out-Null
    New-Item -ItemType Directory -Path $AppImageOutputDir | Out-Null
    Copy-Item -LiteralPath $JarOutputPath -Destination (Join-Path $AppImageInputDir $JarName)

    & $jpackage `
        --type app-image `
        --name $AppName `
        --app-version $Version `
        --vendor CSV-Data-Editor `
        --input $AppImageInputDir `
        --main-jar $JarName `
        --dest $AppImageOutputDir

    $generatedAppImage = Join-Path $AppImageOutputDir $AppName
    if (-not (Test-Path -LiteralPath $generatedAppImage)) {
        throw "Portable app image was not created: $generatedAppImage"
    }

    Copy-Item -LiteralPath $generatedAppImage -Destination $PortableDir -Recurse
    $ConfigDir = Join-Path $RepoRoot "config"
    if (Test-Path -LiteralPath $ConfigDir) {
        Copy-Item -LiteralPath $ConfigDir -Destination (Join-Path $PortableDir "config") -Recurse
    }
    Copy-Item -LiteralPath (Join-Path $RepoRoot "README.md") -Destination (Join-Path $PortableDir "README.md")
    Initialize-BackupDirectory $PortableBackupDir

    if (Test-Path -LiteralPath $ZipOutputPath) {
        Remove-Item -LiteralPath $ZipOutputPath -Force
    }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [System.IO.Compression.ZipFile]::CreateFromDirectory(
        $PortableDir,
        $ZipOutputPath,
        [System.IO.Compression.CompressionLevel]::Optimal,
        $true
    )

    if (-not $SkipSmoke) {
        Write-Host "Smoke check: executable JAR"
        $jarProcess = Start-Process -FilePath $java -ArgumentList @("-jar", $JarOutputPath) -WindowStyle Hidden -PassThru
        Start-Sleep -Seconds 3
        if ($jarProcess.HasExited) {
            throw "Executable jar exited early. ExitCode=$($jarProcess.ExitCode)"
        }
        Stop-Process -Id $jarProcess.Id -Force

        Write-Host "Smoke check: portable exe"
        $portableExe = Join-Path $PortableDir "$AppName.exe"
        $exeProcess = Start-Process -FilePath $portableExe -WorkingDirectory $PortableDir -WindowStyle Hidden -PassThru
        Start-Sleep -Seconds 3
        if ($exeProcess.HasExited) {
            throw "Portable exe exited early. ExitCode=$($exeProcess.ExitCode)"
        }
        Stop-Process -Id $exeProcess.Id -Force
    }

    Write-Host "Release artifacts created:"
    Get-Item -LiteralPath $JarOutputPath, $ZipOutputPath | Select-Object FullName, Length
} finally {
    Pop-Location
}
