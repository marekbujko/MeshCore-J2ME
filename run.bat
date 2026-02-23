@echo off
cd /d "%~dp0"
call ant jar
java -jar C:\kemnnx64\KEmulator.jar dist\MeshCore_Test.jar
pause