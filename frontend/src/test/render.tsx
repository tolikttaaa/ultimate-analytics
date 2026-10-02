import { QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { createQueryClient } from '../api/queryClient'

/**
 * Renders [element] on the route [path] at [url] with the app's providers; other paths show a stub page. Returns the
 * router to inspect the location.
 */
export function renderPage(element: ReactElement, url = '/', path = '/') {
  const router = createMemoryRouter(
    [{ path, element }, { path: '*', element: <p>other page</p> }],
    { initialEntries: [url] },
  )
  render(
    <QueryClientProvider client={createQueryClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return router
}
