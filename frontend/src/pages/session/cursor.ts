/**
 * The time under the charts' axis pointer, shared with the map without re-rendering the session screen on every
 * mouse move.
 */
export interface Cursor {
  get(): number | null
  set(t: number | null): void
  subscribe(listener: () => void): () => void
}

export function createCursor(): Cursor {
  let current: number | null = null
  const listeners = new Set<() => void>()
  return {
    get: () => current,
    set(t) {
      if (t === current) return
      current = t
      listeners.forEach((listener) => listener())
    },
    subscribe(listener) {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
  }
}
