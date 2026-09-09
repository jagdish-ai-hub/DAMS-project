import { useAuth } from '../auth/useAuth'
import type { Role } from '../auth/AuthContext'

const NEXT_STAGE: Record<Exclude<Role, 'SUPER_ADMIN'>, string> = {
  OWNER: 'Your dashboard arrives in Stage 9. Team & Branches, Masters and the Override Audit are in the top nav.',
  FINANCE_MANAGER: 'Your approvals & claims queue is your home screen.',
  ACCOUNTANT: 'Your review queue is your home screen.',
  CASHIER: 'Customer search, receipts, expenses, and the cash page arrive in Stages 3–6.',
}

/**
 * Placeholder landing for non-Super-Admin roles until their screens are built.
 * The empty shell is itself the Stage 1 deliverable — a logged-in, role-aware frame.
 */
export default function RoleLanding() {
  const { user } = useAuth()
  if (!user) return null

  const message =
    user.role === 'SUPER_ADMIN' ? '' : NEXT_STAGE[user.role]

  return (
    <div style={{
      background: 'var(--surface)',
      border: '1px solid var(--line)',
      borderRadius: 'var(--radius)',
      boxShadow: 'var(--shadow)',
      padding: '32px',
      maxWidth: 640,
    }}>
      <h1 style={{ fontSize: '1.15rem', fontWeight: 700, color: 'var(--navy)', marginBottom: 8 }}>
        Welcome, {user.name.split(' ')[0] || 'there'}.
      </h1>
      <p style={{ fontSize: '0.88rem', color: 'var(--muted)', lineHeight: 1.55 }}>
        You're signed in to DAMS. {message}
      </p>
    </div>
  )
}
