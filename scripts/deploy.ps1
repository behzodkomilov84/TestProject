<#
.SYNOPSIS
    study-grow.uz uchun to'liq avtomatik deploy.

.DESCRIPTION
    Pipeline:
      1. Git add / commit / push
      2. Testlar
      3. Maven build
      4. SCP orqali JAR upload
      5. MD5 checksum
      6. Docker rebuild
      7. Spring Boot startup check
      8. Error check

    Windows PowerShell 5.1 va PowerShell 7 bilan mos.

.PARAMETER SkipTests
    Testlarni o'tkazib yuboradi.

.PARAMETER CommitPush
    Git commit va push qiladi.

.PARAMETER Message
    Commit message.
#>

param(
[switch]$SkipTests,
[switch]$CommitPush,
[string]$Message
)

$ErrorActionPreference = "Stop"

# ============================================================
# CONFIG
# ============================================================

$ProjectRoot = Split-Path -Parent $PSScriptRoot

$ServerHost = "root@62.238.102.84"

$RemoteJarPath = "/opt/studygrow/target/TestProject-0.0.1-SNAPSHOT.jar"

$LocalJarPath = Join-Path `
    $ProjectRoot `
    "target\TestProject-0.0.1-SNAPSHOT.jar"


# ============================================================
# FUNCTIONS
# ============================================================

function Write-Step {
    param(
        [string]$Text
    )

    Write-Host ""
    Write-Host "==================================================" `
        -ForegroundColor DarkGray

    Write-Host "==> $Text" `
        -ForegroundColor Cyan

    Write-Host "==================================================" `
        -ForegroundColor DarkGray
}


function Fail {
    param(
        [string]$Text
    )

    Write-Host ""
    Write-Host "XATOLIK: $Text" `
        -ForegroundColor Red

    exit 1
}


function Check-ExitCode {
    param(
        [string]$Operation
    )

    if ($LASTEXITCODE -ne 0) {
        Fail "$Operation muvaffaqiyatsiz tugadi. Exit code: $LASTEXITCODE"
    }
}


# ============================================================
# PROJECT ROOT
# ============================================================

Set-Location $ProjectRoot

Write-Host ""
Write-Host "Project: $ProjectRoot" -ForegroundColor Gray
Write-Host "Server : $ServerHost" -ForegroundColor Gray
Write-Host ""


# ============================================================
# 0. GIT COMMIT + PUSH
# ============================================================

