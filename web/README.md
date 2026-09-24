# Trip Planner — web client

React + TypeScript + Vite UI over the Kotlin core compiled to JS (design v1.1.1 §6.5 / §18 #10,
companion *Web Client (React) System Design* 0.2). Nothing here talks to Firestore directly:
`src/lib/kotlin/tripPlanner.ts` is the only file that touches the Kotlin exports, and
`src/hooks/useKotlinState.ts` turns a Kotlin `StateFlow` into React state.

```bash
./gradlew :shared:packageForWeb      # Kotlin/JS build -> web/vendor/shared (gitignored)
cp .env.example .env.local           # then paste `firebase apps:sdkconfig WEB` values
npm install && npm run dev           # http://localhost:5173/app/
npm run build                        # -> firebase/hosting/public/app (served at /app/**)
```

Layout: `src/app` (router, shell), `src/features/{trips,calendar,map}` (UI only), `src/lib/kotlin`
(the typed boundary to the Kotlin core), `src/lib/time.ts` (display helpers), `src/hooks`.
`npm test` runs Vitest. W0 and W1 results are in the root README under "Web W0 spike" / "Web W1".

## E2E

Playwright (Chromium) against the Firebase Auth + Firestore emulators, nothing leaves the machine:
`npm run e2e:emulators`, or `sh e2e/with-emulators.sh npm run e2e`, which also sets `JAVA_HOME` (the
Firestore emulator needs Java 17+). Both start the emulators under the offline project id
`demo-tripplanner` (Firebase treats `demo-*` as having no cloud project, so a run can never reach
`tripplanner-dev-fe0a4`), serve the app with `--mode e2e` (`.env.e2e`: emulator config, no secrets) on
port 5174 and run the `e2e/*.e2e.ts` specs, a glob Vitest ignores. `npm run e2e:ui` opens Playwright's
UI mode and `npm run e2e:typecheck` type-checks the specs (`e2e/tsconfig.json`); a filter survives the
script's quoting, e.g. `sh e2e/with-emulators.sh npx playwright test -g "Move up"`.
