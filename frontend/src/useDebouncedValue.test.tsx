import { act, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useDebouncedValue } from './useDebouncedValue'

describe('useDebouncedValue', () => {
  afterEach(() => vi.useRealTimers())

  it('passes a value on once it stayed for the delay', () => {
    vi.useFakeTimers()
    const { result, rerender } = renderHook(({ value }) => useDebouncedValue(value, 200), { initialProps: { value: 'a' } })

    rerender({ value: 'b' })
    act(() => vi.advanceTimersByTime(150))
    rerender({ value: 'c' })
    act(() => vi.advanceTimersByTime(150))
    expect(result.current).toBe('a')

    act(() => vi.advanceTimersByTime(50))
    expect(result.current).toBe('c')
  })
})
