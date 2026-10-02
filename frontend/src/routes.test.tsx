import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createQueryClient } from './api/queryClient'
import { routes } from './routes'

/** Answers the API calls of the app shell with fixed data. */
function stubApi() {
  vi.stubGlobal('fetch', async (request: Request) => {
    const path = new URL(request.url).pathname
    const body = path === '/api/sessions'
      ? { items: [{ id: 's1', startTime: '2026-09-30T16:07:47Z', localTzOffsetSec: 10800 }], totalItems: 1, page: 0, size: 20, totalPages: 1 }
      : { analysisVersion: 1, parameters: {} }
    return new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' } })
  })
}

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  render(
    <QueryClientProvider client={createQueryClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

describe('routes', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows the sessions list with the analysis version', async () => {
    stubApi()
    renderAt('/')

    expect(await screen.findByText('1 sessions')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '2026-09-30 19:07' })).toHaveAttribute('href', '/sessions/s1')
    expect(await screen.findByText('analysis v1')).toBeInTheDocument()
  })

  it('routes to the session screen', async () => {
    stubApi()
    renderAt('/sessions/s1')
    expect(await screen.findByRole('heading', { name: 'Session s1' })).toBeInTheDocument()
  })

  it('shows not found for unknown paths', async () => {
    stubApi()
    renderAt('/nothing-here')
    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
  })
})
