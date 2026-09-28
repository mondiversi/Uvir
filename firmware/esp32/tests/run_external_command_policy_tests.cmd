@echo off
call "C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
if errorlevel 1 exit /b 1
if not exist .arduino-build-external-policy mkdir .arduino-build-external-policy
cl /nologo /EHsc /std:c++17 /W4 /Fe:.arduino-build-external-policy\external_command_policy_test.exe /Fo:.arduino-build-external-policy\external_command_policy_test.obj firmware\esp32\tests\external_command_policy_test.cpp
if errorlevel 1 exit /b 1
.arduino-build-external-policy\external_command_policy_test.exe
