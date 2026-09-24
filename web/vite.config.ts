import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { sentryVitePlugin } from '@sentry/vite-plugin'
import { execSync } from 'node:child_process'

const release = (() => { try { return execSync('git rev-parse --short HEAD').toString().trim() } catch { return 'dev' } })()

/** Dev/e2e: Hosting rewrites /join/** to /app/index.html (firebase/firebase.json); Vite only serves under base, so mirror it. The browser URL stays /join/{code} for the router. */
const joinRewrite = (): Plugin => ({
  name: 'join-rewrite',
  configureServer(server) {
    server.middlewares.use((req, _res, next) => { if (req.url?.startsWith('/join/')) req.url = '/app/'; next() })
  },
})

// Served from the existing Hosting site under /app/ (design v1.1.1 §5, companion §14); the landing
// page and /cal/** stay where they are. Output lands in firebase/hosting/public/app.
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    joinRewrite(),
    // Uploads hidden source maps to Sentry only when CI provides a token (companion §14); no-op otherwise.
    ...(process.env.SENTRY_AUTH_TOKEN ? [sentryVitePlugin({ org: process.env.SENTRY_ORG, project: process.env.SENTRY_PROJECT, release: { name: release }, sourcemaps: { filesToDeleteAfterUpload: ['../firebase/hosting/public/app/assets/*.map'] } })] : []),
  ],
  define: { 'import.meta.env.VITE_RELEASE': JSON.stringify(release) },
  base: '/app/',
  build: {
    outDir: '../firebase/hosting/public/app',
    emptyOutDir: true,
    sourcemap: 'hidden', // no sourceMappingURL comment; .map files are ignored by the Hosting deploy
    rollupOptions: {
      output: {
        // The Kotlin core (engine + shared) as its own chunk, loaded once (companion §6.5 budget: 500 KB gzipped).
        manualChunks: (id) => {
          if (id.includes('/vendor/shared/') || id.includes('node_modules/@js-joda/')) return 'kotlin'
          if (id.includes('node_modules/@firebase/messaging') || id.includes('node_modules/@firebase/installations')) return 'firebase-messaging'
          if (id.includes('node_modules/firebase') || id.includes('node_modules/@firebase')) return 'firebase'
          return undefined
        },
      },
    },
  },
})
