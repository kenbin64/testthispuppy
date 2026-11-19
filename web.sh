#!/bin/sh
# TestThisPuppy in your browser. Run it; a page opens. Ctrl+C stops it.
#   ./web.sh            this computer only
#   ./web.sh --phone    also your phone or tablet on the same Wi-Fi (a 6-digit PIN is shown here)
# Build once first: mvn -q install -DskipTests
exec mvn -q -f "$(dirname "$0")/puppy/pom.xml" exec:java -Dexec.args="web $*"
