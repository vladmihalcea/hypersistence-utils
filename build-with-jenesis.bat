@echo off
setlocal

rem The Jenesis counterpart of test.bat: build and test every module.
rem Jenesis needs JDK 25 or newer, taken from JAVA_HOME_25 when it is set.
rem Jenesis skips tests whose inputs have not changed since their last run, so test.force runs them every time, like mvn clean test.

set JAVA=java
if defined JAVA_HOME_25 set JAVA="%JAVA_HOME_25%\bin\java"

%JAVA% -Djenesis.make.profiles=test -Djenesis.test.force=true build/jenesis/Make.java %*

goto:eof
