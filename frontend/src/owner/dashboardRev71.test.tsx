import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DashboardPage from './DashboardPage'
import { dashboardApi, type DashboardSummary, type PendingItem, type PendingWork } from '../api/dashboard'

const navigate = vi.fn()
vi.mock('react-router-dom', async (orig) => ({ ...(await orig<typeof import('react-router-dom')>()), useNavigate: () => navigate }))
vi.mock('../api/branches', () => ({
  branchesApi: { list: () => Promise.resolve({ data: [{ id: 1, code: 'OOJ', name: 'Jeypore', active: true }, { id: 3, code: 'OOR', name: 'Rayagada', active: true }] }) },
}))
vi.mock('../api/dashboard', () => ({
  dashboardApi: {
    summary: vi.fn(), outstanding: vi.fn(), activity: vi.fn(), pendingWork: vi.fn(),
    cashBreakdown: vi.fn(), collectionsBreakdown: vi.fn(), expensesBreakdown: vi.fn(), claims: vi.fn(),
  },
}))
vi.mock('../shared/GlobalSearch', () => ({ default: () => null }))
vi.mock('../shared/ClaimsSummaryCard', () => ({ default: () => null }))
vi.mock('./AiInsightsSection', () => ({ default: () => null }))
vi.mock('./AskDamsPanel', () => ({ default: () => null }))
vi.mock('../shell/motion', async (orig) => ({
  ...(await orig<typeof import('../shell/motion')>()),
  CountUp: ({ value }: { value: string }) => <>{value}</>,
}))

const api = vi.mocked(dashboardApi)

const summary: DashboardSummary = {
  scope: 'ALL', period: 'mtd',
  kpis: { collections: 95376, expenses: 0, net: 95376, cashInHand: 1600, pendingReview: 64, collectionsAwaiting: 925813, expensesAwaiting: 8570 },
  trend: [], byMode: [], byCategory: [], branchComparison: [],
}

const item = (over: Partial<PendingItem>): PendingItem => ({
  type: 'receipt' as const, id: 1, documentNo: 'OOR-OCT26-R-005', branchId: 3, branchCode: 'OOR', party: 'Suresh', category: 'Workshop',
  amount: 5000, workflowStatus: 'SUBMITTED', stage: 'Awaiting verification', draft: false,
  since: new Date(Date.now() - 3 * 86_400_000).toISOString(), ...over,
})

const work: PendingWork = {
  groups: [
    { holder: 'CASHIER', label: 'Cashier', count: 5, amount: 75630, draftCount: 20, draftAmount: 2000000,
      items: [item({ id: 11, documentNo: 'OOR-OCT26-R-020', workflowStatus: 'QUERIED', stage: 'Queried by the Accountant — needs fixing' })] },
    { holder: 'ACCOUNTANT', label: 'Accountant', count: 4, amount: 19300, draftCount: 0, draftAmount: 0,
      items: [item({ id: 7, documentNo: 'OOR-OCT26-R-007' }), item({ type: 'expense', id: 9, documentNo: 'OOR-OCT26-E-003', party: 'City Fuel', amount: 1400 })] },
    { holder: 'FINANCE_MANAGER', label: 'Finance Manager', count: 0, amount: 0, draftCount: 0, draftAmount: 0, items: [] },
  ],
}

function renderPage() {
  return render(<MemoryRouter><DashboardPage /></MemoryRouter>)
}

describe('Owner dashboard (rev 71)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.summary.mockResolvedValue({ data: summary } as never)
    api.outstanding.mockResolvedValue({ data: [] } as never)
    api.activity.mockResolvedValue({ data: [] } as never)
    api.pendingWork.mockResolvedValue({ data: work } as never)
    api.cashBreakdown.mockResolvedValue({ data: [
      { kind: 'cash-in', documentId: 5, documentNo: 'OOR-OCT26-C-001', workflowStatus: 'APPROVED', date: '2026-10-05', createdAt: '', branchCode: 'OOR', party: 'Cash In from bank', description: 'Top-up', modeName: 'Cash', amount: 500 },
      { kind: 'cash-out', documentId: 6, documentNo: 'OOR-OCT26-C-002', workflowStatus: 'APPROVED', date: '2026-10-07', createdAt: '', branchCode: 'OOR', party: 'Cash Out to bank', description: 'Deposit', modeName: 'Cash', amount: -100 },
      { kind: 'opening', documentId: null, documentNo: null, workflowStatus: 'OPENING', date: '2026-09-29', createdAt: null, branchCode: 'OOR', party: 'Counted at close of 2026-09-29', description: 'Carried in', modeName: 'Cash', amount: 1200 },
    ] } as never)
  })

  it('shows what is awaiting approval under the approved-only Collections and Expenses', async () => {
    renderPage()
    expect(await screen.findByText(/\+ ₹9,25,813 awaiting approval/)).toBeInTheDocument()
    expect(screen.getByText(/\+ ₹8,570 awaiting approval/)).toBeInTheDocument()
    // the old, misplaced "pending review" line is gone
    expect(screen.queryByText(/pending review/)).not.toBeInTheDocument()
  })

  it('shows who is holding what, with unsent drafts kept beside the Cashier count', async () => {
    renderPage()
    const cashier = await screen.findByRole('button', { name: 'Cashier: 5 waiting' })
    expect(cashier).toHaveTextContent('+ 20 drafts not submitted')
    expect(screen.getByRole('button', { name: 'Accountant: 4 waiting' })).toBeInTheDocument()
    const fm = screen.getByRole('button', { name: 'Finance Manager: 0 waiting' })
    expect(fm).toBeDisabled()
    expect(fm).toHaveTextContent('nothing waiting')
  })

  it('clicking a tile opens its entries, and a row opens that document', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('button', { name: 'Accountant: 4 waiting' }))

    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText('OOR-OCT26-R-007')).toBeInTheDocument()
    expect(within(dialog).getByText('OOR-OCT26-E-003')).toBeInTheDocument()
    expect(within(dialog).getAllByText('Awaiting verification').length).toBeGreaterThan(0)
    expect(within(dialog).getAllByText('3 days').length).toBeGreaterThan(0)

    await user.click(within(dialog).getByText('OOR-OCT26-E-003'))
    expect(navigate).toHaveBeenCalledWith('/app/new-expense?editDoc=9')
  })

  it('the branch filter narrows the card too', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByRole('button', { name: 'Cashier: 5 waiting' })
    await user.click(screen.getByRole('button', { name: 'OOR' }))
    await waitFor(() => expect(api.pendingWork).toHaveBeenLastCalledWith(3))
  })

  it('Cash in hand opens the running breakdown: opening + movements, outflows negative, total = the card', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByText(/Cash in hand/))
    const dialog = await screen.findByRole('dialog')
    expect(api.cashBreakdown).toHaveBeenCalledWith(undefined)
    expect(within(dialog).getByText('Counted at close of 2026-09-29')).toBeInTheDocument()
    expect(within(dialog).getByText('−₹100')).toBeInTheDocument()
    expect(within(dialog).getByText('₹1,600')).toBeInTheDocument()   // 1200 + 500 − 100

    // the opening row has no document behind it — clicking it goes nowhere
    await user.click(within(dialog).getByText('Counted at close of 2026-09-29'))
    expect(navigate).not.toHaveBeenCalled()
    await user.click(within(dialog).getByText('OOR-OCT26-C-001'))
    expect(navigate).toHaveBeenCalledWith('/app/cash?editDoc=5')
  })
})
