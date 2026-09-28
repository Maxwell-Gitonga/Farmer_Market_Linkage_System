@echo off
REM Maven wrapper: uses an installed "mvn" if available, otherwise downloads Apache Maven into .mvn\
setlocal
set MAVEN_VERSION=3.9.9
set "BASEDIR=%~dp0"
set "LOCAL_MVN=%BASEDIR%.mvn\apache-maven-%MAVEN_VERSION%\bin\mvn.cmd"
where mvn >nul 2>nul
if not errorlevel 1 goto system
if exist "%LOCAL_MVN%" goto local
echo Maven not found - downloading Apache Maven %MAVEN_VERSION% (one time only)...
if not exist "%BASEDIR%.mvn" mkdir "%BASEDIR%.mvn"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/%MAVEN_VERSION%/binaries/apache-maven-%MAVEN_VERSION%-bin.zip' -OutFile '%BASEDIR%.mvn\maven.zip'; Expand-Archive -Path '%BASEDIR%.mvn\maven.zip' -DestinationPath '%BASEDIR%.mvn' -Force; Remove-Item '%BASEDIR%.mvn\maven.zip'"
if not exist "%LOCAL_MVN%" (
  echo Could not download Maven. Install it from https://maven.apache.org or open the project in IntelliJ IDEA.
  exit /b 1
)
:local
call "%LOCAL_MVN%" -f "%BASEDIR%pom.xml" %*
exit /b %ERRORLEVEL%
:system
call mvn -f "%BASEDIR%pom.xml" %*
exit /b %ERRORLEVEL%
