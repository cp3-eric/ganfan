@echo off
setlocal
cd /d "%~dp0"

REM Load local private config (map API keys). Skipped when the file is absent.
if exist "%~dp0local-env.bat" (
  call "%~dp0local-env.bat"
)

where node >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Node.js not found.
  echo Install Node.js from https://nodejs.org
  pause
  exit /b 1
)

set "BACKEND_JAR=backend\target\ganfan-backend-1.0.0.jar"
set "JAVA_CMD=java"
if exist "D:\java-build-tools\jdk-17.0.21.0.2+1\bin\java.exe" (
  set "JAVA_CMD=D:\java-build-tools\jdk-17.0.21.0.2+1\bin\java.exe"
)
if exist "%BACKEND_JAR%" (
  powershell -NoProfile -Command "try{ (Invoke-WebRequest http://127.0.0.1:8080/api/health -UseBasicParsing -TimeoutSec 2) | Out-Null; exit 0 }catch{ exit 1 }" >nul 2>nul
  if errorlevel 1 (
    where java >nul 2>nul
    if errorlevel 1 (
      if not exist "D:\java-build-tools\jdk-17.0.21.0.2+1\bin\java.exe" (
        echo [WARN] Java not found. Start backend manually first:
        echo   cd backend ^&^& mvn spring-boot:run
      ) else (
        echo Starting Ganfan Spring Boot backend...
        start "Ganfan Backend" /min cmd /c "%JAVA_CMD% -jar %BACKEND_JAR% --server.port=8080 --server.address=127.0.0.1"
        timeout /t 6 /nobreak >nul
      )
    ) else (
      echo Starting Ganfan Spring Boot backend...
      start "Ganfan Backend" /min cmd /c "java -jar %BACKEND_JAR% --server.port=8080 --server.address=127.0.0.1"
      timeout /t 6 /nobreak >nul
    )
  )
) else (
  echo [WARN] Backend JAR not found. Run: cd backend ^&^& mvn -DskipTests package
  echo Frontend UI still opens, but login and persistent actions require the backend.
)

powershell -NoProfile -Command "try{ (Invoke-WebRequest http://127.0.0.1:5173/index.html -UseBasicParsing -TimeoutSec 2) | Out-Null; exit 0 }catch{ exit 1 }" >nul 2>nul
if errorlevel 1 (
  echo Starting Ganfan frontend server...
  start "Ganfan Demo Server" /min cmd /c "node server.js"
  timeout /t 2 /nobreak >nul
)

start "" http://127.0.0.1:5173
echo.
echo Frontend: http://127.0.0.1:5173
echo Backend API: http://127.0.0.1:8080/api
echo To stop: close the minimized "Ganfan Backend" and "Ganfan Demo Server" windows.
pause
