import { Link } from 'react-router'
import { useSessions } from '../api/sessions'
import { localDateTime } from '../format'

/** Screen 1 (spec 10.1): the sessions list. The full table, filters and upload follow in step 15. */
export function SessionsPage() {
  const sessions = useSessions()
  if (sessions.isPending) return <p>Loading sessions…</p>
  if (sessions.isError) return <p role="alert">Could not load the sessions: {sessions.error.message}</p>
  return (
    <section>
      <h1>Sessions</h1>
      <p>{sessions.data.totalItems} sessions</p>
      <ul>
        {sessions.data.items.map((session) => (
          <li key={session.id}>
            <Link to={`/sessions/${session.id}`}>{localDateTime(session.startTime, session.localTzOffsetSec)}</Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
