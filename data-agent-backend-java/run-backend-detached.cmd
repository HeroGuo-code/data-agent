@echo off
cd /d "%~dp0"
java -jar target\data-agent-backend-java-1.0.jar > backend-new.log 2> backend-new.err.log
