@echo off
setlocal
call "C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
if errorlevel 1 exit /b 1
if "%~1"=="" exit /b 2
cl /nologo /EHsc /std:c++17 /W4 /I"%~dp0." /Fe:"%~1\optical_window_test.exe" /Fo:"%~1\optical_window_test.obj" "%~dp0optical_window_test.cpp"
if errorlevel 1 exit /b 1
"%~1\optical_window_test.exe"
if %errorlevel% neq 0 exit /b %errorlevel%
cl /nologo /EHsc /std:c++17 /W4 /Fe:"%~1\dark_offsets_test.exe" /Fo:"%~1\dark_offsets_test.obj" "%~dp0dark_offsets_test.cpp"
if errorlevel 1 exit /b 1
"%~1\dark_offsets_test.exe"
if errorlevel 1 exit /b 1
python "%~dp0calibration_guard_regression.py" "%~1"
if errorlevel 1 exit /b 1
cl /nologo /EHsc /std:c++17 /W4 /Fe:"%~1\si_storage_test.exe" /Fo:"%~1\si_storage_test.obj" "%~dp0si_storage_test.cpp"
if errorlevel 1 exit /b 1
"%~1\si_storage_test.exe"
if errorlevel 1 exit /b 1
cl /nologo /EHsc /std:c++17 /W4 /Fe:"%~1\live_sample_health_test.exe" /Fo:"%~1\live_sample_health_test.obj" "%~dp0live_sample_health_test.cpp"
if errorlevel 1 exit /b 1
"%~1\live_sample_health_test.exe"
if errorlevel 1 exit /b 1
cl /nologo /EHsc /std:c++17 /W4 /Fe:"%~1\wifi_recovery_test.exe" /Fo:"%~1\wifi_recovery_test.obj" "%~dp0wifi_recovery_test.cpp"
if errorlevel 1 exit /b 1
"%~1\wifi_recovery_test.exe"
if errorlevel 1 exit /b 1
