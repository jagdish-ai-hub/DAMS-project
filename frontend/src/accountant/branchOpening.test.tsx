import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import BranchOpeningPage from './BranchOpeningPage'
import { cashApi, type BranchOpening } from '../api/cash'

vi.mock('../api/cash', () => ({ cashApi: { openings: vi.fn(), setOpening: vi.fn() } }))
vi.mock('../help/HelpButton', () => ({ default: () => null }))

const row = (over: Partial<BranchOpening>): BranchOpening =>
  ({ branchId: 6, branchCode: 'OOJ', branchName: 'Jeypore', set: false, amount: null, openingDate: null, locked: false, ...over })

describe('Branch opening (rev 97)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(cashApi.openings).mockResolvedValue({
      data: [row({}), row({ branchId: 7, branchCode: 'OOB', branchName: 'Berhampur', set: true, amount: 5000, openingDate: '2026-11-02', locked: true })],
    } as never)
    vi.mocked(cashApi.setOpening).mockResolvedValue({ data: {} } as never)
  })

  it('offers the form only for a branch that is not set, and shows a set one read-only', async () => {
    render(<BranchOpeningPage />)
    const jeypore = await screen.findByRole('region', { name: 'OOJ Jeypore' })
    expect(within(jeypore).getByText('Not set')).toBeInTheDocument()
    expect(within(jeypore).getByRole('button', { name: 'Save opening' })).toBeInTheDocument()

    const berhampur = screen.getByRole('region', { name: 'OOB Berhampur' })
    expect(within(berhampur).getByText('Set')).toBeInTheDocument()
    expect(within(berhampur).getByText(/5,000/)).toBeInTheDocument()
    expect(within(berhampur).queryByRole('button', { name: 'Save opening' })).not.toBeInTheDocument()
  })

  it('needs a date and an amount before it saves anything', async () => {
    const user = userEvent.setup()
    render(<BranchOpeningPage />)
    await user.click(await screen.findByRole('button', { name: 'Save opening' }))
    expect(await screen.findByText('Pick the date the drawer started.')).toBeInTheDocument()

    await user.type(screen.getByLabelText('OOJ opening date'), '2026-11-02')
    await user.click(screen.getByRole('button', { name: 'Save opening' }))
    expect(await screen.findByText(/Enter the cash counted/)).toBeInTheDocument()
    expect(cashApi.setOpening).not.toHaveBeenCalled()
  })

  it('saves the opening — zero is allowed — and reloads the list', async () => {
    const user = userEvent.setup()
    render(<BranchOpeningPage />)
    await screen.findByRole('region', { name: 'OOJ Jeypore' })
    await user.type(screen.getByLabelText('OOJ opening date'), '2026-11-02')
    await user.type(screen.getByLabelText('OOJ opening amount'), '0')
    await user.click(screen.getByRole('button', { name: 'Save opening' }))

    await waitFor(() => expect(cashApi.setOpening).toHaveBeenCalledWith({ branchId: 6, openingDate: '2026-11-02', amount: 0 }))
    expect(await screen.findByRole('status')).toHaveTextContent('the cashier can close the day now')
    expect(cashApi.openings).toHaveBeenCalledTimes(2)
  })

  it('says so when the Accountant has no branch', async () => {
    vi.mocked(cashApi.openings).mockResolvedValue({ data: [] } as never)
    render(<BranchOpeningPage />)
    expect(await screen.findByText(/not assigned to any branch/)).toBeInTheDocument()
  })
})
