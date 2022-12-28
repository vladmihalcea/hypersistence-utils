@echo off

mvn -Ptest clean test -pl hypersistence-utils-hibernate-%* -am

goto:eof