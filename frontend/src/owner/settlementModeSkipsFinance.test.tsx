import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MastersPage from './MastersPage'
import { mastersApi } from '../api/masters'

vi.mock('../api/masters', () => ({
  mastersApi: { list: vi.fn(), create: vi.fn(), update: vi.fn() },
}))
vi.mock('./AiMastersStrip', () => ({ default: () => null }))
vi.mock('./ReceiversSection', () => ({ default: () => null }))
vi.mock('./CustomersSection', () => ({ default: () => null }))

const mode = (id: number, name: string, skipsFinance: boolean) =>
  ({ id, type: 'settlement-modes', name, active: true, sortOrder: id, requiresBank: false, requiresRef: false, isCash: name === 'Cash', skipsFinance })

describe('Settlement modes: "No Finance step needed" (rev 91)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(mastersApi.list).mockImplementation(((type: string) => Promise.resolve({
      data: type === 'settlement-modes'
        ? [mode(1, 'Cash', true), mode(2, 'QR / UPI', true), mode(3, 'AMC/WRNTY', false)]
        : [],
    })) as never)
    vi.mocked(mastersApi.update).mockResolvedValue({ data: {} } as never)
  })

  async function openSettlementModes(user: ReturnType<typeof userEvent.setup>) {
    render(<MastersPage />)
    await user.click(await screen.findByRole('button', { name: 'Settlement modes' }))
    await screen.findByText('AMC/WRNTY')
  }

  it('shows which modes skip Finance in the list', async () => {
    const user = userEvent.setup()
    await openSettlementModes(user)

    expect(screen.getByText('No Finance step')).toBeInTheDocument()
    const rowOf = (name: string) => screen.getByText(name).closest('tr') as HTMLElement
    expect(within(rowOf('Cash')).getByText('Yes')).toBeInTheDocument()
    expect(within(rowOf('QR / UPI')).getByText('Yes')).toBeInTheDocument()
    expect(within(rowOf('AMC/WRNTY')).queryByText('Yes')).not.toBeInTheDocument()
  })

  it('the Owner can tick it on a mode and the value is saved', async () => {
    const user = userEvent.setup()
    await openSettlementModes(user)
    const row = screen.getByText('AMC/WRNTY').closest('tr') as HTMLElement
    await user.click(within(row).getByRole('button', { name: 'Edit' }))

    const tick = await screen.findByRole('checkbox', { name: /No Finance step needed/ })
    expect(tick).not.toBeChecked()
    await user.click(tick)
    await user.click(screen.getByRole('button', { name: /Save/ }))

    await waitFor(() => expect(mastersApi.update).toHaveBeenCalledWith('settlement-modes', 3,
      expect.objectContaining({ skipsFinance: true })))
  })

  it('an already ticked mode opens ticked, and unticking saves false', async () => {
    const user = userEvent.setup()
    await openSettlementModes(user)
    const row = screen.getByText('Cash').closest('tr') as HTMLElement
    await user.click(within(row).getByRole('button', { name: 'Edit' }))

    const tick = await screen.findByRole('checkbox', { name: /No Finance step needed/ })
    expect(tick).toBeChecked()
    await user.click(tick)
    await user.click(screen.getByRole('button', { name: /Save/ }))

    await waitFor(() => expect(mastersApi.update).toHaveBeenCalledWith('settlement-modes', 1,
      expect.objectContaining({ skipsFinance: false })))
  })
})
