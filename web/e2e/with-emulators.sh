#!/bin/sh
# Runs "$@" from web/ inside `firebase emulators:exec` (fresh, empty emulators; exit code propagates).
# Auth + Firestore only by default (E2E_EMULATORS=auth,firestore,functions for phase 2). Firestore needs Java 17+.
# Same as `npm run e2e:emulators`, plus the JAVA_HOME lookup: sh e2e/with-emulators.sh npm run e2e
# demo-*: an offline project id, so the emulators never look up (or touch) the real tripplanner-dev-fe0a4.
# emulators:exec takes one shell string, so each argument is re-quoted in single quotes (the only quoting
# every POSIX sh agrees on): sh e2e/with-emulators.sh npx playwright test -g "Move up" survives intact.
set -eu
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17+ 2>/dev/null || true)}"
[ -n "$JAVA_HOME" ] && export PATH="$JAVA_HOME/bin:$PATH"
cmd='cd ../web &&'
for arg in "$@"; do
  cmd="$cmd '$(printf '%s' "$arg" | sed "s/'/'\\\\''/g")'"
done
cd "$(dirname "$0")/../../firebase"
exec firebase emulators:exec --only "${E2E_EMULATORS:-auth,firestore}" --project demo-tripplanner "$cmd"
