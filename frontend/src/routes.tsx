import type { RouteObject } from 'react-router'
import { AppLayout } from './layout/AppLayout'
import { DrillTypePage, DrillTypesPage, GeozonesPage, NotFoundPage } from './pages/Placeholders'
import { SessionsPage } from './pages/sessions/SessionsPage'

/** The five screens of spec 10.1; the upload dialog opens over the sessions list. */
export const routes: RouteObject[] = [
  {
    element: <AppLayout />,
    // Shown while a screen loaded on demand arrives on the first page load.
    hydrateFallbackElement: <p className="loading">Loading…</p>,
    children: [
      { index: true, element: <SessionsPage /> },
      // Loaded on demand: the charts (ECharts) are most of the bundle.
      { path: 'sessions/:id', lazy: { Component: async () => (await import('./pages/session/SessionPage')).SessionPage } },
      { path: 'drill-types', element: <DrillTypesPage /> },
      { path: 'drill-types/:id', element: <DrillTypePage /> },
      { path: 'geozones', element: <GeozonesPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]
