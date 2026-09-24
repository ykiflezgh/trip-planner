import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router'
import './index.css'
import { router } from './app/router'
import { computeDay, resumeRedirect, start, tripDetail, tripList } from './lib/kotlin/tripPlanner'

// Firebase web config is public by design (design §11); it lives in .env.local, not in source.
const config = JSON.parse(import.meta.env.VITE_FIREBASE_CONFIG ?? '{}')
start(config, import.meta.env.VITE_APP_LINK_HOST ?? '')
void resumeRedirect().catch(() => undefined)
// Dev only: lets the browser console poke the facades directly.
if (import.meta.env.DEV) (window as unknown as { __tp: unknown }).__tp = { tripList, tripDetail, computeDay }

createRoot(document.getElementById('root')!).render(<StrictMode><RouterProvider router={router} /></StrictMode>)
