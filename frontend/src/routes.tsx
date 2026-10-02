import type { RouteObject } from 'react-router'
import { AppLayout } from './layout/AppLayout'
import { DrillTypePage, DrillTypesPage, GeozonesPage, NotFoundPage, SessionPage } from './pages/Placeholders'
import { SessionsPage } from './pages/sessions/SessionsPage'

/** The five screens of spec 10.1; the upload dialog opens over the sessions list. */
export const routes: RouteObject[] = [
  {
    element: <AppLayout />,
    children: [
      { index: true, element: <SessionsPage /> },
      { path: 'sessions/:id', element: <SessionPage /> },
      { path: 'drill-types', element: <DrillTypesPage /> },
      { path: 'drill-types/:id', element: <DrillTypePage /> },
      { path: 'geozones', element: <GeozonesPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]
