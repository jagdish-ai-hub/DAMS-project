import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth/AuthContext'
import { ACCESS_TOKEN_KEY, SESSION_EXTRAS_KEY, USER_NAME_KEY } from '../auth/session'
import { sparePartsApi, type SpareAccess } from '../api/spareParts'
import { SpareAccessProvider, useSpareAccess } from './SpareAccessContext'

vi.mock('../api/spareParts', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/spareParts')>()),
  sparePartsApi: { access: vi.fn() },
}))

/** An unsigned JWT: the client only decodes it; the server is what verifies. */
function token(claims: Record<string, unknown>): string {
  const b64 = (o: unknown) => btoa(JSON.stringify(o)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_')
  return `${b64({ alg: 'none' })}.${b64({ sub: '7', orgId: 1, exp: Math.floor(Date.now() / 1000) + 3600, ...claims })}.x`
}

function signIn(role: string) {
  sessionStorage.setItem(ACCESS_TOKEN_KEY, token({ role, primaryRole: role }))
  sessionStorage.setItem(USER_NAME_KEY, 'Ajay Kumar')
  sessionStorage.setItem(SESSION_EXTRAS_KEY, JSON.stringify({}))
}

const keeper: SpareAccess = {
  enabled: true, canView: true, canSeeDashboard: false, owner: false, role: 'STORE_KEEPER',
  actBranchIds: [12], permissions: ['BILL_PART', 'RAISE_REQUEST', 'SEND_RECEIVE'],
  branches: [{ id: 10, code: 'OOJ', name: 'Jeypore' }, { id: 12, code: 'OOR', name: 'Rayagada' }],
}

/** Prints what the hook says, so the tests assert on what a screen would actually see. */
function Probe() {
  const { visible, loading, can } = useSpareAccess()
  return (
    <ul>
      <li>visible:{String(visible)}</li>
      <li>loading:{String(loading)}</li>
      <li>bill-at-own:{String(can('BILL_PART', 12))}</li>
      <li>bill-at-other:{String(can('BILL_PART', 10))}</li>
      <li>upload:{String(can('UPLOAD_ADD'))}</li>
    </ul>
  )
}

function renderProbe() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <SpareAccessProvider><Probe /></SpareAccessProvider>
      </AuthProvider>
    </MemoryRouter>,
  )
}

// braces matter: a function RETURNED from beforeEach is run by Vitest as cleanup, and mockReset() returns the mock itself
beforeEach(() => { vi.mocked(sparePartsApi.access).mockReset() })
afterEach(() => sessionStorage.clear())

describe('Spare Parts access (rev 91)', () => {
  it('lets a Store Keeper act only at their own branch and only with a ticked permission', async () => {
    signIn('STORE_KEEPER')
    vi.mocked(sparePartsApi.access).mockResolvedValue({ data: keeper } as never)
    renderProbe()

    await waitFor(() => expect(screen.getByText('visible:true')).toBeInTheDocument())
    expect(screen.getByText('bill-at-own:true')).toBeInTheDocument()
    expect(screen.getByText('bill-at-other:false')).toBeInTheDocument()   // they can look at OOJ, not act there
    expect(screen.getByText('upload:false')).toBeInTheDocument()          // not ticked for their role
  })

  it('makes the Owner view-only: they see the module but can never act, whatever the permission list says', async () => {
    signIn('OWNER')
    vi.mocked(sparePartsApi.access).mockResolvedValue({
      data: { ...keeper, owner: true, role: 'OWNER', canSeeDashboard: true, actBranchIds: [], permissions: ['BILL_PART', 'UPLOAD_ADD'] },
    } as never)
    renderProbe()

    await waitFor(() => expect(screen.getByText('visible:true')).toBeInTheDocument())
    expect(screen.getByText('bill-at-own:false')).toBeInTheDocument()
    expect(screen.getByText('upload:false')).toBeInTheDocument()
  })

  it('shows nothing at all while the module is off', async () => {
    signIn('OWNER')
    vi.mocked(sparePartsApi.access).mockResolvedValue({
      data: { enabled: false, canView: false, canSeeDashboard: false, owner: false, role: null, actBranchIds: [], permissions: [], branches: [] },
    } as never)
    renderProbe()

    await waitFor(() => expect(screen.getByText('loading:false')).toBeInTheDocument())
    expect(screen.getByText('visible:false')).toBeInTheDocument()
  })

  it('treats any failure (an old server, no network) as "off" instead of breaking the app', async () => {
    signIn('CASHIER')
    vi.mocked(sparePartsApi.access).mockRejectedValue(new Error('Network Error'))
    renderProbe()

    await waitFor(() => expect(screen.getByText('loading:false')).toBeInTheDocument())
    expect(screen.getByText('visible:false')).toBeInTheDocument()
  })

  it('does not even ask for a Super Admin, who has no organization', async () => {
    signIn('SUPER_ADMIN')
    renderProbe()

    await waitFor(() => expect(screen.getByText('loading:false')).toBeInTheDocument())
    expect(sparePartsApi.access).not.toHaveBeenCalled()
    expect(screen.getByText('visible:false')).toBeInTheDocument()
  })
})
