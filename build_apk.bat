@echo off
cd /d C:\Users\29763\family-tree-app
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
call C:\Users\29763\family-tree-app\gradlew.bat assembleDebug
