import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import NewReceiptPage from './NewReceiptPage'
import { customersApi } from '../api/customers'
import { receiptsApi } from '../api/receipts'

vi.mock('../api/masters', () => {
  const ok = (rows: unknown[]) => Promise.resolve({ data: rows })
  const row = (id: number, name: string) => ({ id, name, active: true, requiresBank: false, isCash: name === 'Cash' })
  return {
    mastersApi: {
      list: vi.fn((key: string) => ok(
        key === 'settlement-modes' ? [row(1, 'Cash')]
          : key === 'receive-categories' ? [row(1, 'Workshop')]
          : [])),
      listSelectable: vi.fn(() => ok([row(1, 'Received')])),
    },
  }
})
vi.mock('../api/customers', () => ({
  customersApi: { search: vi.fn(), get: vi.fn(), vehicles: vi.fn(() => Promise.resolve({ data: [] })) },
}))
vi.mock('../api/vehicles', () => ({ vehiclesApi: { search: vi.fn(() => Promise.resolve({ data: [] })) } }))
vi.mock('../api/jobCards', () => ({
  jobCardsApi: { search: vi.fn(() => Promise.resolve({ data: [] })), get: vi.fn(), patch: vi.fn() },
}))
vi.mock('../api/receipts', () => ({
  receiptsApi: { create: vi.fn(), get: vi.fn() },
}))
vi.mock('./AttachmentsPanel', () => ({ default: () => null }))
vi.mock('./PrintReceiptModal', () => ({ default: () => null }))
vi.mock('./UpiQrModal', () => ({ default: () => null }))

const search = vi.mocked(customersApi.search)
const create = vi.mocked(receiptsApi.create)

const acme = { id: 1, name: 'Acme Transport', phone: '70000 00000', vehicles: [], createdAt: '', lastContactPhone: '98765 43210' }
const beta = { id: 2, name: 'Beta Motors', phone: '88888 00000', vehicles: [], createdAt: '', lastContactPhone: null }

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/app/new-receipt']}>
      <NewReceiptPage />
    </MemoryRouter>,
  )
}

async function pickCustomer(user: ReturnType<typeof userEvent.setup>, typed: string, optionName: string) {
  await user.click(screen.getByLabelText('Customer name'))
  await user.type(screen.getByLabelText('Customer name'), typed)
  await user.click(await screen.findByRole('button', { name: new RegExp(optionName) }))
}

describe('New Receipt: Contact and Chassis # (rev 68)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    search.mockResolvedValue({ data: [acme, beta] } as never)
  })

  it('has both optional boxes, Contact right under the customer name', async () => {
    renderPage()
    const contact = await screen.findByLabelText('Contact number')
    expect(contact).toBeEnabled()
    expect(contact).not.toBeRequired()
    expect(screen.getByLabelText('Chassis number')).not.toBeRequired()
    const labels = screen.getAllByText(/^(Customer Name|Contact|Vehicle #|Chassis #)/).map((e) => e.textContent?.replace(/\s*\*$/, '').trim())
    expect(labels).toEqual(['Customer Name', 'Contact', 'Vehicle #', 'Chassis #'])
  })

  it('fills Contact with the number last saved for the picked customer', async () => {
    const user = userEvent.setup()
    renderPage()
    await pickCustomer(user, 'Acm', 'Acme Transport')
    expect(screen.getByLabelText('Contact number')).toHaveValue('98765 43210')
  })

  it('falls back to the customer record when no receipt ever saved one', async () => {
    const user = userEvent.setup()
    renderPage()
    await pickCustomer(user, 'Bet', 'Beta Motors')
    expect(screen.getByLabelText('Contact number')).toHaveValue('88888 00000')
  })

  it('never carries one customer\'s number over to the next customer', async () => {
    const user = userEvent.setup()
    renderPage()
    await pickCustomer(user, 'Acm', 'Acme Transport')
    expect(screen.getByLabelText('Contact number')).toHaveValue('98765 43210')

    // typing over the name makes it a brand-new customer — the prefilled number must go
    await user.clear(screen.getByLabelText('Customer name'))
    await user.type(screen.getByLabelText('Customer name'), 'Someone New')
    expect(screen.getByLabelText('Contact number')).toHaveValue('')
  })

  it('keeps a number the cashier typed themselves when the customer changes', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.type(screen.getByLabelText('Contact number'), '91111 22222')
    await pickCustomer(user, 'Acm', 'Acme Transport')
    expect(screen.getByLabelText('Contact number')).toHaveValue('91111 22222')
  })

  it('upper-cases the chassis number as it is typed', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.type(screen.getByLabelText('Chassis number'), 'mc2abc123')
    expect(screen.getByLabelText('Chassis number')).toHaveValue('MC2ABC123')
  })

  it('sends both on save — and a brand-new customer also gets the number as their phone', async () => {
    const user = userEvent.setup()
    create.mockResolvedValue({ data: { id: 9, documentNo: null, lines: [], workflowStatus: 'DRAFT' } } as never)
    renderPage()
    await user.type(screen.getByLabelText('Customer name'), 'Fresh Customer')
    await user.type(screen.getByLabelText('Contact number'), '93333 44444')
    await user.type(screen.getByLabelText('Chassis number'), 'mc2xyz789')
    await user.click(screen.getByRole('button', { name: /save draft/i }))

    await waitFor(() => expect(create).toHaveBeenCalled())
    const body = create.mock.calls[0][0]
    expect(body.contactPhone).toBe('93333 44444')
    expect(body.chassisNo).toBe('MC2XYZ789')
    expect(body.customerPhone).toBe('93333 44444')   // new customer: saved as their phone too
  })

  it('does not rewrite an existing customer\'s saved phone', async () => {
    const user = userEvent.setup()
    create.mockResolvedValue({ data: { id: 9, documentNo: null, lines: [], workflowStatus: 'DRAFT' } } as never)
    renderPage()
    await pickCustomer(user, 'Acm', 'Acme Transport')
    await user.clear(screen.getByLabelText('Contact number'))
    await user.type(screen.getByLabelText('Contact number'), '95555 66666')
    await user.click(screen.getByRole('button', { name: /save draft/i }))

    await waitFor(() => expect(create).toHaveBeenCalled())
    const body = create.mock.calls[0][0]
    expect(body.contactPhone).toBe('95555 66666')
    expect(body.customerId).toBe(1)
    expect(body.customerPhone).toBeUndefined()
  })
})
