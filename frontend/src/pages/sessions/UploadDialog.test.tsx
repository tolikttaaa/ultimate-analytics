import { fireEvent, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { UploadResult } from '../../api/types'
import { sessionDetail } from '../../test/data'
import { fakeApi } from '../../test/fakeApi'
import { renderPage } from '../../test/render'
import { UploadDialog } from './UploadDialog'

function chooseFiles(...names: string[]) {
  const input = document.querySelector<HTMLInputElement>('input[type=file]')!
  fireEvent.change(input, { target: { files: names.map((name) => new File(['fit'], name)) } })
}

const unknownPlace: UploadResult = { fileName: 'a.fit', status: 'CREATED', sessionId: 's1', surface: 'UNKNOWN', needsSurface: true }

describe('UploadDialog', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('uploads the chosen files and shows a result per FIT file', async () => {
    const calls = fakeApi({
      'POST /api/sessions/upload': () => [
        { fileName: 'export.zip/a.fit', status: 'CREATED', sessionId: 's1', surface: 'GRASS', needsSurface: false },
        { fileName: 'b.fit', status: 'DUPLICATE', sessionId: 's2', needsSurface: false },
        { fileName: 'notes.txt', status: 'FAILED', needsSurface: false, error: 'Not a FIT file' },
      ],
    })
    renderPage(<UploadDialog onClose={() => {}} />)

    chooseFiles('export.zip', 'b.fit', 'notes.txt')

    const rows = await screen.findAllByRole('listitem')
    expect(rows.map((row) => row.textContent)).toEqual([
      expect.stringContaining('export.zip/a.fitcreated'),
      expect.stringContaining('b.fitalready uploaded'),
      expect.stringContaining('notes.txtfailedNot a FIT file'),
    ])
    expect(within(rows[1]).getByRole('link', { name: 'Open' })).toHaveAttribute('href', '/sessions/s2')
    const form = calls[0].body as FormData
    expect(form.getAll('files').map((file) => (file as File).name)).toEqual(['export.zip', 'b.fit', 'notes.txt'])
  })

  it('sets the surface of a session at an unknown place', async () => {
    let surface = 'UNKNOWN'
    const calls = fakeApi({
      'POST /api/sessions/upload': () => [unknownPlace],
      'GET /api/sessions/s1': () => sessionDetail({ surface: surface as never, surfaceSource: surface === 'UNKNOWN' ? 'NONE' : 'MANUAL' }),
      'PATCH /api/sessions/s1': (call) => {
        surface = (call.body as { surface: string }).surface
        return sessionDetail({ surface: surface as never, surfaceSource: 'MANUAL' })
      },
    })
    renderPage(<UploadDialog onClose={() => {}} />)
    chooseFiles('a.fit')

    await userEvent.click(await screen.findByRole('button', { name: 'Sand' }))

    expect(await screen.findByText('Sand', { selector: '.chip' })).toBeInTheDocument()
    expect(calls.find((call) => call.method === 'PATCH')?.body).toEqual({ surface: 'SAND' })
  })

  it('creates a geozone around the start of the session', async () => {
    let matched = false
    const calls = fakeApi({
      'POST /api/sessions/upload': () => [unknownPlace],
      'GET /api/sessions/s1': () => matched
        ? sessionDetail({ surface: 'SAND', surfaceSource: 'GEOZONE', geozoneName: 'Beach courts' })
        : sessionDetail(),
      'POST /api/geozones': () => {
        matched = true
        return { geozone: null, affectedSessionCount: 1 }
      },
    })
    renderPage(<UploadDialog onClose={() => {}} />)
    chooseFiles('a.fit')

    await userEvent.click(await screen.findByRole('button', { name: 'Create geozone from this session' }))
    await userEvent.clear(screen.getByLabelText('Name'))
    await userEvent.type(screen.getByLabelText('Name'), 'Beach courts')
    await userEvent.selectOptions(screen.getByLabelText('Surface'), 'SAND')
    fireEvent.change(screen.getByLabelText('Radius (m)'), { target: { value: '200' } })
    await userEvent.click(screen.getByRole('button', { name: 'Create' }))

    expect(await screen.findByText('Beach courts')).toBeInTheDocument()
    expect(calls.find((call) => call.path === '/api/geozones')?.body).toEqual({
      name: 'Beach courts',
      surface: 'SAND',
      shape: { type: 'circle', lat: 34.70786, lon: 33.12787, radiusM: 200 },
    })
  })
})
