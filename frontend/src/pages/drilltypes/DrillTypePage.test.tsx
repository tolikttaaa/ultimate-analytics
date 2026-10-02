import { fireEvent, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { endOfDay, startOfDay } from '../../dates'
import { drillTypeStats } from '../../test/data'
import { fakeApi, problem } from '../../test/fakeApi'
import { renderPage } from '../../test/render'
import { DrillTypePage } from './DrillTypePage'

// jsdom has no canvas: the chart renders its title and point count.
vi.mock('../../components/charts/EChart', () => ({
  EChart: ({ option }: { option: { title: { text: string }; series: { data: unknown[] }[] } }) => (
    <div role="img" aria-label={option.title.text}>{option.series[0].data.length} points</div>
  ),
}))

const stats = drillTypeStats([
  { id: 's1', startTime: '2026-08-19T16:06:00Z', surface: 'GRASS', peakBest: 7.5 },
  { id: 's2', startTime: '2026-09-16T16:07:00Z', surface: 'SAND', peakBest: 7.9 },
])

function renderDetail(response: () => unknown = () => stats) {
  const calls = fakeApi({ 'GET /api/drill-types/d1/stats': response })
  const router = renderPage(<DrillTypePage />, '/drill-types/d1', '/drill-types/:id')
  return { calls, router }
}

describe('DrillTypePage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows totals, the trend and the sessions of the drill type', async () => {
    renderDetail()
    expect(await screen.findByRole('heading', { name: 'Sprints' })).toBeInTheDocument()
    for (const [label, value] of [['Sessions', '2'], ['Segments', '4'], ['Efforts', '26'], ['Best peak speed', '27.4 km/h']]) {
      expect(screen.getByText(label, { selector: 'dt' }).nextSibling).toHaveTextContent(value)
    }
    expect(screen.getByRole('img', { name: 'Best peak speed (km/h)' })).toHaveTextContent('2 points')
    expect(within(screen.getByRole('region', { name: 'Metrics' })).getByRole('columnheader', { name: 'All segments' })).toBeInTheDocument()

    const rows = within(screen.getAllByRole('table')[0]).getAllByRole('row').slice(1)
    expect(within(rows[1]).getByRole('link', { name: '2026-09-16' })).toHaveAttribute('href', '/sessions/s2')
    expect(rows[1]).toHaveTextContent('Sand220:00528.4 km/h')
  })

  it('switches the trend metric', async () => {
    renderDetail()
    await userEvent.selectOptions(await screen.findByLabelText('Trend'), 'efforts-per-min')
    expect(screen.getByRole('img', { name: 'Efforts per active minute (/min)' })).toBeInTheDocument()
  })

  it('filters by surface and date range through the URL', async () => {
    const { calls, router } = renderDetail()
    await screen.findByRole('heading', { name: 'Sprints' })

    await userEvent.selectOptions(screen.getByLabelText('Surface'), 'SAND')
    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-09-01' } })
    fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-09-30' } })

    await vi.waitFor(() => expect(calls.at(-1)?.query.get('to')).toBe(endOfDay('2026-09-30')))
    expect(calls.at(-1)?.query.get('surface')).toBe('SAND')
    expect(calls.at(-1)?.query.get('from')).toBe(startOfDay('2026-09-01'))
    expect(router.state.location.search).toBe('?surface=SAND&from=2026-09-01&to=2026-09-30')
    expect(await screen.findByRole('columnheader', { name: 'All sand segments' })).toBeInTheDocument()
  })

  it('explains an empty result', async () => {
    renderDetail(() => drillTypeStats([]))
    expect(await screen.findByText(/No segments of this type yet/)).toBeInTheDocument()
  })

  it('says when the drill type does not exist', async () => {
    renderDetail(() => problem(404, 'Drill type d1 not found'))
    expect(await screen.findByRole('alert')).toHaveTextContent('This drill type does not exist.')
  })
})
