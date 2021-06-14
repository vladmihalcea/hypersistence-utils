@echo off

mvn -Ptest clean test -pl hibernate-types-%* -am

goto:eof