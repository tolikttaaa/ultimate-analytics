import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { drillType } from '../../test/data'
import { fakeApi, problem } from '../../test/fakeApi'
import { renderPage } from '../../test/render'
import { PALETTE } from './drillTypeFormat'
import { DrillTypesPage } from './DrillTypesPage'

const sprints = drillType()
const game = drillType({ id: 'd2', code: 'GAME', name: 'Game', kind: 'GAME', color: '#00897b' })

function renderList(handlers: Parameters<typeof fakeApi>[0] = {}) {
  const calls = fakeApi({
    'GET /api/drill-types': () => [game, sprints],
    'POST /api/drill-types': () => drillType({ id: 'new' }),
    'PATCH /api/drill-types/d1': () => sprints,
    'DELETE /api/drill-types/d2': () => new Response(null, { status: 204 }),
    ...handlers,
  })
  renderPage(<DrillTypesPage />)
  return calls
}

const sent = (calls: ReturnType<typeof fakeApi>, method: string) => calls.find((call) => call.method === method)

describe('DrillTypesPage', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('lists the drill types with kind and code, linking to their statistics', async () => {
    renderList()
    const rows = (await screen.findAllByRole('row')).slice(1)
    expect(rows.map((row) => row.textContent)).toEqual([expect.stringMatching(/^GameGameGAME/), expect.stringMatching(/^SprintsDrillSPRINTS/)])
    expect(within(rows[1]).getByRole('link', { name: 'Sprints' })).toHaveAttribute('href', '/drill-types/d1')
  })

  it('creates a drill type, suggesting the code from the name', async () => {
    const calls = renderList()
    await userEvent.click(await screen.findByRole('button', { name: 'New drill type' }))
    const form = within(screen.getByRole('form', { name: 'New drill type' }))

    await userEvent.type(form.getByLabelText('Name'), 'Cutting 1v1')
    expect(form.getByLabelText('Code')).toHaveValue('CUTTING_1V1')
    await userEvent.selectOptions(form.getByLabelText('Kind'), 'GAME')
    await userEvent.click(form.getByRole('button', { name: `Colour ${PALETTE[3]}` }))
    await userEvent.click(form.getByRole('button', { name: 'Save' }))

    await vi.waitFor(() => expect(screen.queryByRole('form')).not.toBeInTheDocument())
    expect(sent(calls, 'POST')?.body).toEqual({ name: 'Cutting 1v1', code: 'CUTTING_1V1', kind: 'GAME', color: PALETTE[3] })
  })

  it('keeps a typed code and explains a taken one', async () => {
    renderList({ 'POST /api/drill-types': () => problem(409, 'A drill type with code GAME already exists') })
    await userEvent.click(await screen.findByRole('button', { name: 'New drill type' }))
    const form = within(screen.getByRole('form', { name: 'New drill type' }))

    await userEvent.type(form.getByLabelText('Code'), 'game')
    await userEvent.type(form.getByLabelText('Name'), 'Scrimmage')
    expect(form.getByLabelText('Code')).toHaveValue('GAME')
    await userEvent.click(form.getByRole('button', { name: 'Save' }))

    expect(await form.findByRole('alert')).toHaveTextContent('A drill type with code GAME already exists')
  })

  it('edits a drill type', async () => {
    const calls = renderList()
    const row = (await screen.findAllByRole('row'))[2]
    await userEvent.click(within(row).getByRole('button', { name: 'Edit' }))
    const form = within(screen.getByRole('form', { name: 'Edit Sprints' }))

    await userEvent.clear(form.getByLabelText('Name'))
    await userEvent.type(form.getByLabelText('Name'), 'Sprint ladder')
    expect(form.getByLabelText('Code')).toHaveValue('SPRINTS')
    await userEvent.click(form.getByRole('button', { name: 'Save' }))

    await vi.waitFor(() => expect(sent(calls, 'PATCH')?.body).toEqual({ name: 'Sprint ladder', code: 'SPRINTS', kind: 'DRILL', color: '#e65100' }))
  })

  it('deletes a drill type only after confirmation', async () => {
    const calls = renderList()
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
    const row = (await screen.findAllByRole('row'))[1]

    await userEvent.click(within(row).getByRole('button', { name: 'Delete' }))
    expect(sent(calls, 'DELETE')).toBeUndefined()
    await userEvent.click(within(row).getByRole('button', { name: 'Delete' }))

    await vi.waitFor(() => expect(sent(calls, 'DELETE')?.path).toBe('/api/drill-types/d2'))
    expect(confirm).toHaveBeenLastCalledWith('Delete the drill type Game? Its segments stay, without a type.')
  })
})
