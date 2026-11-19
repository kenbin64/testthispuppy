#!/bin/sh
# TestThisPuppy quick check. Point it at a snippet file, "-" (code from a pipe), a project folder, a Git URL or a site.
#   ./check.sh https://example.com
#   ./check.sh ~/code/my-app --level quick
#   cat Snippet.java | ./check.sh -
# Results go to ./puppy-check/ (report.html, report.json, code-fixes.md). Build once first: mvn -q install -DskipTests
here="$(cd "$(dirname "$0")" && pwd)"
exec mvn -q -f "$here/puppy/pom.xml" exec:java -Dexec.args="check $*"
