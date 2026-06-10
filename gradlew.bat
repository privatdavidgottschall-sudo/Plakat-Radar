@echo off
setlocal

set APP_HOME=%~dp0
set GRADLE_VERSION=8.10.2
set BOOTSTRAP_DIR=%APP_HOME%.gradle\bootstrap
set GRADLE_DIR=%BOOTSTRAP_DIR%\gradle-%GRADLE_VERSION%
set GRADLE_BIN=%GRADLE_DIR%\bin\gradle.bat
set ZIP_PATH=%BOOTSTRAP_DIR%\gradle-%GRADLE_VERSION%-bin.zip
set GRADLE_URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip

if not exist "%GRADLE_BIN%" (
  if not exist "%BOOTSTRAP_DIR%" mkdir "%BOOTSTRAP_DIR%"
  if not exist "%ZIP_PATH%" (
    echo Downloading Gradle %GRADLE_VERSION%...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri '%GRADLE_URL%' -OutFile '%ZIP_PATH%'"
    if errorlevel 1 exit /b 1
  )
  echo Extracting Gradle %GRADLE_VERSION%...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -LiteralPath '%ZIP_PATH%' -DestinationPath '%BOOTSTRAP_DIR%' -Force"
  if errorlevel 1 exit /b 1
)

call "%GRADLE_BIN%" %*
