@echo off
setlocal
set "records=%LOCALAPPDATA%\MightyBot\records"
if not exist "%records%" mkdir "%records%"
echo 1. Enable bot replays
echo 2. Disable bot replays
echo 3. Enable score CSV
echo 4. Disable score CSV
echo 5. Open records folder
choice /c 12345 /n /m "Select: "
if errorlevel 5 (explorer "%records%" & exit /b)
if errorlevel 4 (if exist "%records%\scores.enabled" del "%records%\scores.enabled") else (
 if errorlevel 3 (type nul > "%records%\scores.enabled") else (
  if errorlevel 2 (if exist "%records%\replay.enabled" del "%records%\replay.enabled") else (
   if errorlevel 1 type nul > "%records%\replay.enabled"
  )
 )
)
echo Settings updated.
pause
