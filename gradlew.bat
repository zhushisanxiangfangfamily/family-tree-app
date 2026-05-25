@echo off
set DIRNAME=%~dp0
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
set ANDROID_HOME=C:\Users\29763\AppData\Local\Android\Sdk
set GRADLE_USER_HOME=%DIRNAME%.gradle

if exist "%DIRNAME%gradle\wrapper\gradle-wrapper.jar" goto run
echo Downloading Gradle wrapper...
mkdir "%DIRNAME%gradle\wrapper" 2>/dev/null
powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-8.5-bin.zip' -OutFile '%DIRNAME%gradle-8.5-bin.zip'" 
if %ERRORLEVEL% neq 0 goto run
powershell -Command "Expand-Archive -Path '%DIRNAME%gradle-8.5-bin.zip' -DestinationPath '%DIRNAME%gradle-tmp' -Force"
copy "%DIRNAME%gradle-tmp\gradle-8.5\lib\gradle-wrapper-*.jar" "%DIRNAME%gradle\wrapper\gradle-wrapper.jar" >/dev/null 2>/dev/null
rmdir /s /q "%DIRNAME%gradle-tmp" 2>/dev/null
del "%DIRNAME%gradle-8.5-bin.zip" 2>/dev/null

:run
"%JAVA_HOME%\bin\java.exe" -cp "%DIRNAME%gradle\wrapper\gradle-wrapper.jar;%DIRNAME%gradle-tmp\gradle-8.5\lib\gradle-launcher-8.5.jar" org.gradle.wrapper.GradleWrapperMain %*
