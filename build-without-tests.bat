@echo off

mvn -DskipTests -Darguments=-DskipTests clean install

goto:eof