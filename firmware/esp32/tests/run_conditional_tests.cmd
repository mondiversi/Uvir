@echo off
call "C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
if errorlevel 1 exit /b 1
cl /nologo /EHsc /std:c++17 /W4 /Fe:.arduino-build-0573-conditional\conditional_test.exe /Fo:.arduino-build-0573-conditional\conditional_test.obj firmware\esp32\tests\conditional_acquisition_test.cpp
if errorlevel 1 exit /b 1
.arduino-build-0573-conditional\conditional_test.exe
if errorlevel 1 exit /b 1
cl /nologo /EHsc /std:c++17 /W4 /Fe:.arduino-build-0573-conditional\conditional_runtime_test.exe /Fo:.arduino-build-0573-conditional\conditional_runtime_test.obj firmware\esp32\tests\conditional_runtime_test.cpp
if errorlevel 1 exit /b 1
.arduino-build-0573-conditional\conditional_runtime_test.exe
