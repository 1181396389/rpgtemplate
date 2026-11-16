@echo off
setlocal
cd /d "%~dp0"
if defined JAVA_HOME (
  set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_BIN=java"
)
"%JAVA_BIN%" "%~dp0tools\BuildNeoForgeMod.java" %*
exit /b %ERRORLEVEL%
