@echo off
rem TestThisPuppy quick check. Point it at a snippet file, "-" (code from a pipe), a project folder, a Git URL or a site.
rem   check.cmd https://example.com
rem   check.cmd C:\code\my-app --level quick
rem   type Snippet.java | check.cmd -
rem Results go to .\puppy-check\ (report.html, report.json, code-fixes.md). Build once first: mvn -q install -DskipTests
mvn -q -f "%~dp0puppy\pom.xml" exec:java -Dexec.args="check %*"
