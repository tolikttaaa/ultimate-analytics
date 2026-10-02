import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { drillType, segment } from '../../test/data'
import { fakeApi, problem } from '../../test/fakeApi'
import { renderPage } from '../../test/render'
import { WindowBar } from './WindowBar'

function renderBar(response: () => unknown) {
  const calls = fakeApi({ 'POST /api/sessions/s1/segments': response })
  const onClear = vi.fn()
  renderPage(
    <WindowBar
      sessionId="s1"
      selection={[600, 1200]}
      segments={[segment({ id: 'x', startT: 1000, endT: 1500, label: 'Game' })]}
      drillTypes={[drillType()]}
      onClear={onClear}
    />,
  )
  return { calls, onClear }
}

describe('WindowBar', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('invites to select a window when there is none', () => {
    fakeApi({})
    renderPage(<WindowBar sessionId="s1" selection={null} segments={[]} drillTypes={[]} onClear={() => {}} />)
    expect(screen.getByText(/Drag across a chart/)).toBeInTheDocument()
  })

  it('saves the window as a segment with drill type and label', async () => {
    const { calls } = renderBar(() => segment())
    expect(screen.getByText('10:00–20:00')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Save as segment' }))
    await userEvent.selectOptions(screen.getByLabelText('Drill type'), 'd1')
    await userEvent.type(screen.getByLabelText('Label'), ' 5 x 30 m ')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    await vi.waitFor(() => expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument())
    expect(calls[0].body).toEqual({ startT: 600, endT: 1200, drillTypeId: 'd1', label: '5 x 30 m' })
  })

  it('explains an overlap with the segments in the way', async () => {
    renderBar(() => problem(409, 'The segment overlaps segment x [1000, 1500]'))

    await userEvent.click(screen.getByRole('button', { name: 'Save as segment' }))
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('The window overlaps Game (16:40–25:00).')
  })

  it('clears the window', async () => {
    const { onClear } = renderBar(() => segment())
    await userEvent.click(screen.getByRole('button', { name: 'Clear' }))
    expect(onClear).toHaveBeenCalled()
  })
})
