import { useState, type FormEvent, type CSSProperties } from 'react'
import { useNavigate, Navigate, useSearchParams } from 'react-router-dom'
import { useAuth } from './useAuth'
import { authApi } from '../api/auth'

/**
 * Login page — a two-panel layout in the mockups' navy visual language. Email + password,
 * no role toggle (AGENT.md: role toggles are demo-only). The "Demo logins" row is a
 * test-phase convenience for the seeded dealership — one click signs in as each role.
 */

const DEMO_LOGINS: { label: string; sub: string; email: string; password: string }[] = [
  { label: 'Owner', sub: 'org-wide', email: 'owner@jjmotors.demo', password: 'owner123' },
  { label: 'Finance Manager', sub: 'approvals & claims', email: 'finance@jjmotors.demo', password: 'finance123' },
  { label: 'Accountant', sub: 'review queue', email: 'accountant@jjmotors.demo', password: 'accountant123' },
  { label: 'Cashier', sub: 'Rayagada branch', email: 'cashier@jjmotors.demo', password: 'cashier123' },
  { label: 'Super Admin', sub: 'platform', email: 'dams@jjsoftware.com', password: 'Welcome12345@' },
]

export default function LoginPage() {
  const { isAuthenticated, login } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState('')

  const notice = searchParams.get('invited') === '1'
    ? 'Password set. Sign in with your email and new password.'
    : searchParams.get('expired') === '1'
      ? 'Your session ended. Please sign in again.'
      : ''

  if (isAuthenticated) {
    return <Navigate to="/app" replace />
  }

  async function signIn(withEmail: string, withPassword: string, tag: string) {
    setError('')
    setLoading(tag)
    try {
      const { data } = await authApi.login({ email: withEmail, password: withPassword })
      login(data.accessToken, data.name)
      navigate('/app', { replace: true })
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        ?? 'Login failed — check your credentials'
      setError(msg)
      setLoading('')
    }
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    signIn(email, password, 'form')
  }

  return (
    <div style={{ minHeight: '100vh', display: 'flex', background: 'var(--bg)' }}>
      {/* ── Brand panel ── */}
      <aside className="dams-login-brand" style={brandPanel}>
        <div style={{ position: 'relative', zIndex: 1 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 40 }}>
            <div style={brandMark}>DA</div>
            <span style={{ fontWeight: 700, fontSize: '1.05rem', letterSpacing: '0.01em' }}>DAMS</span>
          </div>
          <h1 style={{ fontSize: '1.7rem', fontWeight: 700, lineHeight: 1.3, maxWidth: 380 }}>
            Dealer Activity Management System
          </h1>
          <p style={{ fontSize: '0.92rem', opacity: 0.72, marginTop: 12, maxWidth: 360, lineHeight: 1.6 }}>
            The clean, verified, ledger-tagged pipe between your service stations and your accountants —
            receipts, expenses, cash and claims, all maker-checked.
          </p>
          <div style={{ display: 'flex', gap: 8, marginTop: 28, flexWrap: 'wrap' }}>
            {['Receipts', 'Expenses', 'Cash', 'Claims', 'Dashboard'].map((c) => (
              <span key={c} style={brandChip}>{c}</span>
            ))}
          </div>
        </div>
        <div style={{ position: 'relative', zIndex: 1, fontSize: '0.72rem', opacity: 0.5 }}>
          Test phase · seeded demo dealership “JJ Motors”
        </div>
        <div style={brandGlow} />
      </aside>

      {/* ── Form panel ── */}
      <main style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '40px 20px' }}>
        <div style={{ width: '100%', maxWidth: 380 }}>
          <div className="dams-login-compact-brand" style={{ alignItems: 'center', gap: 10, marginBottom: 24 }}>
            <div style={{ ...brandMark, width: 30, height: 30, fontSize: '0.72rem', background: 'var(--navy)' }}>DA</div>
            <span style={{ fontWeight: 700, fontSize: '0.98rem', color: 'var(--navy)' }}>DAMS</span>
          </div>

          <h2 style={{ fontSize: '1.35rem', fontWeight: 700, color: 'var(--navy)' }}>Sign in to DAMS</h2>
          <p style={{ fontSize: '0.85rem', color: 'var(--muted)', marginTop: 4, marginBottom: 22 }}>
            Welcome back. Sign in to continue.
          </p>

          {notice && <div style={noticeBox}>{notice}</div>}

          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <label style={fieldWrap}>
              <span style={fieldLabel}>Email</span>
              <input
                type="email" value={email} onChange={(e) => setEmail(e.target.value)} required
                autoComplete="email" placeholder="you@dealership.com" style={inputStyle}
                onFocus={(e) => (e.target.style.borderColor = 'var(--navy2)')}
                onBlur={(e) => (e.target.style.borderColor = 'var(--line)')}
              />
            </label>
            <label style={fieldWrap}>
              <span style={fieldLabel}>Password</span>
              <input
                type="password" value={password} onChange={(e) => setPassword(e.target.value)} required
                autoComplete="current-password" placeholder="••••••••" style={inputStyle}
                onFocus={(e) => (e.target.style.borderColor = 'var(--navy2)')}
                onBlur={(e) => (e.target.style.borderColor = 'var(--line)')}
              />
            </label>

            {error && <div style={errorBox}>{error}</div>}

            <button type="submit" disabled={loading !== ''} style={{ ...submitBtn, opacity: loading !== '' ? 0.7 : 1, cursor: loading !== '' ? 'not-allowed' : 'pointer' }}>
              {loading === 'form' ? 'Signing in…' : 'Sign in'}
            </button>
          </form>

          <div style={{ display: 'flex', alignItems: 'center', gap: 10, margin: '22px 0 14px' }}>
            <span style={{ flex: 1, height: 1, background: 'var(--line)' }} />
            <span style={{ fontSize: '0.7rem', fontWeight: 700, letterSpacing: '0.06em', color: 'var(--faint)', textTransform: 'uppercase' }}>
              Demo logins
            </span>
            <span style={{ flex: 1, height: 1, background: 'var(--line)' }} />
          </div>

          <div style={{ display: 'grid', gap: 8 }}>
            {DEMO_LOGINS.map((d) => (
              <button
                key={d.email}
                type="button"
                disabled={loading !== ''}
                onClick={() => signIn(d.email, d.password, d.email)}
                style={{ ...demoBtn, opacity: loading !== '' && loading !== d.email ? 0.5 : 1 }}
              >
                <span style={{ fontWeight: 700, color: 'var(--navy2)' }}>{d.label}</span>
                <span style={{ fontSize: '0.74rem', color: 'var(--faint)' }}>{d.sub}</span>
                <span style={{ marginLeft: 'auto', fontSize: '0.74rem', color: 'var(--navy2)', fontWeight: 700 }}>
                  {loading === d.email ? 'Signing in…' : 'Enter →'}
                </span>
              </button>
            ))}
          </div>
        </div>
      </main>
    </div>
  )
}

