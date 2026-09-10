<#
.SYNOPSIS
    Starts the Spring Boot backend using environment values from backend/.env.
.DESCRIPTION
    - Resolves JAVA_HOME (existing valid value, else the Adoptium JDK 21 path)
      and adds %JAVA_HOME%\bin to the process PATH.
    - Validates the Java runtime with `java -version`.
    - Reads KEY=VALUE pairs from backend/.env (ignores blank lines and '#'
      comments; splits on the first '=' so values may contain '='; never prints
      secret values).
    - Verifies required environment variables are present.
    - Starts Spring Boot with global Maven if available, otherwise the Maven
      Wrapper (mvnw.cmd).
.EXAMPLE
    .\start-backend.ps1
#>

$ErrorActionPreference = 'Stop'

# Resolve the backend directory (this script's parent) so it works from any CWD.
$BackendDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# ── 1. Java / JAVA_HOME ─────────────────────────────────────────────────────
$CandidateJdk   = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot'
$Jdk = $null

# Prefer existing JAVA_HOME if it points to a real java.exe.
if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $Jdk = $env:JAVA_HOME
} elseif (Test-Path -LiteralPath (Join-Path $CandidateJdk 'bin\java.exe')) {
    $Jdk = $CandidateJdk
} else {
    # Last resort: rely on java already being on PATH.
    if (Get-Command java -ErrorAction SilentlyContinue) {
        $Jdk = $null  # use whatever java is on PATH
    } else {
        Write-Error "Could not locate a Java 21 JDK. Install JDK 21 or set JAVA_HOME."
        exit 1
    }
}

if ($Jdk) {
    [Environment]::SetEnvironmentVariable('JAVA_HOME', $Jdk, 'Process')
    # Add %JAVA_HOME%\bin to the front of the process PATH if not already there.
    $javaBin = Join-Path $Jdk 'bin'
    $pathList = [Environment]::GetEnvironmentVariable('Path', 'Process')
    if ($pathList -notlike "*$javaBin*") {
        [Environment]::SetEnvironmentVariable('Path', "$javaBin;$pathList", 'Process')
    }
}

# Verify java is now available. java -version writes to stderr, which under
# $ErrorActionPreference='Stop' would surface as a terminating error, so run
# the check with the preference relaxed and inspect the real exit code.
$javaCmd = if ($Jdk) { Join-Path $Jdk 'bin\java.exe' } else { 'java' }
Write-Host "Using JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Cyan

$prevEAP = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$javaVer = & $javaCmd -version 2>&1
$javaExit = $LASTEXITCODE
$ErrorActionPreference = $prevEAP

$javaVer | ForEach-Object { "  $_" }
if ($javaExit -ne 0) {
    Write-Error "Java verification failed (java -version returned exit code $javaExit)."
    exit 1
}

# ── 2. Load backend/.env ────────────────────────────────────────────────────
$EnvFile = Join-Path $BackendDir '.env'
if (-not (Test-Path -LiteralPath $EnvFile)) {
    Write-Error "Missing environment file: $EnvFile"
    exit 1
}

$count = 0
Get-Content -LiteralPath $EnvFile | ForEach-Object {
    $line = $_.Trim()

    # Skip blank lines and comments.
    if ($line -eq '' -or $line.StartsWith('#')) {
        return
    }

    # Parse the first '=' only; values may contain '='.
    $eqIndex = $line.IndexOf('=')
    if ($eqIndex -lt 1) {
        Write-Warning "Skipping malformed line (no '='): $line"
        return
    }

    $key   = $line.Substring(0, $eqIndex).Trim()
    $value = $line.Substring($eqIndex + 1).Trim()

    # Strip surrounding quotes if present.
    if ($value.Length -ge 2 -and
        (($value[0] -eq '"' -and $value[-1] -eq '"') -or
         ($value[0] -eq "'" -and $value[-1] -eq "'"))) {
        $value = $value.Substring(1, $value.Length - 2)
    }

    [Environment]::SetEnvironmentVariable($key, $value, 'Process')
    $count++
}

Write-Host "Loaded $count environment variable(s) from backend/.env" -ForegroundColor Green

# ── 3. Verify required environment variables ────────────────────────────────
$required = @(
    'DATABASE_URL'
    'DATABASE_USERNAME'
    'DATABASE_PASSWORD'
    'JWT_SECRET'
    'SPRING_PROFILES_ACTIVE'
    'SPRING_JPA_HIBERNATE_DDL_AUTO'
)
foreach ($name in $required) {
    if (-not [Environment]::GetEnvironmentVariable($name, 'Process')) {
        Write-Error "Missing required environment variable: $name"
        exit 1
    }
}

# ── 4. Run from the backend directory ────────────────────────────────────────
Set-Location -LiteralPath $BackendDir

# ── 5. Maven: prefer global mvn, else the Maven wrapper ─────────────────────
if (Get-Command mvn -ErrorAction SilentlyContinue) {
    Write-Host "Starting Spring Boot with MAVEN (global)..." -ForegroundColor Cyan
    mvn spring-boot:run
} elseif (Test-Path -LiteralPath (Join-Path $BackendDir 'mvnw.cmd')) {
    Write-Host "Starting Spring Boot with MAVEN WRAPPER (mvnw.cmd)..." -ForegroundColor Cyan
    & cmd /c mvnw.cmd spring-boot:run
} else {
    Write-Error "Neither 'mvn' nor 'mvnw.cmd' is available. Install Maven or restore mvnw.cmd."
    exit 1
}
