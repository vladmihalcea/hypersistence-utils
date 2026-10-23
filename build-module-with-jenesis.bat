@echo off
setlocal

rem The Jenesis counterpart of test-hibernate-module.bat, e.g. build-module-with-jenesis 73
rem In a pom.xml build, a module's tests are a module of their own, selected by this step path.
rem Jenesis needs JDK 25 or newer, taken from JAVA_HOME_25 when it is set.
rem Jenesis skips tests whose inputs have not changed since their last run, so test.force runs them every time, like mvn clean test.

if "%1" == "" goto usage

set JAVA=java
if defined JAVA_HOME_25 set JAVA="%JAVA_HOME_25%\bin\java"

%JAVA% -Djenesis.make.profiles=test -Djenesis.test.force=true -Djenesis.print.tests=true build/jenesis/Make.java build/maven/compose/module/build-testmodule-hypersistence-utils-hibernate-%1
goto:eof

:usage
echo Usage: %0 63^|71^|73
