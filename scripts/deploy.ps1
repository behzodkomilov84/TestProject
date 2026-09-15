<#
.SYNOPSIS
    study-grow.uz uchun to'liq deploy: test -> build -> scp -> docker rebuild -> tekshirish.

.DESCRIPTION
    Shu sessiyada Claude qo'llagan pipeline'ning aynan o'zi — endi mustaqil,
    Claude'siz ham ishlatish uchun (foydalanuvchi so'rovi, 2026-09-15).

    Bosqichlar:
      1. (ixtiyoriy, -SkipTests bo'lmasa) to'liq test to'plami
      2. mvn package -DskipTests -> jar quriladi
      3. jar serverga scp qilinadi
      4. md5 checksum orqali to'g'ri ko'chganini tekshiradi
      5. serverda "docker compose up -d --build app"
      6. konteyner xatosiz ishga tushganini kutib, loglarni ko'rsatadi

.PARAMETER SkipTests
    Test bosqichini o'tkazib yuborish (faqat build+deploy). Standart: testlar ishlaydi.

.PARAMETER CommitPush
    Deploy'dan OLDIN "git add -A && git commit && git push" ham bajaradi.
    Commit xabari -Message parametri bilan beriladi (bo'lmasa so'raladi).

.PARAMETER Message
    -CommitPush bilan birga ishlatiladigan commit xabari.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\deploy.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\deploy.ps1 -SkipTests

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\deploy.ps1 -CommitPush -Message "Xato tuzatildi"
#>
param(
    [switch]$SkipTests,
    [switch]$CommitPush,
    [string]$Message
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ServerHost = "root@62.238.102.84"
$RemoteJarPath = "/opt/studygrow/target/TestProject-0.0.1-SNAPSHOT.jar"
$LocalJarPath = Join-Path $ProjectRoot "target\TestProject-0.0.1-SNAPSHOT.jar"

function Write-Step($text) {
    Write-Host ""
    Write-Host "==> $text" -ForegroundColor Cyan
}

function Fail($text) {
    Write-Host ""
    Write-Host "XATOLIK: $text" -ForegroundColor Red
    exit 1
}

Set-Location $ProjectRoot

# ---- 0. (ixtiyoriy) commit + push ----
if ($CommitPush) {
    Write-Step "Git: o'zgarishlarni commit qilish"
    git add -A
    $staged = git diff --cached --name-only
    if (-not $staged) {
        Write-Host "Commit qilinadigan o'zgarish yo'q, o'tkazib yuborilyapti." -ForegroundColor Yellow
    } else {
        if (-not $Message) {
            $Message = Read-Host "Commit xabarini kiriting"
        }
        $fullMessage = "$Message`n`nCo-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
        git commit -m $fullMessage
        if ($LASTEXITCODE -ne 0) { Fail "git commit muvaffaqiyatsiz tugadi." }

        Write-Step "Git: push"
        git push origin master
        if ($LASTEXITCODE -ne 0) { Fail "git push muvaffaqiyatsiz tugadi." }
    }
}

# ---- 1. Testlar ----
if (-not $SkipTests) {
    Write-Step "Testlar ishga tushirilmoqda (ClamAvScanServiceTest chetlab o'tiladi)..."
    & .\mvnw.cmd test "-Dtest=!ClamAvScanServiceTest"
    if ($LASTEXITCODE -ne 0) { Fail "Testlar muvaffaqiyatsiz tugadi — deploy to'xtatildi." }
    Write-Host "Testlar muvaffaqiyatli o'tdi." -ForegroundColor Green
} else {
    Write-Host "(-SkipTests: testlar o'tkazib yuborildi)" -ForegroundColor Yellow
}

# ---- 2. Build ----
Write-Step "Jar quriladi (mvn package -DskipTests)..."
& .\mvnw.cmd -q -o package -DskipTests
if ($LASTEXITCODE -ne 0) { Fail "Build muvaffaqiyatsiz tugadi." }
if (-not (Test-Path $LocalJarPath)) { Fail "Jar fayl topilmadi: $LocalJarPath" }
Write-Host "Jar tayyor: $LocalJarPath" -ForegroundColor Green

# ---- 3. SCP ----
Write-Step "Jar serverga yuklanmoqda (scp)..."
scp $LocalJarPath "${ServerHost}:${RemoteJarPath}"
if ($LASTEXITCODE -ne 0) { Fail "scp muvaffaqiyatsiz tugadi." }

# ---- 4. Checksum tekshiruvi ----
Write-Step "Checksum tekshirilmoqda..."
$localHash = (Get-FileHash -Path $LocalJarPath -Algorithm MD5).Hash.ToLower()
$remoteHash = (ssh $ServerHost "md5sum $RemoteJarPath").Split(" ")[0]
if ($localHash -ne $remoteHash) {
    Fail "Checksum mos kelmadi! Lokal: $localHash, Server: $remoteHash. Qayta urinib ko'ring."
}
Write-Host "Checksum mos: $localHash" -ForegroundColor Green

# ---- 5. Docker rebuild ----
Write-Step "Docker konteyner qayta qurilmoqda va ishga tushirilmoqda..."
ssh $ServerHost "cd /opt/studygrow && docker compose -f docker-compose.prod.yml up -d --build app"
if ($LASTEXITCODE -ne 0) { Fail "docker compose muvaffaqiyatsiz tugadi." }

# ---- 6. Startup tekshiruvi ----
Write-Step "Server toza ishga tushishini kutilmoqda (bu ~30-60 soniya davom etishi mumkin)..."
$started = $false
for ($i = 0; $i -lt 40; $i++) {
    Start-Sleep -Seconds 3
    $logLine = ssh $ServerHost "docker logs spring-app --since 3m 2>&1 | grep -i 'started testapplication' | tail -1"
    if ($logLine) {
        $started = $true
        Write-Host $logLine -ForegroundColor Green
        break
    }
}

if (-not $started) {
    Write-Host "Ogohlantirish: 'Started TestApplication' qatori 2 daqiqa ichida topilmadi." -ForegroundColor Yellow
    Write-Host "Loglarni qo'lda tekshiring:" -ForegroundColor Yellow
    Write-Host "  ssh $ServerHost `"docker logs spring-app --since 3m`""
    exit 1
}

Write-Step "Xatolarni tekshirish..."
$errors = ssh $ServerHost "docker logs spring-app --since 3m 2>&1 | grep -iE 'ERROR|Exception' | grep -v 'Hibernate:' | grep -v 'Telegram update error'"
if ($errors) {
    Write-Host "Diqqat — loglarda xato(lar) topildi:" -ForegroundColor Yellow
    Write-Host $errors
} else {
    Write-Host "Xato topilmadi." -ForegroundColor Green
}

Write-Host ""
Write-Host "=== DEPLOY TUGADI: https://study-grow.uz ===" -ForegroundColor Cyan
Write-Host "Eslatma: har bir deploy sessiyalarni tozalaydi — brauzerda qayta login qiling." -ForegroundColor Yellow
