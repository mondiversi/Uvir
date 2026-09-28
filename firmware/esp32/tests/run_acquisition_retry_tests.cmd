@echo off
call "C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
if errorlevel 1 exit /b 1
if not exist .arduino-build-0588-retry mkdir .arduino-build-0588-retry
cl /nologo /EHsc /std:c++17 /W4 /Fe:.arduino-build-0588-retry\acquisition_retry_test.exe /Fo:.arduino-build-0588-retry\acquisition_retry_test.obj firmware\esp32\tests\acquisition_retry_test.cpp
if errorlevel 1 exit /b 1
.arduino-build-0588-retry\acquisition_retry_test.exe
