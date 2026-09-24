import * as Sentry from '@sentry/react'

const DSN = (import.meta.env.VITE_SENTRY_DSN as string | undefined) || undefined

/**
 * Error reporting (companion §12): Crashlytics has no web SDK. Off without a DSN. PII stays off (the SDK default),
 * calendar-feed URLs scrubbed (they carry bearer tokens), release = git SHA so source maps match.
 * Imported lazily after first paint so the SDK stays out of the startup budget (companion §6.5).
 */
export function initSentry(): void {
  if (!DSN) return
  Sentry.init({
    dsn: DSN,
    release: import.meta.env.VITE_RELEASE || undefined,
    environment: import.meta.env.MODE,
    integrations: [Sentry.browserTracingIntegration()],
    tracesSampleRate: 0.1,
    beforeSend: (event) => scrub(event),
    beforeBreadcrumb: (crumb) => (typeof crumb.data?.url === 'string' && crumb.data.url.includes('/cal/') ? { ...crumb, data: { ...crumb.data, url: '/cal/<redacted>' } } : crumb),
  })
}

const FEED = /\/cal\/[^\s"']+/g

function scrub<T extends Sentry.ErrorEvent>(event: T): T {
  if (event.request?.url) event.request.url = event.request.url.replace(FEED, '/cal/<redacted>')
  if (event.message) event.message = event.message.replace(FEED, '/cal/<redacted>')
  for (const e of event.exception?.values ?? []) if (e.value) e.value = e.value.replace(FEED, '/cal/<redacted>')
  return event
}

/** Expected errors are tagged, not alerted (companion §12): a deleted stop, a revoked membership. */
export const expectedError = (message: string): boolean => /NOT_FOUND|permission-denied|PERMISSION_DENIED/i.test(message)

export const captureException = (e: unknown): void => { if (DSN) Sentry.captureException(e) }
