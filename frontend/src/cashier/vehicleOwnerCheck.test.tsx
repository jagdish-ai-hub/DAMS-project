import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import NewReceiptPage from './NewReceiptPage'
import { customersApi } from '../api/customers'
import { vehiclesApi } from '../api/vehicles'
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
  customersApi: { search: vi.fn(), get: vi.fn(), update: vi.fn(), vehicles: vi.fn(() => Promise.resolve({ data: [] })) },
}))
vi.mock('../api/vehicles', () => ({ vehiclesApi: { byNumber: vi.fn() } }))
vi.mock('../api/jobCards', () => ({
  jobCardsApi: { search: vi.fn(() => Promise.resolve({ data: [] })), get: vi.fn(), patch: vi.fn() },
}))
vi.mock('../api/receipts', () => ({ receiptsApi: { create: vi.fn(), get: vi.fn() } }))
vi.mock('./AttachmentsPanel', () => ({ default: () => null }))
vi.mock('./PrintReceiptModal', () => ({ default: () => null }))
vi.mock('./UpiQrModal', () => ({ default: () => null }))

const search = vi.mocked(customersApi.search)
const getCustomer = vi.mocked(customersApi.get)
const updateCustomer = vi.mocked(customersApi.update)
const byNumber = vi.mocked(vehiclesApi.byNumber)
const create = vi.mocked(receiptsApi.create)

/** XYZ Transport owns OD33AB1234 on record. */
const xyzVehicle = { id: 70, vehicleNo: 'OD33AB1234', customerId: 7, customerName: 'XYZ Transport', createdAt: '' }
const xyz = { id: 7, name: 'XYZ Transport', phone: '70000 00000', vehicles: [], createdAt: '' }
const other = { id: 2, name: 'Other Motors', phone: null, vehicles: [], createdAt: '' }

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/app/new-receipt']}>
      <NewReceiptPage />
    </MemoryRouter>,
  )
}

type User = ReturnType<typeof userEvent.setup>

async function typeNameAndVehicle(user: User, name: string, vehicle: string) {
  await user.type(screen.getByLabelText('Customer name'), name)
  await user.type(screen.getByLabelText('Vehicle number'), vehicle)
}

describe('Vehicle on record under a different name (rev 70)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    search.mockResolvedValue({ data: [xyz, other] } as never)
    byNumber.mockResolvedValue({ data: xyzVehicle } as never)
    getCustomer.mockResolvedValue({ data: xyz } as never)
    updateCustomer.mockResolvedValue({ data: xyz } as never)
    create.mockResolvedValue({ data: { id: 9, documentNo: null, lines: [], workflowStatus: 'DRAFT' } } as never)
  })

  it('does not interrupt while typing — nothing is asked until Save', async () => {
    const user = userEvent.setup()
    renderPage()
    await typeNameAndVehicle(user, 'BCD Motors', 'OD33AB1234')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(create).not.toHaveBeenCalled()
  })

  it('on Save, asks instead of saving silently — two option boxes, Proceed disabled until one is chosen', async () => {
    const user = userEvent.setup()
    renderPage()
    await typeNameAndVehicle(user, 'BCD Motors', 'OD33AB1234')
    await user.click(screen.getByRole('button', { name: /save draft/i }))

    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent('OD33AB1234 is registered to XYZ Transport, but you entered BCD Motors')
    expect(screen.getAllByRole('radio')).toHaveLength(2)
    expect(screen.getByRole('button', { name: 'Proceed' })).toBeDisabled()
    expect(create).not.toHaveBeenCalled()
  })

  it('"XYZ is correct" saves under the customer on record and ignores the typed name', async () => {
    const user = userEvent.setup()
    renderPage()
    await typeNameAndVehicle(user, 'BCD Motors', 'OD33AB1234')
    await user.click(screen.getByRole('button', { name: /save draft/i }))
    await user.click(await screen.findByRole('radio', { name: /XYZ Transport is correct/ }))
    await user.click(screen.getByRole('button', { name: 'Proceed' }))

    await waitFor(() => expect(create).toHaveBeenCalledTimes(1))
    const body = create.mock.calls[0][0]
    expect(body.customerId).toBe(7)
    expect(body.vehicleId).toBe(70)
    expect(body.customerName).toBeUndefined()
    expect(updateCustomer).not.toHaveBeenCalled()
  })

  it('"Update the name to BCD" renames the customer (keeping their phone), then saves under them', async () => {
    const user = userEvent.setup()
    renderPage()
    await typeNameAndVehicle(user, 'BCD Motors', 'OD33AB1234')
    await user.click(screen.getByRole('button', { name: /save draft/i }))
    await user.click(await screen.findByRole('radio', { name: /Update the name to BCD Motors/ }))
    await user.click(screen.getByRole('button', { name: 'Proceed' }))

    await waitFor(() => expect(create).toHaveBeenCalledTimes(1))
    expect(updateCustomer).toHaveBeenCalledWith(7, { name: 'BCD Motors', phone: '70000 00000' })
    const body = create.mock.calls[0][0]
    expect(body.customerId).toBe(7)
    expect(body.vehicleId).toBe(70)
  })

  it('Cancel saves nothing', async () => {
    const user = userEvent.setup()
    renderPage()
    await typeNameAndVehicle(user, 'BCD Motors', 'OD33AB1234')
    await user.click(screen.getByRole('button', { name: /save draft/i }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Cancel' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(create).not.toHaveBeenCalled()
    expect(updateCustomer).not.toHaveBeenCalled()
  })

  it('a different PICKED customer is offered no rename — only the customer on record', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.click(screen.getByLabelText('Customer name'))
    await user.type(screen.getByLabelText('Customer name'), 'Oth')
    await user.click(await screen.findByRole('button', { name: /Other Motors/ }))
    await user.type(screen.getByLabelText('Vehicle number'), 'OD33AB1234')
    await user.click(screen.getByRole('button', { name: /save draft/i }))

    await screen.findByRole('dialog')
    expect(screen.getAllByRole('radio')).toHaveLength(1)
    expect(screen.queryByRole('radio', { name: /Update the name/ })).not.toBeInTheDocument()
  })

  it('saves straight away when the name matches the owner (ignoring case and spaces)', async () => {
    const user = userEvent.setup()
    renderPage()
    await typeNameAndVehicle(user, ' xyz   transport ', 'OD33AB1234')
    await user.click(screen.getByRole('button', { name: /save draft/i }))

    await waitFor(() => expect(create).toHaveBeenCalledTimes(1))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('saves straight away for a brand-new vehicle number', async () => {
    byNumber.mockRejectedValue({ response: { status: 404 } })
    const user = userEvent.setup()
    renderPage()
    await typeNameAndVehicle(user, 'BCD Motors', 'OD99ZZ0001')
    await user.click(screen.getByRole('button', { name: /save draft/i }))

    await waitFor(() => expect(create).toHaveBeenCalledTimes(1))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(create.mock.calls[0][0].customerName).toBe('BCD Motors')
  })
})
