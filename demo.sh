#!/usr/bin/env bash
# TestThisPuppy demo for Mac and Linux: builds everything, then runs every check on a throwaway database
# with Chrome visible, so you can watch it work. Opens the HTML report at the end.
set -u
cd "$(dirname "$0")"

echo
echo "TestThisPuppy demo: QA in a box"
echo "==============================="
echo "Checking what the demo needs..."
missing=0
command -v java >/dev/null || { echo "  MISSING: Java 21 (https://adoptium.net)"; missing=1; }
command -v mvn >/dev/null || { echo "  MISSING: Maven (https://maven.apache.org)"; missing=1; }
docker info >/dev/null 2>&1 || { echo "  MISSING: a running Docker"; missing=1; }
if ! command -v google-chrome >/dev/null && ! command -v chromium >/dev/null && [ ! -d "/Applications/Google Chrome.app" ]; then
  echo "  MISSING: Google Chrome"; missing=1
fi
[ "$missing" = 0 ] || { echo "Install what is missing and run this again."; exit 1; }
echo "  Java, Maven, Docker and Chrome found."

echo
echo "Building (the first time downloads libraries, which can take a few minutes)..."
mvn -q -DskipTests install || { echo "  The build failed. See the messages above."; exit 1; }

echo
echo "Running the demo. A Chrome window will open and fill in the forms by itself; leave it alone until it closes."
cd puppy
mvn -q exec:java -Dexec.args=puppy-demo.properties
result=$?

echo
echo "Opening the HTML report..."
if command -v open >/dev/null; then open puppy-report-demo.html; elif command -v xdg-open >/dev/null; then xdg-open puppy-report-demo.html; fi
echo "Frames of every browser step are in puppy/demo-frames."
exit $result
