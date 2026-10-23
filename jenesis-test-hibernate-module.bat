@echo off

rem The Jenesis counterpart of test-hibernate-module.bat, e.g. jenesis-test-hibernate-module 73
rem In a pom.xml build, a module's tests are a module of their own, selected by this step path.

java -Djenesis.make.profiles=test -Djenesis.print.tests=true build/jenesis/Make.java build/maven/compose/module/build-testmodule-hypersistence-utils-hibernate-%1

goto:eof
