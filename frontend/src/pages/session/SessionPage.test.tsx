import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { drillType, effort, geozone, segment, sessionDetail, sessionSeries, uiConfig, windowMetrics } from '../../test/data'
import { fakeApi, problem } from '../../test/fakeApi'
import { renderPage } from '../../test/render'
import { SessionPage } from './SessionPage'

// jsdom has no canvas: a chart renders as its title, with buttons standing in for brushing and clicking a marker.
vi.mock('../../components/charts/EChart', () => ({
  EChart: ({ option, onEvents }: { option: { title?: { text: string } }; onEvents?: Record<string, (event: unknown) => void> }) => (
    <div role="img" aria-label={option.title?.text ?? 'Effort chart'}>
      {onEvents?.brushEnd && (
        <button onClick={() => onEvents.brushEnd({ areas: [{ coordRange: [600.4, 1199.6] }] })}>brush 600–1200</button>
      )}
      {onEvents?.click && (
        <button onClick={() => onEvents.click({ componentType: 'markPoint', data: { effortId: 'e2' } })}>marker e2</button>
      )}
    </div>
  ),
}))
vi.mock('../../components/map/EffortMap', () => ({ EffortMap: () => <div>effort map</div> }))

// jsdom has no WebGL either: the map renders what it was given.
vi.mock('../../components/map/SessionMap', () => ({
  SessionMap: ({ timeWindow, geozone, config }: { timeWindow: number[] | null; geozone: { name: string } | null; config: { vectorStyleUrl: string } }) => (
    <div role="region" aria-label="Map">
      {config.vectorStyleUrl} · window {timeWindow ? timeWindow.join('–') : 'none'} · geozone {geozone?.name ?? 'none'}
    </div>
  ),
}))

function renderSession(overrides: Parameters<typeof sessionDetail>[0] = {}, url = '/sessions/s1') {
  let detail = sessionDetail({ segments: [segment({ endT: 3900 })], ...overrides })
  const calls = fakeApi({
    'GET /api/sessions/s1': () => detail,
    'GET /api/sessions/s1/series': () => sessionSeries(),
    'GET /api/sessions/s1/efforts': () => [effort(), effort({ id: 'e2', startT: 6, peakT: 7, endT: 8 })],
    'GET /api/sessions/s1/metrics': () => windowMetrics({ decelCount: 4, efforts: { ...windowMetrics().efforts, count: 3 } }),
    'GET /api/drill-types': () => [drillType()],
    'GET /api/config': () => uiConfig(),
    'GET /api/geozones': () => [geozone()],
    'PATCH /api/sessions/s1': (call) => {
      detail = { ...detail, ...(call.body as object), surfaceSource: 'MANUAL' }
      return detail
    },
    'POST /api/sessions/s1/recompute': () => {
      detail = { ...detail, outdated: false }
      return detail
    },
  })
  const router = renderPage(<SessionPage />, url, '/sessions/:id')
  return Object.assign(calls, { router })
}

