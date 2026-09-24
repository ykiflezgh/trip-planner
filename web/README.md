# Trip Planner — web client (W0 spike)

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

Spike results (2026-09-24) are in the root README under "Web W0 spike".
