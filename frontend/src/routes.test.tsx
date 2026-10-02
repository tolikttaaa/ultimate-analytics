import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createQueryClient } from './api/queryClient'
import { sessionDetail, sessionPage, sessionSeries, sessionSummary, uiConfig } from './test/data'
import { fakeApi } from './test/fakeApi'
import { routes } from './routes'

// jsdom has no canvas and no WebGL.
vi.mock('./components/charts/EChart', () => ({ EChart: () => null }))
vi.mock('./components/map/SessionMap', () => ({ SessionMap: () => null }))
vi.mock('./components/map/GeozoneMap', () => ({ GeozoneMap: () => null }))

/** Answers the API calls of the screens with fixed data. */
function stubApi() {
  fakeApi({
    'GET /api/sessions': () => sessionPage([sessionSummary({ startTime: '2026-09-30T16:07:47Z', localTzOffsetSec: 10800 })]),
    'GET /api/sessions/s1': () => sessionDetail({ startTime: '2026-09-30T16:07:47Z', localTzOffsetSec: 10800 }),
    'GET /api/sessions/s1/series': () => sessionSeries(),
    'GET /api/sessions/s1/efforts': () => [],
    'GET /api/drill-types': () => [],
    'GET /api/config': () => uiConfig(),
    'GET /api/geozones': () => [],
    'GET /api/analysis/parameters': () => ({ analysisVersion: 1, parameters: {} }),
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

    expect(await screen.findByRole('link', { name: '2026-09-30 19:07' })).toHaveAttribute('href', '/sessions/s1')
    expect(await screen.findByText('analysis v1')).toBeInTheDocument()
  })

  it('loads the session screen on demand', async () => {
    stubApi()
    renderAt('/sessions/s1')
    expect(await screen.findByRole('heading', { name: '2026-09-30 19:07' })).toBeInTheDocument()
  })

  it('shows the geozones', async () => {
    stubApi()
    renderAt('/geozones')
    expect(await screen.findByRole('heading', { name: 'Geozones' })).toBeInTheDocument()
  })

  it('shows the drill types', async () => {
    stubApi()
    renderAt('/drill-types')
    expect(await screen.findByRole('heading', { name: 'Drill types' })).toBeInTheDocument()
  })

  it('shows not found for unknown paths', async () => {
    stubApi()
    renderAt('/nothing-here')
    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
  })
})