describe('SessionPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows header, segment strip, the three charts and the session metrics', async () => {
    renderSession({ geozoneName: 'Akrotiri field', surface: 'GRASS', surfaceSource: 'GEOZONE' })

    expect(await screen.findByRole('heading', { name: '2026-03-25 15:39' })).toBeInTheDocument()
    expect(screen.getByText('Akrotiri field')).toBeInTheDocument()
    expect(screen.getByText('Grass', { selector: '.chip' })).toBeInTheDocument()
    for (const [label, value] of [['Duration', '2:10:00'], ['Active', '30:57'], ['Distance', '7.48 km'], ['Efforts', '26']]) {
      expect(screen.getByText(label, { selector: 'dt' }).nextSibling).toHaveTextContent(value)
    }

    const strip = screen.getByLabelText('Segments')
    expect(within(strip).getByText('Sprints').closest('.segment')).toHaveStyle({ left: '0%', width: '50%', background: '#e65100' })

    for (const title of ['Speed (km/h)', 'GPS acceleration (m/s²)', 'Heart rate (bpm)']) {
      expect(await screen.findByRole('img', { name: title })).toBeInTheDocument()
    }

    const metrics = within(screen.getByRole('region', { name: 'Metrics' }))
    expect(metrics.getByRole('columnheader', { name: 'Session' })).toBeInTheDocument()
    expect(metrics.getByText('Peak speed').nextSibling).toHaveTextContent('23.4 km/h / 27.4 km/h')
    expect(metrics.getByText('Mean acceleration (GPS)').closest('tr')).toHaveAttribute('title', expect.stringContaining('1 Hz GPS'))
    expect(metrics.getByText('Zone 5').nextSibling).toHaveTextContent('15 %')
    expect(metrics.getByText('Sprint').nextSibling).toHaveTextContent('5:00 · 900 m')
  })

  it('shows the map with the window of the URL and the matched geozone', async () => {
    renderSession({ geozoneId: 'g1', geozoneName: 'Akrotiri field' }, '/sessions/s1?from=600&to=1200')

    expect(await screen.findByRole('region', { name: 'Map' })).toHaveTextContent(
      'https://tiles.openfreemap.org/styles/liberty · window 600–1200 · geozone Akrotiri field',
    )
  })

  it('ignores a window that does not fit the session', async () => {
    renderSession({}, '/sessions/s1?from=600&to=99999')
    expect(await screen.findByRole('region', { name: 'Map' })).toHaveTextContent('window none · geozone none')
  })

  it('selects a window by brushing, shows its metrics and clears it with Escape', async () => {
    const calls = renderSession()
    const { router } = calls
    await userEvent.click(await screen.findByRole('button', { name: 'brush 600–1200' }))

    expect(router.state.location.search).toBe('?from=600&to=1200')
    expect(screen.getByText('10:00–20:00')).toBeInTheDocument()
    const metrics = within(screen.getByRole('region', { name: 'Metrics' }))
    expect(metrics.getByRole('columnheader', { name: 'Window' })).toBeInTheDocument()
    await vi.waitFor(() => expect(metrics.getByText('Decelerations (GPS)').parentElement).toHaveTextContent('Decelerations (GPS)314'))
    const request = calls.find((call) => call.path === '/api/sessions/s1/metrics')!
    expect([request.query.get('from'), request.query.get('to')]).toEqual(['600', '1200'])

    await userEvent.keyboard('{Escape}')
    expect(router.state.location.search).toBe('')
    expect(metrics.queryByRole('columnheader', { name: 'Window' })).not.toBeInTheDocument()
  })

  it('selects a segment from the strip', async () => {
    const { router } = renderSession()
    await userEvent.click(await screen.findByText('Sprints'))
    expect(router.state.location.search).toBe('?from=0&to=3900')
  })

  it('opens the drawer of a clicked effort and steps through the efforts', async () => {
    renderSession({}, '/sessions/s1?from=600&to=1200')
    await userEvent.click(await screen.findByRole('button', { name: 'marker e2' }))

    const drawer = within(screen.getByRole('dialog', { name: 'Effort 2 of 2' }))
    expect(drawer.getByText('0:06–0:08')).toBeInTheDocument()
    expect(drawer.getByText('Peak speed').nextSibling).toHaveTextContent('21.6 km/h')
    expect(drawer.getByText('Peak acceleration (GPS)').closest('tr')).toHaveAttribute('title', expect.stringContaining('1 Hz GPS'))
    expect(await drawer.findByText('effort map')).toBeInTheDocument()
    expect(drawer.getByRole('button', { name: 'Next' })).toBeDisabled()

    await userEvent.click(drawer.getByRole('button', { name: 'Previous' }))
    expect(screen.getByRole('dialog', { name: 'Effort 1 of 2' })).toBeInTheDocument()

    // Escape closes the drawer first and keeps the window.
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByText('10:00–20:00')).toBeInTheDocument()
  })

  it('warns about Smart recording', async () => {
    renderSession({ recordingMode: 'SMART' })
    expect(await screen.findByText(/sprint and acceleration numbers are low-confidence/)).toBeInTheDocument()
  })

  it('sets the surface by hand', async () => {
    const calls = renderSession()
    await userEvent.selectOptions(await screen.findByLabelText('Surface'), 'SAND')

    expect(await screen.findByText('Sand', { selector: '.chip' })).toBeInTheDocument()
    expect(calls.find((call) => call.method === 'PATCH')?.body).toEqual({ surface: 'SAND' })
  })

  it('recomputes an outdated session', async () => {
    const calls = renderSession({ outdated: true })
    expect(await screen.findByText('outdated')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Recompute' }))

    await vi.waitFor(() => expect(screen.queryByText('outdated')).not.toBeInTheDocument())
    expect(calls.some((call) => call.method === 'POST' && call.path === '/api/sessions/s1/recompute')).toBe(true)
  })

  it('says when the session does not exist', async () => {
    fakeApi({ 'GET /api/sessions/s1': () => problem(404, 'Session s1 not found') })
    renderPage(<SessionPage />, '/sessions/s1', '/sessions/:id')
    expect(await screen.findByRole('alert')).toHaveTextContent('This session does not exist.')
  })
})
