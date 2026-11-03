import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import NewReceiptPage from './NewReceiptPage'
import { customersApi } from '../api/customers'

vi.mock('../api/masters', () => {
  const ok = (rows: unknown[]) => Promise.resolve({ data: rows })
  const row = (id: number, name: string) => ({ id, name, active: true, requiresBank: false, isCash: name === 'Cash' })
  return {
    mastersApi: {
      list: vi.fn((key: string) => ok(
        key === 'settlement-modes' ? [row(1, 'Cash')]
          : key === 'receive-categories' ? [row(1, 'Workshop')]
          : key === 'claim-types' ? [row(7, 'Claim')]
          : [])),
      listSelectable: vi.fn(() => ok([row(22, 'Received'), row(4, 'Awaiting Claim'), row(6, 'Credit')])),
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
vi.mock('../api/receipts', () => ({ receiptsApi: { create: vi.fn(), get: vi.fn() } }))
vi.mock('./AttachmentsPanel', () => ({ default: () => null }))
vi.mock('./PrintReceiptModal', () => ({ default: () => null }))
vi.mock('./UpiQrModal', () => ({ default: () => null }))

const search = vi.mocked(customersApi.search)

const withGst = { id: 1, name: 'Kalaivani Logistics', phone: '94370 11111', vehicles: [], createdAt: '', lastContactPhone: null, gstNo: '21ABCDE1234F1Z5' }
const noGst = { id: 2, name: 'Beta Motors', phone: '88888 00000', vehicles: [], createdAt: '', lastContactPhone: null, gstNo: null }

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/app/new-receipt']}>
      <NewReceiptPage />
    </MemoryRouter>,
  )
}

const statusSelect = () => screen.getAllByRole('combobox').find((s) => within(s).queryByText('Awaiting Claim') != null) as HTMLSelectElement

describe('New Receipt: claim type sets the status (rev 87)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    search.mockResolvedValue({ data: [withGst, noGst] } as never)
  })

  it('picking a Claim Type moves the status to Awaiting Claim, and going back restores it', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('— Not a claim —')
    expect(statusSelect().selectedOptions[0].textContent).toBe('Received')

    await user.selectOptions(screen.getByDisplayValue('— Not a claim —'), 'Claim')
    expect(statusSelect().selectedOptions[0].textContent).toBe('Awaiting Claim')

    await user.selectOptions(screen.getByDisplayValue('Claim'), '— Not a claim —')
    expect(statusSelect().selectedOptions[0].textContent).toBe('Received')
  })

  it('does not undo a status the cashier chose themselves after the claim type', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('— Not a claim —')
    await user.selectOptions(screen.getByDisplayValue('— Not a claim —'), 'Claim')
    await user.selectOptions(statusSelect(), 'Credit')

    await user.selectOptions(screen.getByDisplayValue('Claim'), '— Not a claim —')
    expect(statusSelect().selectedOptions[0].textContent).toBe('Credit')
  })
})

describe('New Receipt: GST fills from the customer (rev 87)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    search.mockResolvedValue({ data: [withGst, noGst] } as never)
  })

  async function pick(user: ReturnType<typeof userEvent.setup>, typed: string, optionName: string) {
    await user.click(screen.getByLabelText('Customer name'))
    await user.type(screen.getByLabelText('Customer name'), typed)
    await user.click(await screen.findByRole('button', { name: new RegExp(optionName) }))
  }

  it('a customer with a GST No fills the GST# box and makes the receipt B2B', async () => {
    const user = userEvent.setup()
    renderPage()
    expect(screen.queryByPlaceholderText('21ABCDE1234F1Z5')).not.toBeInTheDocument()
    await pick(user, 'Kal', 'Kalaivani Logistics')
    expect(await screen.findByPlaceholderText('21ABCDE1234F1Z5')).toHaveValue('21ABCDE1234F1Z5')
  })

  it('a customer without one leaves the receipt B2C', async () => {
    const user = userEvent.setup()
    renderPage()
    await pick(user, 'Bet', 'Beta Motors')
    expect(screen.queryByPlaceholderText('21ABCDE1234F1Z5')).not.toBeInTheDocument()
  })
})
