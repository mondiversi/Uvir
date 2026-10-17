@echo off
setlocal
call "C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
if errorlevel 1 exit /b 1
if "%~1"=="" exit /b 2
cl /nologo /EHsc /std:c++17 /W4 /I"%~dp0." /Fe:"%~1\visible_auto_gain_test.exe" /Fo:"%~1\visible_auto_gain_test.obj" "%~dp0visible_auto_gain_test.cpp"
if errorlevel 1 exit /b 1
"%~1\visible_auto_gain_test.exe"
