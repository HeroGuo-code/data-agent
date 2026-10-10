@echo off
rem Start the backend JAR. Ensure a JDK 21 `java` is on your PATH,
rem or set JAVA_HOME to your JDK 21 installation first.
cd /d "%~dp0"
java -jar target\data-agent-backend-java-1.0.jar
