import { Component, type ReactNode } from 'react'

/** Catches render errors, shows a plain fallback, and reports through the lazily loaded Sentry module. */
export class ErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }
  static getDerivedStateFromError() { return { failed: true } }
  componentDidCatch(error: unknown) { void import('../lib/sentry').then((m) => m.captureException(error)) }
  render() {
    if (this.state.failed) return <p role="alert" className="p-6 text-sm text-red-700">Something went wrong. Reload the page; if it keeps happening, the error has been reported.</p>
    return this.props.children
  }
}
