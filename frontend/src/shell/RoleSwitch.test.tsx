import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { AuthProvider } from '../auth/AuthContext'
import { ACCESS_TOKEN_KEY, SESSION_EXTRAS_KEY, USER_NAME_KEY } from '../auth/session'
import { actorLabel } from '../auth/roleLabels'
import ActingRoleBanner from './ActingRoleBanner'
import RoleSwitchButton from './RoleSwitchButton'
import AccountMenu from './AccountMenu'

/** An unsigned JWT — the client only decodes it; the server is what verifies. */
function token(claims: Record<string, unknown>): string {
  const b64 = (o: unknown) => btoa(JSON.stringify(o)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_')
  return `${b64({ alg: 'none' })}.${b64({ sub: '7', orgId: 1, exp: Math.floor(Date.now() / 1000) + 3600, ...claims })}.x`
}

function signIn(claims: Record<string, unknown>, extras: Record<string, unknown> = {}) {
  sessionStorage.setItem(ACCESS_TOKEN_KEY, token(claims))
  sessionStorage.setItem(USER_NAME_KEY, 'Ajay Kumar')
  sessionStorage.setItem(SESSION_EXTRAS_KEY, JSON.stringify(extras))
}

function renderShellBits() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <RoleSwitchButton />
        <ActingRoleBanner />
        <AccountMenu />
      </AuthProvider>
    </MemoryRouter>,
  )
}

afterEach(() => sessionStorage.clear())

describe('role switching (rev 55)', () => {
  it('hides the Switch role button from a user with no extra roles', () => {
    signIn({ role: 'ACCOUNTANT', primaryRole: 'ACCOUNTANT', branchIds: [2] }, { canSwitchRole: false })
    renderShellBits()
    expect(screen.queryByRole('button', { name: /switch role/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('shows the Switch role button, but no banner, to a user who has extra roles and is in their own role', () => {
    signIn({ role: 'ACCOUNTANT', primaryRole: 'ACCOUNTANT', branchIds: [2] }, { canSwitchRole: true })
    renderShellBits()
    expect(screen.getByRole('button', { name: /switch role/i })).toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('tells a switched user which role and branch they are in, whose name entries carry, and how to go back', () => {
    signIn(
      { role: 'CASHIER', primaryRole: 'ACCOUNTANT', actingBranchId: 3, homeBranchId: 3, branchIds: [] },
      { canSwitchRole: true, actingBranchLabel: 'OOR' },
    )
    renderShellBits()
    const banner = screen.getByRole('status')
    expect(banner).toHaveTextContent('Cashier · OOR')
    expect(banner).toHaveTextContent('Ajay Kumar')
    expect(screen.getByRole('button', { name: /switch back to accountant/i })).toBeInTheDocument()
  })

  it('keeps the Switch role button for a switched user even if the stored flag was lost', () => {
    signIn({ role: 'CASHIER', primaryRole: 'ACCOUNTANT', actingBranchId: 3, homeBranchId: 3 }, {})
    renderShellBits()
    expect(screen.getByRole('button', { name: /switch role/i })).toBeInTheDocument()
  })

  it('treats a token from before role switching (no primaryRole claim) as the user’s own role', () => {
    signIn({ role: 'OWNER' }, { canSwitchRole: true })
    renderShellBits()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })
})

describe('actorLabel', () => {
  it('shows just the name when the actor was in their own role', () => {
    expect(actorLabel('Ajay Kumar', null)).toBe('Ajay Kumar')
    expect(actorLabel('Ajay Kumar')).toBe('Ajay Kumar')
  })

  it('appends the acting role when the actor had switched', () => {
    expect(actorLabel('Ajay Kumar', 'CASHIER')).toBe('Ajay Kumar · as Cashier')
    expect(actorLabel('Ajay Kumar', 'FINANCE_MANAGER')).toBe('Ajay Kumar · as Finance Manager')
  })
})
