@echo off

rem The Jenesis counterpart of test.bat: build and test every module, quietly.

java -Djenesis.make.profiles=test build/jenesis/Make.java %*
