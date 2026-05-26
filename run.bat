@echo off
setlocal

if not exist build mkdir build

javac --release 8 -d build *.java
if errorlevel 1 (
    echo Compilation failed.
    exit /b 1
)

java -cp build Main
