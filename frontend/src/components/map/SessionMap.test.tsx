import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { effort, segment, sessionSeries, uiConfig } from '../../test/data'
import { createCursor } from '../../pages/session/cursor'
import { SessionMap } from './SessionMap'

// jsdom has no WebGL: the map renders only its controls.
vi.mock('./MapView', () => ({ MapView: ({ controls }: { controls?: ReactNode }) => <div>{controls}</div> }))

function renderMap(timeWindow: [number, number] | null) {
  render(
    <SessionMap
      config={uiConfig().map}
      series={sessionSeries()}
      efforts={[effort()]}
      segments={[segment()]}
      drillTypes={[]}
      timeWindow={timeWindow}
      geozone={null}
      cursor={createCursor()}
    />,
  )
}

describe('SessionMap', () => {
  it('offers the window-only view while a window is selected', async () => {
    renderMap([2, 5])
    const whole = screen.getByRole('button', { name: 'Whole track' })
    const windowOnly = screen.getByRole('button', { name: 'Window only' })
    expect(whole).toHaveAttribute('aria-pressed', 'true')

    await userEvent.click(windowOnly)
    expect(windowOnly).toHaveAttribute('aria-pressed', 'true')
    expect(whole).toHaveAttribute('aria-pressed', 'false')
  })

  it('has nothing to toggle without a window', () => {
    renderMap(null)
    expect(screen.queryByRole('group', { name: 'Track shown' })).not.toBeInTheDocument()
  })
})
