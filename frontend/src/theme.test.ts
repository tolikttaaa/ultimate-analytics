import { act, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { resolveTheme, setThemeChoice, useTheme } from './theme'

describe('theme', () => {
  afterEach(() => {
    setThemeChoice('system')
    vi.restoreAllMocks()
  })

  it('follows the system until a theme is chosen, and keeps the choice', () => {
    const { result } = renderHook(() => useTheme())
    expect(result.current).toEqual({ choice: 'system', theme: 'light' })

    act(() => setThemeChoice('dark'))
    expect(result.current).toEqual({ choice: 'dark', theme: 'dark' })
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(localStorage.getItem('ua-theme')).toBe('dark')

    act(() => setThemeChoice('system'))
    expect(document.documentElement.dataset.theme).toBe('light')
    expect(localStorage.getItem('ua-theme')).toBeNull()
  })

  it('resolves the system choice through the media query', () => {
    vi.stubGlobal('matchMedia', (query: string) => ({ matches: query === '(prefers-color-scheme: dark)', addEventListener: () => {} }))
    expect(resolveTheme('system')).toBe('dark')
    expect(resolveTheme('light')).toBe('light')
    vi.unstubAllGlobals()
    vi.stubGlobal('matchMedia', (query: string) => ({ matches: false, media: query, addEventListener: () => {} }))
  })

  it('still switches when storage is blocked', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    act(() => setThemeChoice('dark'))
    expect(document.documentElement.dataset.theme).toBe('dark')
  })
})