if ($CommitPush) {

    Write-Step "Git: o'zgarishlarni tekshirish"

    git status --short

    Check-ExitCode "git status"


    Write-Step "Git: git add -A"

    git add -A

    Check-ExitCode "git add"


    $staged = git diff --cached --name-only


    if (-not $staged) {

        Write-Host ""
        Write-Host "Commit qilinadigan o'zgarish yo'q." `
            -ForegroundColor Yellow

        Write-Host "Git push o'tkazib yuboriladi." `
            -ForegroundColor Yellow

    }
    else {

        Write-Host ""
        Write-Host "Commit qilinadigan fayllar:" `
            -ForegroundColor Green

        Write-Host $staged


        if (-not $Message) {

            $Message = Read-Host "Commit xabarini kiriting"

        }


        if (-not $Message) {

            $Message = "Auto deploy"

        }


        Write-Step "Git: commit"

        git commit -m $Message

        Check-ExitCode "git commit"


        Write-Step "Git: push"

        git push origin master

        Check-ExitCode "git push"

    }

}
else {

    Write-Host ""
    Write-Host "Git commit/push o'tkazib yuborildi." `
        -ForegroundColor Yellow

}


# ============================================================
# 1. TESTS
# ============================================================

if (-not $SkipTests) {

    Write-Step "Testlar ishga tushirilmoqda"

    Write-Host ""
    Write-Host "ClamAvScanServiceTest chetlab o'tiladi." `
        -ForegroundColor Yellow

    Write-Host ""

    & .\mvnw.cmd test "-Dtest=!ClamAvScanServiceTest"

    Check-ExitCode "Testlar"

    Write-Host ""
    Write-Host "Testlar muvaffaqiyatli o'tdi." `
        -ForegroundColor Green

}
else {

    Write-Host ""
    Write-Host "(-SkipTests) Testlar o'tkazib yuborildi." `
        -ForegroundColor Yellow

}


# ============================================================
# 2. MAVEN BUILD
# ============================================================

Write-Step "Maven: JAR qurilmoqda"

& .\mvnw.cmd -q -o package -DskipTests

Check-ExitCode "Maven build"


if (-not (Test-Path $LocalJarPath)) {

    Fail "JAR fayl topilmadi: $LocalJarPath"

}


$jarInfo = Get-Item $LocalJarPath

Write-Host ""
Write-Host "JAR tayyor." -ForegroundColor Green
Write-Host "Path : $LocalJarPath"
Write-Host "Size : $([math]::Round($jarInfo.Length / 1MB, 2)) MB"


# ============================================================
# 3. SCP UPLOAD
# ============================================================

Write-Step "SCP: JAR serverga yuklanmoqda"

scp $LocalJarPath "${ServerHost}:${RemoteJarPath}"

Check-ExitCode "SCP upload"

Write-Host ""
Write-Host "SCP muvaffaqiyatli tugadi." `
    -ForegroundColor Green


# ============================================================
# 4. MD5 CHECKSUM
# ============================================================

Write-Step "MD5 checksum tekshirilmoqda"


$localHash = (
Get-FileHash `
        -Path $LocalJarPath `
        -Algorithm MD5
).Hash.ToLower()


$remoteHashOutput = ssh $ServerHost `
    "md5sum $RemoteJarPath"


Check-ExitCode "Remote md5sum"


$remoteHash = (
$remoteHashOutput `
        -split "\s+"
)[0].ToLower()


Write-Host ""
Write-Host "Local : $localHash"
Write-Host "Remote: $remoteHash"


if ($localHash -ne $remoteHash) {

    Fail @"
Checksum mos kelmadi!

Lokal : $localHash
Server: $remoteHash

Deploy to'xtatildi.
"@

}


Write-Host ""
Write-Host "Checksum MOS." `
    -ForegroundColor Green


# ============================================================
# 5. DOCKER REBUILD
# ============================================================

Write-Step "Docker: app rebuild va restart"


$dockerCommand = `
    "cd /opt/studygrow && " +
        "docker compose -f docker-compose.prod.yml " +
        "up -d --build app"


ssh $ServerHost $dockerCommand

Check-ExitCode "Docker compose up"


Write-Host ""
Write-Host "Docker app muvaffaqiyatli qayta ishga tushirildi." `
    -ForegroundColor Green


# ============================================================
# 6. STARTUP CHECK
# ============================================================

Write-Step "Spring Boot startup kutilmoqda"

Write-Host ""
Write-Host "Bu taxminan 30-120 soniya davom etishi mumkin..." `
    -ForegroundColor Yellow


$started = $false


for ($i = 0; $i -lt 40; $i++) {

    Start-Sleep -Seconds 3


    $logLine = ssh $ServerHost `
        "docker logs spring-app --since 3m 2>&1 | grep -i 'started testapplication' | tail -1"


    if ($LASTEXITCODE -eq 0 -and $logLine) {

        $started = $true

        Write-Host ""
        Write-Host "Spring Boot STARTED:" `
            -ForegroundColor Green

        Write-Host $logLine `
            -ForegroundColor Green

        break

    }


    Write-Host "." -NoNewline

}


Write-Host ""


if (-not $started) {

    Write-Host ""
    Write-Host "OGOHLANTIRISH!" `
        -ForegroundColor Yellow

    Write-Host "'Started TestApplication' topilmadi." `
        -ForegroundColor Yellow

    Write-Host ""
    Write-Host "Server loglarini tekshiring:" `
        -ForegroundColor Yellow

    Write-Host ""
    Write-Host "ssh $ServerHost `"docker logs spring-app --since 5m`"" `
        -ForegroundColor Gray

    exit 1

}


# ============================================================
# 7. ERROR CHECK
# ============================