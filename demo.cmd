@echo off
rem TestThisPuppy demo for Windows: builds everything, then runs every check on a throwaway database
rem with Chrome visible, so you can watch it work. Opens the HTML report at the end.
setlocal
cd /d "%~dp0"

echo.
echo TestThisPuppy demo: QA in a box
echo ===============================
echo Checking what the demo needs...

where java >nul 2>nul || (echo   MISSING: Java 21. Install it from https://adoptium.net and run this again. & exit /b 1)
where mvn >nul 2>nul || (echo   MISSING: Maven. Install it from https://maven.apache.org and run this again. & exit /b 1)
docker info >nul 2>nul || (echo   MISSING: a running Docker. Start Docker Desktop and run this again. & exit /b 1)
if not exist "%ProgramFiles%\Google\Chrome\Application\chrome.exe" if not exist "%LocalAppData%\Google\Chrome\Application\chrome.exe" (echo   MISSING: Google Chrome. Install it and run this again. & exit /b 1)
echo   Java, Maven, Docker and Chrome found.

rem Use the Windows certificate store, so downloads work behind antivirus that inspects secure traffic.
set MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT

echo.
echo Building (the first time downloads libraries, which can take a few minutes)...
call mvn -q -DskipTests install || (echo   The build failed. See the messages above. & exit /b 1)

echo.
echo Running the demo. A Chrome window will open and fill in the forms by itself; leave it alone until it closes.
cd puppy
call mvn -q exec:java -Dexec.args=puppy-demo.properties
set RESULT=%ERRORLEVEL%

echo.
echo Opening the HTML report...
start "" "puppy-report-demo.html"
echo Frames of every browser step are in puppy\demo-frames.
exit /b %RESULT%
