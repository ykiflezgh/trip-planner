import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Served from the existing Hosting site under /app/ (design v1.1.1 §5, companion §14); the landing
// page and /cal/** stay where they are. Output lands in firebase/hosting/public/app.
export default defineConfig({
  plugins: [react()],
  base: '/app/',
  build: {
    outDir: '../firebase/hosting/public/app',
    emptyOutDir: true,
    sourcemap: true,
    rollupOptions: {
      output: {
        // The Kotlin core (engine + shared) as its own chunk, loaded once (companion §6.5 budget: 500 KB gzipped).
        manualChunks: (id) => (id.includes('/vendor/shared/') ? 'kotlin' : id.includes('node_modules/firebase') || id.includes('node_modules/@firebase') ? 'firebase' : undefined),
      },
    },
  },
})
