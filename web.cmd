@echo off
rem TestThisPuppy in your browser. Double-click this file; a page opens. Close this window to stop it.
rem   web.cmd            this computer only
rem   web.cmd --phone    also your phone or tablet on the same Wi-Fi (a 6-digit PIN is shown here)
rem Build once first: mvn -q install -DskipTests
title TestThisPuppy
mvn -q -f "%~dp0puppy\pom.xml" exec:java -Dexec.args="web %*"
