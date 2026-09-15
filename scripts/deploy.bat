@echo off
setlocal EnableExtensions

cd /d "%~dp0"

title Study-Grow Auto Deploy

echo.
echo ==================================================
echo          STUDY-GROW AUTO DEPLOY
echo ==================================================
echo.
echo Deploy avtomatik boshlanmoqda...
echo.

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy.ps1" -CommitPush -Message "Auto deploy"

set "EXITCODE=%ERRORLEVEL%"

echo.
echo ==================================================

if "%EXITCODE%"=="0" (
    echo.
    echo   DEPLOY MUVAFFAQIYATLI YAKUNLANDI
    echo.
    echo   https://study-grow.uz
    echo.
) else (
    echo.
    echo   DEPLOY XATO BILAN TUGADI
    echo.
    echo   Exit code: %EXITCODE%
    echo.
)

echo ==================================================
echo.
pause

exit /b %EXITCODE%
