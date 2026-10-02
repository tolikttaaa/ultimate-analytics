import { describe, expect, it, vi } from 'vitest'
import { createCursor } from './cursor'

describe('cursor', () => {
  it('tells its listeners about changes only', () => {
    const cursor = createCursor()
    const listener = vi.fn()
    const unsubscribe = cursor.subscribe(listener)

    cursor.set(10)
    cursor.set(10)
    cursor.set(null)
    unsubscribe()
    cursor.set(11)

    expect(listener).toHaveBeenCalledTimes(2)
    expect(cursor.get()).toBe(11)
  })
})
