@echo off
setlocal

set "PROJECT_DIR=%~dp0"
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
set "GRADLE_OPTS=-Duser.home=%USERPROFILE% %GRADLE_OPTS%"

if not exist "%PROJECT_DIR%keystore.properties" (
    echo Signing configuration missing: keystore.properties
    echo Read RELEASE.md before building a public APK.
    pause
    exit /b 1
)

pushd "%PROJECT_DIR%"
call gradlew.bat :app:assembleRelease --no-daemon --no-configuration-cache

if errorlevel 1 (
    popd
    echo.
    echo APK build failed.
    pause
    exit /b 1
)

if not exist "%PROJECT_DIR%dist" mkdir "%PROJECT_DIR%dist"
copy /Y "%PROJECT_DIR%app\build\outputs\apk\release\app-release.apk" "%PROJECT_DIR%dist\Uvir-1.3.0-release.apk" >nul
powershell -NoProfile -Command "$h=(Get-FileHash -LiteralPath '%PROJECT_DIR%dist\Uvir-1.3.0-release.apk' -Algorithm SHA256).Hash; Set-Content -LiteralPath '%PROJECT_DIR%dist\Uvir-1.3.0-release.apk.sha256' -Value ($h + '  Uvir-1.3.0-release.apk') -Encoding ascii"
popd

echo.
echo Release APK created at:
echo %PROJECT_DIR%dist\Uvir-1.3.0-release.apk
echo SHA-256 checksum at:
echo %PROJECT_DIR%dist\Uvir-1.3.0-release.apk.sha256
pause
