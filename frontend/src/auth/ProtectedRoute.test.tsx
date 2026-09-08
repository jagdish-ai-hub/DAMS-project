import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, vi } from 'vitest'
import ProtectedRoute from './ProtectedRoute'
import { useAuth } from './useAuth'

vi.mock('./useAuth')
const mockUseAuth = vi.mocked(useAuth)

/**
 * The only unauthenticated route is /login: every /app screen sits behind
 * ProtectedRoute, which renders on the JWT — never on a client-side role choice.
 */
function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/login" element={<h1>Sign in to DAMS</h1>} />
        <Route
          path="/app"
          element={
            <ProtectedRoute>
              <h1>Cashier home</h1>
            </ProtectedRoute>
          }
        />
      </Routes>
    </MemoryRouter>,
  )
}

describe('ProtectedRoute', () => {
  beforeEach(() => vi.resetAllMocks())

  it('renders the screen when authenticated', () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: true } as ReturnType<typeof useAuth>)
    renderAt('/app')
    expect(screen.getByRole('heading', { name: /cashier home/i })).toBeInTheDocument()
  })

  it('redirects to /login when unauthenticated', () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: false } as ReturnType<typeof useAuth>)
    renderAt('/app')
    expect(screen.getByRole('heading', { name: /sign in to dams/i })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /cashier home/i })).not.toBeInTheDocument()
  })
})
