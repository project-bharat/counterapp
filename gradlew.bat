@echo off
setlocal
set "VERSION=8.7"
set "DIST=%USERPROFILE%\.gradle\counterapp-wrapper\gradle-%VERSION%"
if not exist "%DIST%\bin\gradle.bat" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$root=Join-Path $env:USERPROFILE '.gradle\counterapp-wrapper'; New-Item -ItemType Directory -Force $root | Out-Null; $zip=Join-Path $root 'gradle-8.7-bin.zip'; if (!(Test-Path $zip)) { Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-8.7-bin.zip' -OutFile $zip }; Expand-Archive -Path $zip -DestinationPath $root -Force"
)
call "%DIST%\bin\gradle.bat" %*
