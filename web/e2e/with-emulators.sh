#!/bin/sh
# Runs "$@" from web/ inside `firebase emulators:exec` (fresh, empty emulators; exit code propagates).
# Auth + Firestore only by default (E2E_EMULATORS=auth,firestore,functions for phase 2). Firestore needs Java 17+.
# Same as `npm run e2e:emulators`, plus the JAVA_HOME lookup: sh e2e/with-emulators.sh npm run e2e
set -eu
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17+ 2>/dev/null || true)}"
[ -n "$JAVA_HOME" ] && export PATH="$JAVA_HOME/bin:$PATH"
cd "$(dirname "$0")/../../firebase"
exec firebase emulators:exec --only "${E2E_EMULATORS:-auth,firestore}" --project tripplanner-dev-fe0a4 "cd ../web && $*"
