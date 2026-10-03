import { fireEvent, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { startOfDay, endOfDay } from '../../dates'
import { sessionPage, sessionSummary } from '../../test/data'
import { fakeApi } from '../../test/fakeApi'
import { renderPage } from '../../test/render'
import { SessionsPage } from './SessionsPage'

describe('SessionsPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('lists the sessions with display units, surfaces and badges', async () => {
    fakeApi({
      'GET /api/sessions': () => sessionPage([
        sessionSummary({ geozoneName: 'Field', surface: 'GRASS', surfaceSource: 'GEOZONE', recordingMode: 'SMART' }),
        sessionSummary({ id: 's2', startTime: '2026-09-30T16:07:47Z', localTzOffsetSec: 10800, outdated: true, maxSpeed: null }),
      ]),
    })

    renderPage(<SessionsPage />)

    const rows = await screen.findAllByRole('row')
    const first = within(rows[1])
    expect(first.getByRole('link', { name: '2026-03-25 15:39' })).toHaveAttribute('href', '/sessions/s1')
    for (const text of ['Field', 'Grass', '2:10:00', '30:57', '26', '27.4 km/h', '4:16 /km', 'Smart recording']) {
      expect(first.getByText(text)).toBeInTheDocument()
    }
    const second = within(rows[2])
    expect(second.getByText('2026-09-30 19:07')).toBeInTheDocument()
    expect(second.getByText('Unknown')).toBeInTheDocument()
    expect(second.getByText('outdated')).toBeInTheDocument()
    expect(second.getByText('–', { selector: 'td' })).toBeInTheDocument()
  })

  it('filters by surface and date range through the URL', async () => {
    const calls = fakeApi({ 'GET /api/sessions': () => sessionPage([sessionSummary()]) })
    const router = renderPage(<SessionsPage />, '/?page=2')
    await screen.findAllByRole('row')

    await userEvent.selectOptions(screen.getByLabelText('Surface'), 'SAND')
    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-09-01' } })
    fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-09-30' } })

    await vi.waitFor(() => expect(calls.at(-1)?.query.get('to')).toBe(endOfDay('2026-09-30')))
    const query = calls.at(-1)!.query
    expect(query.get('surface')).toBe('SAND')
    expect(query.get('from')).toBe(startOfDay('2026-09-01'))
    expect(query.get('page')).toBe('0')
    expect(router.state.location.search).toBe('?surface=SAND&from=2026-09-01&to=2026-09-30')
  })

  it('pages through the sessions', async () => {
    const calls = fakeApi({
      'GET /api/sessions': (call) => sessionPage([sessionSummary()], { page: Number(call.query.get('page')), totalPages: 2, totalItems: 21 }),
    })
    renderPage(<SessionsPage />)

    expect(await screen.findByText('Page 1 of 2 · 21 sessions')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Next' }))

    expect(await screen.findByText('Page 2 of 2 · 21 sessions')).toBeInTheDocument()
    expect(calls.at(-1)?.query.get('page')).toBe('1')
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
  })

  it('invites to upload when there are no sessions', async () => {
    fakeApi({ 'GET /api/sessions': () => sessionPage([]) })
    renderPage(<SessionsPage />)

    expect(await screen.findByText(/No sessions yet/)).toBeInTheDocument()
    await userEvent.click(screen.getAllByRole('button', { name: 'Upload FIT files' })[1])

    expect(screen.getByRole('heading', { name: 'Upload trainings' })).toBeInTheDocument()
  })

  it('deletes the selected sessions after confirmation', async () => {
    const calls = fakeApi({
      'GET /api/sessions': () => sessionPage([sessionSummary(), sessionSummary({ id: 's2' }), sessionSummary({ id: 's3' })]),
      'DELETE /api/sessions/s1': () => new Response(null, { status: 204 }),
      'DELETE /api/sessions/s3': () => new Response(null, { status: 204 }),
    })
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
    renderPage(<SessionsPage />)
    const boxes = await screen.findAllByRole('checkbox', { name: /^Select session/ })

    await userEvent.click(boxes[0])
    await userEvent.click(boxes[2])
    expect(screen.getByRole('region', { name: 'Selection' })).toHaveTextContent('2 selected')
    await userEvent.click(screen.getByRole('button', { name: 'Delete selected' }))
    expect(calls.filter((call) => call.method === 'DELETE')).toHaveLength(0)
    await userEvent.click(screen.getByRole('button', { name: 'Delete selected' }))

    await vi.waitFor(() => expect(calls.filter((call) => call.method === 'DELETE').map((call) => call.path))
      .toEqual(['/api/sessions/s1', '/api/sessions/s3']))
    expect(confirm).toHaveBeenLastCalledWith(expect.stringContaining('Delete 2 sessions?'))
    await vi.waitFor(() => expect(screen.queryByRole('region', { name: 'Selection' })).not.toBeInTheDocument())
    vi.restoreAllMocks()
  })

  it('selects every session of the page at once', async () => {
    fakeApi({ 'GET /api/sessions': () => sessionPage([sessionSummary(), sessionSummary({ id: 's2' })]) })
    renderPage(<SessionsPage />)

    await userEvent.click(await screen.findByRole('checkbox', { name: 'Select all sessions on this page' }))
    expect(screen.getByRole('region', { name: 'Selection' })).toHaveTextContent('2 selected')
    expect(screen.getAllByRole('checkbox', { name: /^Select session/ }).every((box) => (box as HTMLInputElement).checked)).toBe(true)
  })

  it('says which sessions could not be deleted', async () => {
    fakeApi({
      'GET /api/sessions': () => sessionPage([sessionSummary()]),
      'DELETE /api/sessions/s1': () => new Response(JSON.stringify({ status: 500 }), { status: 500 }),
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderPage(<SessionsPage />)

    await userEvent.click((await screen.findAllByRole('checkbox', { name: /^Select session/ }))[0])
    await userEvent.click(screen.getByRole('button', { name: 'Delete selected' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('1 of 1 sessions could not be deleted.')
    vi.restoreAllMocks()
  })
})
