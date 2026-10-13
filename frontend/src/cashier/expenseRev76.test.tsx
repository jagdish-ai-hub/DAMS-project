import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import NewExpensePage from './NewExpensePage'
import { expensesApi } from '../api/expenses'
import { receiversApi } from '../api/receivers'

vi.mock('../api/masters', () => {
  const ok = (rows: unknown[]) => Promise.resolve({ data: rows })
  const row = (id: number, name: string, extra: object = {}) =>
    ({ id, name, active: true, requiresBank: false, requiresRef: false, isCash: true, ...extra })
  return {
    mastersApi: {
      list: vi.fn((key: string) => ok(
        key === 'expense-categories' ? [row(1, 'Workshop'), row(2, 'Fuel')]
          : key === 'expense-statuses' ? [
            row(1, 'Open'),
            row(2, 'Requires Finance Approval', { requiresFmApproval: true }),
            row(3, 'Transfer to Claim', { triggersClaim: true }),
          ]
          : key === 'expense-sub-categories' ? [{ ...row(5, 'Tools'), expenseCategoryId: 1, limitAmount: 5000 }]
          : key === 'expense-modes' ? [row(7, 'Cash')]
          : [])),
      listSelectable: vi.fn(() => ok([row(1, 'Open')])),
    },
  }
})
vi.mock('../api/customers', () => ({
  customersApi: { search: vi.fn(() => Promise.resolve({ data: [] })), get: vi.fn(), update: vi.fn(), vehicles: vi.fn(() => Promise.resolve({ data: [] })) },
}))
vi.mock('../api/vehicles', () => ({ vehiclesApi: { byNumber: vi.fn() } }))
vi.mock('../api/receivers', () => ({ receiversApi: { search: vi.fn() } }))
vi.mock('../api/jobCards', () => ({
  jobCardsApi: { search: vi.fn(() => Promise.resolve({ data: [] })), get: vi.fn(), patch: vi.fn() },
}))
vi.mock('../api/expenses', () => ({
  expensesApi: { create: vi.fn(), get: vi.fn(), requestApproval: vi.fn() },
}))
vi.mock('./AttachmentsPanel', () => ({ default: () => null }))

const search = vi.mocked(receiversApi.search)
const create = vi.mocked(expensesApi.create)
const requestApproval = vi.mocked(expensesApi.requestApproval)

function renderPage() {
  render(<MemoryRouter initialEntries={['/app/new-expense']}><NewExpensePage /></MemoryRouter>)
}

describe('Expense form (rev 76)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    Element.prototype.scrollIntoView = vi.fn() // jsdom has none; the form scrolls errors into view
    search.mockResolvedValue({
      data: [
        { id: 11, name: 'Sharma Auto Spares', phone: '98765 43210', active: true, createdAt: '', categoryIds: [1] },
        { id: 12, name: 'City Fuel Station', phone: null, active: true, createdAt: '', categoryIds: [2] },
      ],
    } as never)
    create.mockResolvedValue({ data: { id: 9, documentNo: null, lines: [], workflowStatus: 'DRAFT' } } as never)
    requestApproval.mockResolvedValue({ data: { id: 9, documentNo: null, lines: [], workflowStatus: 'DRAFT' } } as never)
  })

  it('searches vendors, marks the ones that supply the chosen category, and fills the phone on pick', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('textbox', { name: 'Receiver name' }))

    const mapped = await screen.findByRole('button', { name: /Sharma Auto Spares/ })
    expect(within(mapped).getByText(/Supplies this category/)).toBeInTheDocument()
    expect(search).toHaveBeenCalledWith('', 1) // default category = the first one

    await user.click(mapped)
    expect(screen.getByRole('textbox', { name: 'Receiver name' })).toHaveValue('Sharma Auto Spares')
    expect(screen.getByText('Existing vendor linked.')).toBeInTheDocument()
    expect(screen.getByPlaceholderText(/Optional — mobile number/)).toHaveValue('98765 43210')
  })

  it('treats a typed name as a new vendor', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.type(await screen.findByRole('textbox', { name: 'Receiver name' }), 'Brand New Garage')
    expect(await screen.findByText(/New vendor — will be added when you save/)).toBeInTheDocument()
  })

  it('Transfer to Claim is a Yes / No dropdown that moves the expense onto the claim status', async () => {
    const user = userEvent.setup()
    renderPage()
    const claim = await screen.findByLabelText('Transfer to Claim')
    expect(claim).toHaveValue('no')
    // the claim status is not a plain status choice any more
    expect(screen.queryByRole('option', { name: 'Transfer to Claim' })).not.toBeInTheDocument()

    await user.selectOptions(claim, 'yes')
    expect(claim).toHaveValue('yes')
    expect(screen.getAllByRole('combobox').some((el) => (el as HTMLSelectElement).disabled
      && within(el).queryByRole('option', { name: 'Transfer to Claim' }) != null)).toBe(true)

    await user.selectOptions(claim, 'no')
    expect(claim).toHaveValue('no')
  })

  it('a status that needs Finance approval asks for a mandatory reason, and Send for Review refuses without it', async () => {
    const user = userEvent.setup()
    renderPage()
    expect(screen.queryByLabelText(/Why is this expense needed/)).not.toBeInTheDocument()

    const status = (await screen.findAllByRole('combobox')).find(
      (el) => within(el).queryByRole('option', { name: 'Requires Finance Approval' }) != null) as HTMLSelectElement
    await user.selectOptions(status, 'Requires Finance Approval')
    const reason = await screen.findByLabelText(/Why is this expense needed/)
    expect(reason).toBeRequired()

    await user.type(screen.getByRole('textbox', { name: 'Receiver name' }), 'Some Vendor')
    const pick = async (option: string) => {
      const sel = (await screen.findAllByRole('combobox')).find(
        (el) => within(el).queryByRole('option', { name: option }) != null) as HTMLSelectElement
      await user.selectOptions(sel, option)
    }
    await pick('Tools')
    await pick('Cash')
    await user.type(screen.getAllByRole('spinbutton')[0], '300')
    await user.click(screen.getByRole('button', { name: 'Send for Review' }))

    expect(await within(await screen.findByRole('alert')).findByText(/Write why this expense is needed/)).toBeInTheDocument()
    expect(create).not.toHaveBeenCalled()
    expect(requestApproval).not.toHaveBeenCalled()

    await user.type(reason, 'Tow truck for a stranded customer')
    await user.click(screen.getByRole('button', { name: 'Send for Review' }))
    await waitFor(() => expect(requestApproval).toHaveBeenCalledWith(9, 'Tow truck for a stranded customer'))
  })
})
