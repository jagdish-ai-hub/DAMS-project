import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthUser } from '../auth/AuthContext'
import { reviewApi } from '../api/review'
import { isMakerOf, MakerBlockedNote, type MakerDoc } from './reviewShared'

vi.mock('../api/review', () => ({ reviewApi: { reviewers: vi.fn() } }))

const reviewers = vi.mocked(reviewApi.reviewers)

function user(over: Partial<AuthUser> = {}): AuthUser {
  return {
    userId: 2, orgId: 1, role: 'ACCOUNTANT', name: 'Priya Nair', branchIds: [], homeBranchId: null,
    primaryRole: 'ACCOUNTANT', isActing: false, actingBranchId: null, actingBranchLabel: null,
    canSwitchRole: true, ...over,
  }
}

/** OOJ-OCT26-E-001 as it is live: entered by user 2 while acting as Cashier. */
function doc(over: Partial<MakerDoc> = {}): MakerDoc {
  return {
    createdBy: 2, lastModifiedBy: 2, branchId: 1, branchCode: 'OOJ',
    history: [{ actor: 'Priya Nair', action: 'Created', note: null, at: '2026-10-04T14:22:36Z', actorRole: 'CASHIER' }],
    ...over,
  }
}

describe('isMakerOf (maker-checker mirror, rev 59)', () => {
  it('blocks an Accountant from an entry they created', () => {
    expect(isMakerOf(user(), doc())).toBe(true)
  })

  it('blocks them when they only last modified it', () => {
    expect(isMakerOf(user(), doc({ createdBy: 9, lastModifiedBy: 2 }))).toBe(true)
  })

  it('does not block a different person', () => {
    expect(isMakerOf(user({ userId: 4 }), doc())).toBe(false)
  })

  it('does not block an Owner acting as Accountant on the entry they made as Cashier', () => {
    const owner = user({ role: 'ACCOUNTANT', primaryRole: 'OWNER', isActing: true, actingBranchId: 1 })
    expect(isMakerOf(owner, doc())).toBe(false)
  })

  it('does not block an Owner acting as Finance Manager either', () => {
    const owner = user({ role: 'FINANCE_MANAGER', primaryRole: 'OWNER', isActing: true, actingBranchId: 1 })
    expect(isMakerOf(owner, doc())).toBe(false)
  })

  it('still blocks a Finance Manager who switched and entered the entry (only an Owner is exempt)', () => {
    const fm = user({ role: 'FINANCE_MANAGER', primaryRole: 'ACCOUNTANT', isActing: true, actingBranchId: 1 })
    expect(isMakerOf(fm, doc())).toBe(true)
  })

  it('is false when nobody is signed in', () => {
    expect(isMakerOf(null, doc())).toBe(false)
  })
})

describe('MakerBlockedNote', () => {
  beforeEach(() => reviewers.mockReset())

  it('says who entered it, in which role, and names the people who can verify it', async () => {
    reviewers.mockResolvedValue({ data: [{ id: 4, name: 'Anita Rao' }] } as never)
    render(<MakerBlockedNote doc={doc()} step="ACCOUNTANT" />)

    expect(screen.getByText(/you entered this as cashier/i)).toBeInTheDocument()
    expect(await screen.findByText('Anita Rao')).toBeInTheDocument()
    expect(screen.getByText(/can verify it/i)).toBeInTheDocument()
    // the maker and last modifier are excluded from the lookup, for the right branch and step
    expect(reviewers).toHaveBeenCalledWith(1, 'ACCOUNTANT', [2, 2])
  })

  it('uses "approve" and the Finance Manager step on the Finance page', async () => {
    reviewers.mockResolvedValue({ data: [{ id: 3, name: 'Rakesh Menon' }] } as never)
    render(<MakerBlockedNote doc={doc()} step="FINANCE_MANAGER" />)

    expect(await screen.findByText('Rakesh Menon')).toBeInTheDocument()
    expect(screen.getByText(/can approve it/i)).toBeInTheDocument()
    expect(reviewers).toHaveBeenCalledWith(1, 'FINANCE_MANAGER', [2, 2])
  })

  it('tells the user to ask the Owner when nobody else can clear it', async () => {
    reviewers.mockResolvedValue({ data: [] } as never)
    render(<MakerBlockedNote doc={doc()} step="ACCOUNTANT" />)

    expect(await screen.findByText(/no other accountant at ooj is set up yet/i)).toBeInTheDocument()
    expect(screen.getByText(/ask your owner/i)).toBeInTheDocument()
  })

  it('keeps the first sentence on its own if the reviewer list cannot be loaded', async () => {
    // A response the component can't use makes its `.then` handler throw, which lands in the same
    // `.catch` a network error would. (A mock returning a rejected promise is not used here: this
    // vitest version reports the spy's own bookkeeping of it as an unhandled rejection, which
    // would fail the run no matter how the component handles it.)
    reviewers.mockResolvedValue(undefined as never)
    render(<MakerBlockedNote doc={doc()} step="ACCOUNTANT" />)

    expect(screen.getByText(/you entered this as cashier/i)).toBeInTheDocument()
    await vi.waitFor(() => expect(reviewers).toHaveBeenCalled())
    await Promise.resolve()   // let the `.catch` run
    expect(screen.queryByText(/ask your owner/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/can verify it/i)).not.toBeInTheDocument()
  })

  it('omits the role when the entry has no recorded acting role', async () => {
    reviewers.mockResolvedValue({ data: [] } as never)
    const own = doc({ history: [{ actor: 'Priya Nair', action: 'Created', note: null, at: 'x', actorRole: null }] })
    render(<MakerBlockedNote doc={own} step="ACCOUNTANT" />)

    expect(screen.getByText(/you entered this \(or edited it last\)/i)).toBeInTheDocument()
    await screen.findByText(/ask your owner/i)   // let the reviewer lookup settle inside act()
  })

  it('lists at most three names, then "and N more"', async () => {
    reviewers.mockResolvedValue({
      data: ['A One', 'B Two', 'C Three', 'D Four', 'E Five'].map((name, i) => ({ id: 10 + i, name })),
    } as never)
    render(<MakerBlockedNote doc={doc()} step="ACCOUNTANT" />)

    expect(await screen.findByText('A One, B Two, C Three and 2 more')).toBeInTheDocument()
  })
})
