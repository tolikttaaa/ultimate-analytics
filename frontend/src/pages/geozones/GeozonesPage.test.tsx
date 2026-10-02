import { fireEvent, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Draft } from './draft'
import { geozone, sessionDetail, sessionPage, sessionSummary, uiConfig } from '../../test/data'
import { fakeApi } from '../../test/fakeApi'
import { renderPage } from '../../test/render'
import { GeozonesPage } from './GeozonesPage'

const CORNERS = [[33.1, 34.7], [33.102, 34.7], [33.102, 34.702], [33.1, 34.702]]

// jsdom has no WebGL: the map shows its state and has buttons for the clicks it would pass on.
vi.mock('../../components/map/GeozoneMap', () => ({
  GeozoneMap: (props: {
    draft: Draft | null
    selectedId: string | null
    fallbackCenter: number[] | null
    geozones: { id: string; name: string }[]
    onMapClick: (position: number[]) => void
    onFinish: () => void
    onSelect: (id: string) => void
  }) => (
    <div aria-label="Map">
      <span>selected {props.selectedId ?? 'none'} · start {props.fallbackCenter?.join(',') ?? 'none'}</span>
      <button onClick={() => props.onMapClick(CORNERS[props.draft?.kind === 'polygon' ? props.draft.points.length : 0])}>click map</button>
      <button onClick={props.onFinish}>double-click map</button>
      {props.geozones.map((zone) => <button key={zone.id} onClick={() => props.onSelect(zone.id)}>select {zone.name}</button>)}
    </div>
  ),
}))

const field = geozone({ id: 'g1', name: 'Akrotiri field' })
const beach = geozone({
  id: 'g2',
  name: 'Beach courts',
  surface: 'SAND',
  shape: { type: 'Polygon', coordinates: [[[33, 34], [33.2, 34], [33.2, 34.1], [33, 34]]] },
})

function renderGeozones(zones = [field, beach]) {
  const calls = fakeApi({
    'GET /api/config': () => uiConfig(),
    'GET /api/geozones': () => zones,
    'GET /api/sessions': () => sessionPage([sessionSummary()]),
    'GET /api/sessions/s1': () => sessionDetail({ startPosition: { lat: 34.70786, lon: 33.12787 } }),
    'POST /api/geozones': () => ({ geozone: geozone({ id: 'new' }), affectedSessionCount: 3 }),
    'PATCH /api/geozones/g1': () => ({ geozone: field, affectedSessionCount: 1 }),
    'DELETE /api/geozones/g2': () => ({ geozone: null, affectedSessionCount: 0 }),
  })
  renderPage(<GeozonesPage />)
  return calls
}

const sent = (calls: ReturnType<typeof fakeApi>, method: string) => calls.find((call) => call.method === method)

describe('GeozonesPage', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('lists the geozones with surface and shape, and selects them on the map', async () => {
    renderGeozones()
    const rows = within(await screen.findByRole('list', { name: 'Geozones' })).getAllByRole('listitem')
    expect(rows.map((row) => row.textContent)).toEqual([
      expect.stringMatching(/^Akrotiri fieldGrassCircle, 150 m/),
      expect.stringMatching(/^Beach courtsSandPolygon, 3 corners/),
    ])

    await userEvent.click(await screen.findByRole('button', { name: 'select Beach courts' }))
    expect(rows[1]).toHaveClass('selected')
  })

  it('creates a circle: centre on the map, then name, surface and radius', async () => {
    const calls = renderGeozones()
    await userEvent.click(await screen.findByRole('button', { name: 'New circle' }))
    const editor = within(screen.getByRole('form', { name: 'New circle' }))
    expect(editor.getByText('Click the centre of the field on the map.')).toBeInTheDocument()

    await userEvent.type(editor.getByLabelText('Name'), 'Park')
    expect(editor.getByRole('button', { name: 'Save' })).toBeDisabled()
    await userEvent.click(await screen.findByRole('button', { name: 'click map' }))
    await userEvent.selectOptions(editor.getByLabelText('Surface'), 'SAND')
    fireEvent.change(editor.getByLabelText('Radius (m)'), { target: { value: '200' } })
    await userEvent.click(editor.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Created Park. 3 sessions were classified again.')
    expect(sent(calls, 'POST')?.body).toEqual({ name: 'Park', surface: 'SAND', shape: { type: 'circle', lon: 33.1, lat: 34.7, radiusM: 200 } })
  })

  it('creates a polygon from clicked corners, finished by a double-click', async () => {
    const calls = renderGeozones()
    await userEvent.click(await screen.findByRole('button', { name: 'New polygon' }))
    const editor = within(screen.getByRole('form', { name: 'New polygon' }))
    await userEvent.type(editor.getByLabelText('Name'), 'Courts')
    const click = await screen.findByRole('button', { name: 'click map' })

    await userEvent.click(click)
    await userEvent.click(click)
    expect(editor.getByRole('button', { name: 'Finish' })).toBeDisabled()
    await userEvent.click(click)
    await userEvent.click(click)
    await userEvent.click(editor.getByRole('button', { name: 'Undo corner' }))
    await userEvent.click(screen.getByRole('button', { name: 'double-click map' }))
    expect(editor.getByText('3 corners. Redraw to change them.')).toBeInTheDocument()
    await userEvent.click(editor.getByRole('button', { name: 'Save' }))

    await vi.waitFor(() => expect(sent(calls, 'POST')?.body).toEqual({
      name: 'Courts',
      surface: 'GRASS',
      shape: { type: 'Polygon', coordinates: [[CORNERS[0], CORNERS[1], CORNERS[2], CORNERS[0]]] },
    }))
  })

  it('edits a geozone', async () => {
    const calls = renderGeozones()
    const row = within((await screen.findAllByRole('listitem'))[0])
    await userEvent.click(row.getByRole('button', { name: 'Edit' }))
    const editor = within(screen.getByRole('form', { name: 'Edit circle' }))

    await userEvent.clear(editor.getByLabelText('Name'))
    await userEvent.type(editor.getByLabelText('Name'), 'Main field')
    await userEvent.click(editor.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Saved Main field. 1 session was classified again.')
    expect(sent(calls, 'PATCH')?.body).toEqual({
      name: 'Main field',
      surface: 'GRASS',
      shape: { type: 'circle', lon: 33.12787, lat: 34.70786, radiusM: 150 },
    })
  })

  it('deletes a geozone only after confirmation', async () => {
    const calls = renderGeozones()
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
    const row = within((await screen.findAllByRole('listitem'))[1])

    await userEvent.click(row.getByRole('button', { name: 'Delete' }))
    expect(sent(calls, 'DELETE')).toBeUndefined()
    await userEvent.click(row.getByRole('button', { name: 'Delete' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Deleted Beach courts. No session changed its surface.')
    expect(confirm).toHaveBeenLastCalledWith(expect.stringContaining('Beach courts'))
  })

  it('cancels drawing with Escape', async () => {
    renderGeozones()
    await userEvent.click(await screen.findByRole('button', { name: 'New polygon' }))
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('form')).not.toBeInTheDocument()
  })

  it('starts the map at the latest session without geozones', async () => {
    renderGeozones([])
    expect(await screen.findByText(/No geozones yet/)).toBeInTheDocument()
    expect(await screen.findByText('selected none · start 33.12787,34.70786')).toBeInTheDocument()
  })
})
