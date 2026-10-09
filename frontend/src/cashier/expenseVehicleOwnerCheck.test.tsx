import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import NewExpensePage from './NewExpensePage'
import { customersApi } from '../api/customers'
import { vehiclesApi } from '../api/vehicles'
import { expensesApi } from '../api/expenses'

vi.mock('../api/masters', () => {
  const ok = (rows: unknown[]) => Promise.resolve({ data: rows })
  const row = (id: number, name: string) => ({ id, name, active: true, requiresBank: false, requiresRef: false, isCash: true })
  return {
    mastersApi: {
      list: vi.fn((key: string) => ok(
        key === 'expense-categories' ? [row(1, 'Workshop')]
          : key === 'expense-statuses' ? [row(1, 'Received')]
          : [])),
      listSelectable: vi.fn(() => ok([row(1, 'Received')])),
    },
  }
})
vi.mock('../api/customers', () => ({
  customersApi: { search: vi.fn(), get: vi.fn(), update: vi.fn(), vehicles: vi.fn(() => Promise.resolve({ data: [] })) },
}))
vi.mock('../api/vehicles', () => ({ vehiclesApi: { byNumber: vi.fn() } }))
vi.mock('../api/jobCards', () => ({
  jobCardsApi: { search: vi.fn(() => Promise.resolve({ data: [] })), get: vi.fn(), patch: vi.fn() },
}))
vi.mock('../api/expenses', () => ({ expensesApi: { create: vi.fn(), get: vi.fn() } }))
vi.mock('./AttachmentsPanel', () => ({ default: () => null }))

const getCustomer = vi.mocked(customersApi.get)
const updateCustomer = vi.mocked(customersApi.update)
const search = vi.mocked(customersApi.search)
const byNumber = vi.mocked(vehiclesApi.byNumber)
const create = vi.mocked(expensesApi.create)

const xyz = { id: 7, name: 'XYZ Transport', phone: '70000 00000', vehicles: [], createdAt: '' }

async function fillAndSave(user: ReturnType<typeof userEvent.setup>) {
  render(<MemoryRouter initialEntries={['/app/new-expense']}><NewExpensePage /></MemoryRouter>)
  await user.type(await screen.findByPlaceholderText('Vendor / payee name'), 'Some Vendor')
  await user.type(screen.getByLabelText('Customer name'), 'BCD Motors')
  await user.type(screen.getByLabelText('Vehicle number'), 'OD33AB1234')
  await user.click(screen.getByRole('button', { name: 'Save Draft' }))
}

describe('Expense: vehicle on record under a different name (rev 70)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    search.mockResolvedValue({ data: [xyz] } as never)
    byNumber.mockResolvedValue({ data: { id: 70, vehicleNo: 'OD33AB1234', customerId: 7, customerName: 'XYZ Transport', createdAt: '' } } as never)
    getCustomer.mockResolvedValue({ data: xyz } as never)
    updateCustomer.mockResolvedValue({ data: xyz } as never)
    create.mockResolvedValue({ data: { id: 9, documentNo: null, lines: [], workflowStatus: 'DRAFT' } } as never)
  })

  it('asks on Save instead of saving silently', async () => {
    const user = userEvent.setup()
    await fillAndSave(user)
    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent('OD33AB1234 is registered to XYZ Transport, but you entered BCD Motors')
    expect(create).not.toHaveBeenCalled()
  })

  it('"Update the name to BCD" renames, then saves under the customer on record', async () => {
    const user = userEvent.setup()
    await fillAndSave(user)
    await user.click(await screen.findByRole('radio', { name: /Update the name to BCD Motors/ }))
    await user.click(screen.getByRole('button', { name: 'Proceed' }))

    await waitFor(() => expect(create).toHaveBeenCalledTimes(1))
    expect(updateCustomer).toHaveBeenCalledWith(7, { name: 'BCD Motors', phone: '70000 00000' })
    const body = create.mock.calls[0][0]
    expect(body.customerId).toBe(7)
    expect(body.vehicleId).toBe(70)
    expect(body.newCustomerName).toBeUndefined()
    expect(body.newVehicleNo).toBeUndefined()
  })
})
