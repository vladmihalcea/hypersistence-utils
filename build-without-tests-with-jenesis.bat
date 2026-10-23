@echo off
setlocal

rem The Jenesis counterpart of build-without-tests.bat: build every module and install the jars into the local Maven repository.
rem Jenesis needs JDK 25 or newer, taken from JAVA_HOME_25 when it is set.

set JAVA=java
if defined JAVA_HOME_25 set JAVA="%JAVA_HOME_25%\bin\java"

%JAVA% -Djenesis.test.skip=true build/jenesis/Make.java export

goto:eof
