import { NavLink, Outlet } from 'react-router'
import { useAnalysisParameters } from '../api/analysis'
import { setThemeChoice, type ThemeChoice, useTheme } from '../theme'

/** A flying disc seen from the side. */
function DiscMark() {
  return (
    <svg width="26" height="26" viewBox="0 0 26 26" aria-hidden="true">
      <ellipse cx="13" cy="14" rx="11.5" ry="6" fill="var(--accent)" />
      <ellipse cx="13" cy="12.6" rx="7.5" ry="3.2" fill="none" stroke="var(--accent-ink)" strokeWidth="1.4" opacity="0.7" />
    </svg>
  )
}

const THEME_LABELS: Record<ThemeChoice, string> = { light: 'Light', dark: 'Dark', system: 'System' }

/** Light, dark or as the system says. */
function ThemeSwitch() {
  const { choice } = useTheme()
  return (
    <div className="theme-switch" role="group" aria-label="Theme">
      {(['light', 'dark', 'system'] as const).map((value) => (
        <button key={value} aria-pressed={choice === value} onClick={() => setThemeChoice(value)}>
          {THEME_LABELS[value]}
        </button>
      ))}
    </div>
  )
}

/** Frame of every screen: navigation on top, the screen below (spec 10.1). */
export function AppLayout() {
  const parameters = useAnalysisParameters()
  return (
    <div className="app">
      <header className="app-header">
        <span className="app-title"><DiscMark />Ultimate Analytics</span>
        <nav>
          <NavLink to="/" end>Sessions</NavLink>
          <NavLink to="/drill-types">Drill types</NavLink>
          <NavLink to="/geozones">Geozones</NavLink>
        </nav>
        <div className="app-header-end">
          <ThemeSwitch />
          {parameters.data && <span className="app-version">Analysis v{parameters.data.analysisVersion}</span>}
        </div>
      </header>
      <main className="app-main">
        <Outlet />
      </main>
    </div>
  )
}
