import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '../auth/AuthContext'
import App from '../App'
import MaintenancePage from './MaintenancePage'
import { BYPASS_TOKEN, bundleOf, isMaintenanceBlocked, maintenanceBypassed } from './maintenance'

beforeEach(() => sessionStorage.clear())

function renderAt(entry: string) {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('isMaintenanceBlocked', () => {
  it('blocks everyone while maintenance is on, unless bypassed', () => {
    expect(isMaintenanceBlocked(true, false)).toBe(true)
    expect(isMaintenanceBlocked(true, true)).toBe(false)
  })

  it('blocks nobody when maintenance is off', () => {
    expect(isMaintenanceBlocked(false, false)).toBe(false)
  })
})

describe('maintenanceBypassed', () => {
  it('is false for a normal visit', () => {
    expect(maintenanceBypassed('')).toBe(false)
    expect(maintenanceBypassed('?bypass=wrong')).toBe(false)
  })

  it('opens the door for the right link, and remembers it for the rest of the tab', () => {
    expect(maintenanceBypassed(`?bypass=${BYPASS_TOKEN}`)).toBe(true)
    expect(maintenanceBypassed('')).toBe(true)
  })
})

describe('bundleOf', () => {
  it('finds the hashed app bundle in an index.html', () => {
    const html = '<script type="module" crossorigin src="/assets/index-Bw5KtGjn.js"></script>'
    expect(bundleOf(html)).toBe('/assets/index-Bw5KtGjn.js')
  })

  it('returns null when there is no hashed bundle (dev server)', () => {
    expect(bundleOf('<script type="module" src="/src/main.tsx"></script>')).toBeNull()
  })
})

describe('maintenance screen in the app', () => {
  it('replaces a role page with the maintenance page', () => {
    renderAt('/app')
    expect(screen.getByRole('heading', { name: /we.re under maintenance/i })).toBeInTheDocument()
  })

  it('replaces the sign-in page too', () => {
    renderAt('/login')
    expect(screen.getByRole('heading', { name: /we.re under maintenance/i })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /sign in to dams/i })).not.toBeInTheDocument()
  })

  it('lets the private bypass link through to the real sign-in page', () => {
    renderAt(`/login?bypass=${BYPASS_TOKEN}`)
    expect(screen.getByRole('heading', { name: /sign in to dams/i })).toBeInTheDocument()
  })

  it('describes the illustration for screen readers and offers a manual re-check', () => {
    render(<MaintenancePage />)
    expect(screen.getByRole('img', { name: /server rack/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /check again/i })).toBeInTheDocument()
  })
})
