import { Link } from 'react-router'

/** Shown for /app/t/** while `web_client_enabled` is off (companion §14); invites still work. */
export function ComingSoon() {
  return (
    <main className="mx-auto max-w-md p-6 text-center">
      <h1 className="mb-2 text-xl font-semibold">The web planner is coming soon</h1>
      <p className="mb-4 text-sm text-stone-600">Trips are being rolled out to the web in stages. Use the Trip Planner app on your phone for now; invite links still work here.</p>
      <Link to="/app" className="text-indigo-700 underline">Back to your trips</Link>
    </main>
  )
}
