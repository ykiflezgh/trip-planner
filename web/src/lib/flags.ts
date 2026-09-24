import { getApp } from 'firebase/app'
import { fetchAndActivate, getRemoteConfig, getValue, isSupported } from 'firebase/remote-config'

/** Remote Config flags (design §14, companion §14). Defaults apply until a fetch succeeds; a fetch never blocks startup for more than `timeoutMs`. */
export interface Flags { webClientEnabled: boolean; calendarFeedEnabled: boolean; suggestOrderEnabled: boolean; source: 'default' | 'remote' }

export const flags: Flags = { webClientEnabled: true, calendarFeedEnabled: true, suggestOrderEnabled: true, source: 'default' }

const DEFAULTS = { web_client_enabled: true, calendar_feed_enabled: true, suggest_order_enabled: true }

export async function loadFlags(timeoutMs = 2500): Promise<Flags> {
  if (import.meta.env.DEV && import.meta.env.VITE_USE_EMULATORS === '1') return flags // e2e (web/e2e) on the dev server: Remote Config has no emulator; defaults (all on), no network. DEV-gated: a production bundle always fetches
  try {
    if (!(await isSupported())) return flags
    const rc = getRemoteConfig(getApp())
    rc.defaultConfig = DEFAULTS
    rc.settings.minimumFetchIntervalMillis = import.meta.env.DEV ? 60_000 : 3_600_000
    const activated = await Promise.race([fetchAndActivate(rc), new Promise<false>((r) => setTimeout(() => r(false), timeoutMs))])
    if (activated === false && rc.fetchTimeMillis <= 0) return flags // nothing cached yet: keep defaults
    flags.webClientEnabled = getValue(rc, 'web_client_enabled').asBoolean()
    flags.calendarFeedEnabled = getValue(rc, 'calendar_feed_enabled').asBoolean()
    flags.suggestOrderEnabled = getValue(rc, 'suggest_order_enabled').asBoolean()
    flags.source = 'remote'
  } catch {
    // Offline or the API not enabled: defaults stand.
  }
  return flags
}