// ── styles ──

const brandPanel: CSSProperties = {
  width: '42%',
  maxWidth: 520,
  background: 'linear-gradient(150deg, #16264a 0%, var(--navy) 55%, var(--navy2) 130%)',
  color: '#fff',
  padding: '48px 44px',
  display: 'flex',
  flexDirection: 'column',
  justifyContent: 'space-between',
  position: 'relative',
  overflow: 'hidden',
}

const brandGlow: CSSProperties = {
  position: 'absolute',
  right: -140,
  bottom: -160,
  width: 420,
  height: 420,
  borderRadius: '50%',
  background: 'radial-gradient(circle, rgba(255,255,255,0.12) 0%, rgba(255,255,255,0) 70%)',
}

const brandMark: CSSProperties = {
  width: 40, height: 40, borderRadius: 11,
  background: 'rgba(255,255,255,0.14)', border: '1px solid rgba(255,255,255,0.2)',
  display: 'flex', alignItems: 'center', justifyContent: 'center',
  fontWeight: 700, fontSize: '0.9rem',
}

const brandChip: CSSProperties = {
  fontSize: '0.74rem', fontWeight: 600,
  padding: '5px 11px', borderRadius: 999,
  background: 'rgba(255,255,255,0.1)', border: '1px solid rgba(255,255,255,0.18)',
}

const noticeBox: CSSProperties = {
  background: 'var(--blue-bg)', border: '1px solid #C7D6F5', color: 'var(--blue)',
  borderRadius: 8, padding: '10px 12px', fontSize: '0.82rem', marginBottom: 16,
}

const errorBox: CSSProperties = {
  background: 'var(--red-bg)', border: '1px solid #EBC2C2', color: 'var(--red)',
  borderRadius: 8, padding: '10px 12px', fontSize: '0.82rem',
}

const fieldWrap: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 5 }
const fieldLabel: CSSProperties = { fontSize: '0.78rem', fontWeight: 600, color: 'var(--muted)' }

const inputStyle: CSSProperties = {
  border: '1.5px solid var(--line)', borderRadius: 8, padding: '10px 12px',
  fontSize: '0.9rem', outline: 'none', transition: 'border-color .12s',
}

const submitBtn: CSSProperties = {
  background: 'var(--navy)', color: '#fff', border: 'none', borderRadius: 8,
  padding: '11px', fontWeight: 700, fontSize: '0.92rem', marginTop: 4,
}

const demoBtn: CSSProperties = {
  display: 'flex', alignItems: 'center', gap: 8,
  width: '100%', textAlign: 'left',
  border: '1px solid var(--line)', background: 'var(--surface)', borderRadius: 9,
  padding: '9px 12px', fontSize: '0.85rem',
}
