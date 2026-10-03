import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { sessionSeries } from '../../test/data'
import { ActivityStrip } from './ActivityStrip'

describe('ActivityStrip', () => {
  it('shows active time, rest and gaps across the view and selects a run on click', async () => {
    const onSelect = vi.fn()
    render(<ActivityStrip series={sessionSeries()} view={[0, 10]} onSelect={onSelect} />)
    const runs = screen.getByLabelText('Activity').children

    expect([...runs].map((run) => [run.className, (run as HTMLElement).style.left, (run as HTMLElement).style.width])).toEqual([
      ['activity-run active', '0%', '60%'],
      ['activity-run rest', '60%', '20%'],
      ['activity-run gap', '80%', '10%'],
      ['activity-run active', '90%', '10%'],
    ])
    expect(runs[1]).toHaveAttribute('title', 'Rest 0:06–0:08 (0:02). Click to select.')

    await userEvent.click(runs[1])
    expect(onSelect).toHaveBeenCalledWith([6, 8])
  })

  it('follows the zoom', () => {
    render(<ActivityStrip series={sessionSeries()} view={[5, 9]} onSelect={() => {}} />)
    const runs = [...screen.getByLabelText('Activity').children] as HTMLElement[]
    expect(runs.map((run) => [run.className, run.style.left])).toEqual([
      ['activity-run active', '-125%'],
      ['activity-run rest', '25%'],
      ['activity-run gap', '75%'],
    ])
  })
})
