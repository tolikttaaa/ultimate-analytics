import { useSyncExternalStore } from 'react'

/*
 * Light and dark themes (backlog): the choice is light, dark or the system's, kept in this browser only. `data-theme`
 * on <html> drives the CSS tokens; index.html sets it before the first paint with the same rules.
 */

export type ThemeChoice = 'light' | 'dark' | 'system'
export type Theme = 'light' | 'dark'

const STORAGE_KEY = 'ua-theme'
const DARK_QUERY = '(prefers-color-scheme: dark)'
const listeners = new Set<() => void>()

/** The stored choice; storage may be blocked (private windows), then the system decides. */
function readChoice(): ThemeChoice {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    return stored === 'light' || stored === 'dark' ? stored : 'system'
  } catch {
    return 'system'
  }
}

let choice: ThemeChoice = readChoice()

function systemTheme(): Theme {
  return typeof window.matchMedia === 'function' && window.matchMedia(DARK_QUERY).matches ? 'dark' : 'light'
}

export function resolveTheme(value: ThemeChoice): Theme {
  return value === 'system' ? systemTheme() : value
}

function apply() {
  document.documentElement.dataset.theme = resolveTheme(choice)
  listeners.forEach((listener) => listener())
}

export function setThemeChoice(value: ThemeChoice) {
  choice = value
  try {
    if (value === 'system') localStorage.removeItem(STORAGE_KEY)
    else localStorage.setItem(STORAGE_KEY, value)
  } catch {
    // Not stored: the choice lasts until the page is reloaded.
  }
  apply()
}

// Following the system: its changes apply at once.
if (typeof window.matchMedia === 'function') {
  window.matchMedia(DARK_QUERY).addEventListener?.('change', () => {
    if (choice === 'system') apply()
  })
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** The theme choice and the theme it resolves to. */
export function useTheme(): { choice: ThemeChoice; theme: Theme } {
  const snapshot = useSyncExternalStore(subscribe, () => `${choice}:${resolveTheme(choice)}`)
  const [current, theme] = snapshot.split(':') as [ThemeChoice, Theme]
  return { choice: current, theme }
}
