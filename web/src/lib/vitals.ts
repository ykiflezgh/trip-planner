import { onCLS, onINP, onLCP, type Metric } from 'web-vitals'

/**
 * Core Web Vitals (companion §12) as Analytics events when the Firebase config has a measurement id,
 * otherwise as console debug lines in dev. Loaded lazily so it never delays first paint.
 */
export function reportWebVitals(hasAnalytics: boolean): void {
  const send = async (m: Metric) => {
    if (hasAnalytics) {
      try {
        const [{ getAnalytics, logEvent }, { getApp }] = await Promise.all([import('firebase/analytics'), import('firebase/app')])
        logEvent(getAnalytics(getApp()), 'web_vital', { name: m.name, value: Math.round(m.name === 'CLS' ? m.value * 1000 : m.value), rating: m.rating, platform: 'web' })
        return
      } catch { /* analytics unavailable */ }
    }
    if (import.meta.env.DEV) console.debug('[web-vital]', m.name, Math.round(m.value), m.rating)
  }
  onLCP(send); onINP(send); onCLS(send)
}
