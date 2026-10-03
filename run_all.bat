@echo off
cd /d "%~dp0"
echo ==================================================
echo   microvac project - one click regression gate
echo   working dir: %cd%
echo ==================================================

echo.
echo ---------- [1/3] python model.py ----------
python model.py
if errorlevel 1 goto :fail

echo.
echo ---------- [2/3] python buffer_sweep.py ----------
python buffer_sweep.py
if errorlevel 1 goto :fail

echo.
echo ---------- [3/3] python sim_upgrade.py ----------
python sim_upgrade.py
if errorlevel 1 goto :fail

echo.
echo ================ ALL PASSED =================
echo outputs: figs\   stl\   stl\views\
pause
exit /b 0

:fail
echo.
echo [FAIL] see the error message above.
pause
exit /b 1
