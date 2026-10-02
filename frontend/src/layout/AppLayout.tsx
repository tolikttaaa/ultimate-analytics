import { NavLink, Outlet } from 'react-router'
import { useAnalysisParameters } from '../api/analysis'

/** Frame of every screen: navigation on top, the screen below (spec 10.1). */
export function AppLayout() {
  const parameters = useAnalysisParameters()
  return (
    <div className="app">
      <header className="app-header">
        <span className="app-title">Ultimate Analytics</span>
        <nav>
          <NavLink to="/" end>Sessions</NavLink>
          <NavLink to="/drill-types">Drill types</NavLink>
          <NavLink to="/geozones">Geozones</NavLink>
        </nav>
        {parameters.data && <span className="app-version">analysis v{parameters.data.analysisVersion}</span>}
      </header>
      <main className="app-main">
        <Outlet />
      </main>
    </div>
  )
}
