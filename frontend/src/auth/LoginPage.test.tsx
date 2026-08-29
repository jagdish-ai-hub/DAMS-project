import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from './AuthContext'
import LoginPage from './LoginPage'

function renderLogin(initialEntry = '/login') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <AuthProvider>
        <LoginPage />
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('LoginPage', () => {
  it('renders the sign-in form', () => {
    renderLogin()
    expect(screen.getByRole('heading', { name: /sign in to dams/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument()
  })

  it('shows the session-ended notice when redirected with ?expired=1', () => {
    renderLogin('/login?expired=1')
    expect(screen.getByText(/your session ended/i)).toBeInTheDocument()
  })
})
