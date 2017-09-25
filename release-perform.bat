@echo off

if "%1" == "" goto usage

SET passphrase=%1
mvn -P release -Dgpg.passphrase=%passphrase% release:perform
goto:eof

:usage
echo Usage: %0 passphrase