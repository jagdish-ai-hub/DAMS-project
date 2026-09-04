import { useState, FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { authApi } from '../api/auth'

/**
 * Accept invite page — reads ?token= from the URL query param.
 * On success, redirects to /login with a success message.
 */
export default function AcceptInvitePage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()

  const token = searchParams.get('token') ?? ''

  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError('')

    if (password !== confirmPassword) {
      setError('Passwords do not match')
      return
    }

    if (password.length < 8) {
      setError('Password must be at least 8 characters')
      return
    }

    if (!token) {
      setError('Invite token is missing from the URL')
      return
    }

    setLoading(true)

    try {
      await authApi.acceptInvite({ token, password })
      // Redirect to login — the user now has a password and can sign in
      navigate('/login?invited=1', { replace: true })
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        ?? 'Failed to accept invite — the token may have expired'
      setError(msg)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)', display: 'flex', flexDirection: 'column' }}>
      {/* Navy topbar */}
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
          padding: '32px clamp(16px, 5vw, 32px)',
          width: '100%',
          maxWidth: 'min(400px, 100%)',
        }}>
          <h2 style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--navy)', marginBottom: 6 }}>
            Set your password
          </h2>
          <p style={{ fontSize: '0.85rem', color: 'var(--muted)', marginBottom: 24 }}>
            You've been invited to DAMS. Set a password to activate your account.
          </p>

          {!token && (
            <div style={{
              background: 'var(--red-bg)',
              border: '1px solid #EBC2C2',
              color: 'var(--red)',
              borderRadius: 8,
              padding: '10px 12px',
              fontSize: '0.82rem',
              marginBottom: 16,
            }}>
              No invite token found in the URL. Check the link you received.
            </div>
          )}

          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
              <label style={{ fontSize: '0.78rem', fontWeight: 600, color: 'var(--muted)' }}>
                New Password
              </label>
              <input
                type="password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                required
                minLength={8}
                autoComplete="new-password"
                placeholder="At least 8 characters"
                style={{
                  border: '1.5px solid var(--line)',
                  borderRadius: 8,
                  padding: '9px 11px',
                  fontSize: '0.86rem',
                  outline: 'none',
                  minHeight: 40,
                }}
                onFocus={e => (e.target.style.borderColor = 'var(--navy2)')}
                onBlur={e => (e.target.style.borderColor = 'var(--line)')}
              />
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
              <label style={{ fontSize: '0.78rem', fontWeight: 600, color: 'var(--muted)' }}>
                Confirm Password
              </label>
              <input
                type="password"
                value={confirmPassword}
                onChange={e => setConfirmPassword(e.target.value)}
                required
                autoComplete="new-password"
                placeholder="Repeat your password"
                style={{
                  border: '1.5px solid var(--line)',
                  borderRadius: 8,
                  padding: '9px 11px',
                  fontSize: '0.86rem',
                  outline: 'none',
                  minHeight: 40,
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
              disabled={loading || !token}
              style={{
                background: 'var(--navy)',
                color: '#fff',
                border: 'none',
                borderRadius: 8,
                padding: '11px',
                fontWeight: 700,
                fontSize: '0.9rem',
                minHeight: 42,
                cursor: loading || !token ? 'not-allowed' : 'pointer',
                marginTop: 4,
                opacity: loading || !token ? 0.7 : 1,
              }}
            >
              {loading ? 'Setting password…' : 'Activate account'}
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}
