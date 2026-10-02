import { fireEvent, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { drillType, segment } from '../../test/data'
import { fakeApi, problem } from '../../test/fakeApi'
import { renderPage } from '../../test/render'
import { SegmentStrip } from './SegmentStrip'

// The strip is 1000 px wide from x = 0 and shows t = 0..1000, so x is t.
const warmUp = segment({ id: 'w', startT: 0, endT: 300, drillTypeId: 'd1', label: 'Warm-up' })
const game = segment({ id: 'g', startT: 500, endT: 1000, drillTypeId: null, label: null })

function renderStrip(handlers: Parameters<typeof fakeApi>[0] = {}) {
  const calls = fakeApi({
    'PATCH /api/sessions/s1/segments/w': () => warmUp,
    'POST /api/sessions/s1/segments/w/split': () => [warmUp, warmUp],
    'POST /api/sessions/s1/segments/merge': () => warmUp,
    'DELETE /api/sessions/s1/segments/w': () => new Response(null, { status: 204 }),
    'POST /api/sessions/s1/segments/reset-from-laps': () => [],
    ...handlers,
  })
  const onSelect = vi.fn()
  renderPage(
    <SegmentStrip
      sessionId="s1"
      segments={[warmUp, game]}
      drillTypes={[drillType()]}
      view={[0, 1000]}
      lastT={1000}
      selection={null}
      onSelect={onSelect}
    />,
  )
  return { calls, onSelect }
}

function openMenu(atT: number) {
  fireEvent.contextMenu(screen.getByLabelText('Segments'), { clientX: atT, clientY: 10 })
  return within(screen.getByRole('menu'))
}

describe('SegmentStrip', () => {
  beforeEach(() => {
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue(
      { left: 0, width: 1000, top: 0, height: 26, right: 1000, bottom: 26, x: 0, y: 0, toJSON: () => ({}) },
    )
  })
  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('selects the range of a clicked segment', async () => {
    const { onSelect } = renderStrip()
    await userEvent.click(screen.getByText('Warm-up'))
    expect(onSelect).toHaveBeenCalledWith([0, 300])
  })

  it('resizes a segment by its edge, up to the next segment', async () => {
    const { calls } = renderStrip()
    const edge = screen.getByLabelText('Move end of Warm-up')

    fireEvent.mouseDown(edge, { button: 0, clientX: 300 })
    fireEvent.mouseMove(window, { clientX: 420 })
    expect(screen.getByText('Warm-up').closest('.segment')).toHaveStyle({ width: '42%' })
    fireEvent.mouseMove(window, { clientX: 640 })
    fireEvent.mouseUp(window)

    await vi.waitFor(() => expect(calls.find((call) => call.method === 'PATCH')?.body).toEqual({ endT: 500 }))
  })

  it('shows why a change failed', async () => {
    renderStrip({ 'PATCH /api/sessions/s1/segments/w': () => problem(409, 'The segment overlaps segment g [500, 1000]') })

    fireEvent.mouseDown(screen.getByLabelText('Move end of Warm-up'), { button: 0, clientX: 300 })
    fireEvent.mouseMove(window, { clientX: 400 })
    fireEvent.mouseUp(window)

    expect(await screen.findByRole('alert')).toHaveTextContent('overlaps')
  })

  it('splits at the clicked time and merges with the next segment', async () => {
    const { calls } = renderStrip()

    await userEvent.click(openMenu(150).getByRole('menuitem', { name: 'Split at 2:30' }))
    await userEvent.click(openMenu(150).getByRole('menuitem', { name: 'Merge with next' }))

    await vi.waitFor(() => expect(calls).toHaveLength(2))
    expect(calls.map((call) => [call.method, call.path, call.body])).toEqual([
      ['POST', '/api/sessions/s1/segments/w/split', { atT: 150 }],
      ['POST', '/api/sessions/s1/segments/merge', { segmentIds: ['w', 'g'] }],
    ])
  })

  it('changes the drill type and deletes', async () => {
    const { calls } = renderStrip()
    const menu = openMenu(100)
    expect(menu.getByRole('menuitemradio', { name: 'Sprints' })).toHaveAttribute('aria-checked', 'true')

    await userEvent.click(menu.getByRole('menuitemradio', { name: 'No type' }))
    await userEvent.click(openMenu(100).getByRole('menuitem', { name: 'Delete' }))

    await vi.waitFor(() => expect(calls).toHaveLength(2))
    expect(calls.map((call) => [call.method, call.path, call.body])).toEqual([
      ['PATCH', '/api/sessions/s1/segments/w', { drillTypeId: null }],
      ['DELETE', '/api/sessions/s1/segments/w', undefined],
    ])
  })

  it('offers no split at an edge and only reset outside segments', () => {
    renderStrip()
    expect(openMenu(0).getByRole('menuitem', { name: 'Split at 0:00' })).toBeDisabled()
    fireEvent.keyDown(window, { key: 'Escape' })
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()

    expect(openMenu(400).getAllByRole('menuitem').map((item) => item.textContent)).toEqual(['Reset from laps…'])
  })

  it('resets from laps only after confirmation', async () => {
    const { calls } = renderStrip()
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)

    await userEvent.click(openMenu(400).getByRole('menuitem', { name: 'Reset from laps…' }))
    expect(calls).toHaveLength(0)
    await userEvent.click(openMenu(400).getByRole('menuitem', { name: 'Reset from laps…' }))

    await vi.waitFor(() => expect(calls).toHaveLength(1))
    expect(confirm).toHaveBeenCalledTimes(2)
    expect(calls[0].path).toBe('/api/sessions/s1/segments/reset-from-laps')
    expect(calls[0].query.get('confirm')).toBe('true')
  })
})
