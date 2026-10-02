import { useParams } from 'react-router'

/* Screens of spec 10.1 that later steps fill in. */

export function SessionPage() {
  const { id } = useParams()
  return <h1>Session {id}</h1>
}

export function GeozonesPage() {
  return <h1>Geozones</h1>
}

export function DrillTypesPage() {
  return <h1>Drill types</h1>
}

export function DrillTypePage() {
  const { id } = useParams()
  return <h1>Drill type {id}</h1>
}

export function NotFoundPage() {
  return <h1>Page not found</h1>
}
