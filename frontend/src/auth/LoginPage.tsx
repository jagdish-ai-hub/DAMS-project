import { useState, FormEvent } from 'react'
import { useNavigate, Navigate, useSearchParams } from 'react-router-dom'
import { useAuth } from './useAuth'
import { authApi } from '../api/auth'

/**
 * Login page — matches the navy topbar visual language from the mockups.
 * Email + password, no role toggle (see AGENT.md: role toggles are demo-only).
 */
export default function LoginPage() {
  const { isAuthenticated, login } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const notice = searchParams.get('invited') === '1'
    ? 'Password set. Sign in with your email and new password.'
    : searchParams.get('expired') === '1'
      ? 'Your session ended. Please sign in again.'
      : ''

  // Already authenticated — go to app
  if (isAuthenticated) {
    return <Navigate to="/app" replace />
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError('')
    setLoading(true)

    try {
      const { data } = await authApi.login({ email, password })
      login(data.accessToken, data.name)
      navigate('/app', { replace: true })
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        ?? 'Login failed — check your credentials'
      setError(msg)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)', display: 'flex', flexDirection: 'column' }}>
      {/* Navy topbar — same visual language as mockups */}
      <div style={{
        background: 'var(--navy)',
        color: '#fff',
        padding: '11px 24px',
        display: 'flex',
        alignItems: 'center',
        gap: '14px',
      }}>
        <div style={{
          width: 30, height: 30, borderRadius: 8,
          background: 'rgba(255,255,255,.14)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontWeight: 700, fontSize: '0.76rem',
        }}>
          DA
        </div>
        <span style={{ fontWeight: 700, fontSize: '0.98rem' }}>DAMS</span>
      </div>

      {/* Login card */}
      <div style={{
        flex: 1,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '40px 16px',
      }}>
        <div style={{
          background: 'var(--surface)',
          border: '1px solid var(--line)',
          borderRadius: 'var(--radius)',
          boxShadow: 'var(--shadow)',
          padding: '36px 32px',
          width: '100%',
          maxWidth: 400,
        }}>
          <h2 style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--navy)', marginBottom: 6 }}>
            Sign in to DAMS
          </h2>
          <p style={{ fontSize: '0.85rem', color: 'var(--muted)', marginBottom: 24 }}>
            Dealer Activity Management System
          </p>

          {notice && (
            <div style={{
              background: 'var(--blue-bg)',
              border: '1px solid #C7D6F5',
              color: 'var(--blue)',
              borderRadius: 8,
              padding: '10px 12px',
              fontSize: '0.82rem',
              marginBottom: 16,
            }}>
              {notice}
            </div>
          )}

          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
              <label style={{ fontSize: '0.78rem', fontWeight: 600, color: 'var(--muted)' }}>
                Email
              </label>
              <input
                type="email"
                value={email}
                onChange={e => setEmail(e.target.value)}
                required
                autoComplete="email"
                placeholder="you@example.com"
                style={{
                  border: '1.5px solid var(--line)',
                  borderRadius: 8,
                  padding: '9px 11px',
                  fontSize: '0.86rem',
                  outline: 'none',
                }}
                onFocus={e => (e.target.style.borderColor = 'var(--navy2)')}
                onBlur={e => (e.target.style.borderColor = 'var(--line)')}
              />
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
              <label style={{ fontSize: '0.78rem', fontWeight: 600, color: 'var(--muted)' }}>
                Password
              </label>
              <input
                type="password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                required
                autoComplete="current-password"
                placeholder="••••••••"
                style={{
                  border: '1.5px solid var(--line)',
                  borderRadius: 8,
                  padding: '9px 11px',
                  fontSize: '0.86rem',
                  outline: 'none',
                }}
                onFocus={e => (e.target.style.borderColor = 'var(--navy2)')}
                onBlur={e => (e.target.style.borderColor = 'var(--line)')}
              />
            </div>

            {error && (
              <div style={{
                background: 'var(--red-bg)',
                border: '1px solid #EBC2C2',
                color: 'var(--red)',
                borderRadius: 8,
                padding: '10px 12px',
                fontSize: '0.82rem',
              }}>
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              style={{
                background: loading ? 'var(--navy2)' : 'var(--navy)',
                color: '#fff',
                border: 'none',
                borderRadius: 8,
                padding: '11px',
                fontWeight: 700,
                fontSize: '0.9rem',
                cursor: loading ? 'not-allowed' : 'pointer',
                marginTop: 4,
              }}
            >
              {loading ? 'Signing in…' : 'Sign in'}
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}
