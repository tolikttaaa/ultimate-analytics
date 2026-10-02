import { QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { createQueryClient } from '../api/queryClient'

/** Renders [element] at [url] with the app's providers; returns the router to inspect the location. */
export function renderPage(element: ReactElement, url = '/') {
  const router = createMemoryRouter(
    [{ path: '/', element }, { path: '/sessions/:id', element: <p>session screen</p> }],
    { initialEntries: [url] },
  )
  render(
    <QueryClientProvider client={createQueryClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return router
}
