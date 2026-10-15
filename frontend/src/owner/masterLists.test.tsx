import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ReceiversSection from './ReceiversSection'
import CustomersSection from './CustomersSection'
import { receiversApi } from '../api/receivers'
import { customersApi } from '../api/customers'
import { vehiclesApi } from '../api/vehicles'

vi.mock('../api/masters', () => ({
  mastersApi: { list: vi.fn(() => Promise.resolve({ data: [{ id: 1, name: 'Workshop', active: true }] })) },
}))
vi.mock('../api/receivers', () => ({ receiversApi: { list: vi.fn(), create: vi.fn(), update: vi.fn() } }))
vi.mock('../api/customers', () => ({ customersApi: { master: vi.fn(), create: vi.fn(), update: vi.fn() } }))
vi.mock('../api/vehicles', () => ({ vehiclesApi: { rename: vi.fn(), create: vi.fn(), move: vi.fn() } }))

const vendor = (id: number, name: string, phone: string | null) =>
  ({ id, name, phone, active: true, createdAt: '', categoryIds: [] })
const customer = (id: number, name: string, phone: string | null, address: string | null, vehicles: [number, string][]) =>
  ({ id, name, phone, address, createdAt: '', vehicles: vehicles.map(([vid, no]) => ({ id: vid, vehicleNo: no })) })

describe('Vendor list search (rev 77)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(receiversApi.list).mockResolvedValue({
      data: [vendor(1, 'Sharma Auto Spares', '98765 43210'), vendor(2, 'City Fuel Station', null), vendor(3, 'Quick Courier', '94370-22222')],
    } as never)
  })

  it('filters by name, and by phone number whatever the spacing', async () => {
    const user = userEvent.setup()
    render(<ReceiversSection />)
    expect(await screen.findByText('Sharma Auto Spares')).toBeInTheDocument()
    const box = screen.getByLabelText('Search vendors')

    await user.type(box, 'fuel')
    expect(screen.queryByText('Sharma Auto Spares')).not.toBeInTheDocument()
    expect(screen.getByText('City Fuel Station')).toBeInTheDocument()

    await user.clear(box)
    await user.type(box, '9437022')
    expect(screen.getByText('Quick Courier')).toBeInTheDocument()
    expect(screen.queryByText('City Fuel Station')).not.toBeInTheDocument()

    await user.clear(box)
    await user.type(box, '98765 4')
    expect(screen.getByText('Sharma Auto Spares')).toBeInTheDocument()
    expect(screen.queryByText('Quick Courier')).not.toBeInTheDocument()
  })

  it('says so when nothing matches', async () => {
    const user = userEvent.setup()
    render(<ReceiversSection />)
    await screen.findByText('Sharma Auto Spares')
    await user.type(screen.getByLabelText('Search vendors'), 'zzz')
    expect(screen.getByText('No vendor matches your search.')).toBeInTheDocument()
  })
})

describe('Customers master (rev 77)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(customersApi.master).mockResolvedValue({
      data: [
        customer(1, 'Sumati Sahoo', '98765 43210', '12 Station Road, Rayagada', [[10, 'OD05CA4177'], [11, 'OD33AB1234']]),
        customer(2, 'Kalaivani Logistics', '94370 11111', null, [[12, 'TN09ZZ0001']]),
        customer(3, 'Walk-in', null, null, []),
      ],
    } as never)
    vi.mocked(customersApi.update).mockResolvedValue({ data: { id: 1 } } as never)
    vi.mocked(customersApi.create).mockResolvedValue({ data: { id: 9 } } as never)
    vi.mocked(vehiclesApi.rename).mockResolvedValue({ data: {} } as never)
    vi.mocked(vehiclesApi.create).mockResolvedValue({ data: {} } as never)
    vi.mocked(vehiclesApi.move).mockResolvedValue({ data: {} } as never)
  })

  it('lists name, mobile, vehicles and address', async () => {
    render(<CustomersSection />)
    const row = (await screen.findByText('Sumati Sahoo')).closest('tr') as HTMLElement
    expect(within(row).getByText('98765 43210')).toBeInTheDocument()
    expect(within(row).getByText('OD05CA4177')).toBeInTheDocument()
    expect(within(row).getByText('OD33AB1234')).toBeInTheDocument()
    expect(within(row).getByText('12 Station Road, Rayagada')).toBeInTheDocument()
  })

  it('searches by name, mobile, a vehicle number typed with spaces, and address', async () => {
    const user = userEvent.setup()
    render(<CustomersSection />)
    await screen.findByText('Sumati Sahoo')
    const box = screen.getByLabelText('Search customers')
    const only = async (text: string, present: string, absent: string) => {
      await user.clear(box)
      await user.type(box, text)
      expect(screen.getByText(present)).toBeInTheDocument()
      expect(screen.queryByText(absent)).not.toBeInTheDocument()
    }
    await only('kalai', 'Kalaivani Logistics', 'Sumati Sahoo')
    await only('98765', 'Sumati Sahoo', 'Kalaivani Logistics')
    await only('tn09 zz', 'Kalaivani Logistics', 'Sumati Sahoo')
    await only('rayagada', 'Sumati Sahoo', 'Walk-in')
  })

  it('edits the name, mobile, address and a vehicle number, and adds a vehicle', async () => {
    const user = userEvent.setup()
    render(<CustomersSection />)
    const row = (await screen.findByText('Sumati Sahoo')).closest('tr') as HTMLElement
    await user.click(within(row).getByRole('button', { name: 'Edit' }))

    const dialog = await screen.findByRole('dialog').catch(() => document.body)
    const d = within(dialog as HTMLElement)
    await user.clear(d.getByPlaceholderText('Customer name'))
    await user.type(d.getByPlaceholderText('Customer name'), 'Sumati S. Sahoo')
    await user.clear(d.getByPlaceholderText('Street, town, PIN'))
    await user.type(d.getByPlaceholderText('Street, town, PIN'), 'New address')
    await user.clear(d.getByLabelText('Vehicle OD05CA4177'))
    await user.type(d.getByLabelText('Vehicle OD05CA4177'), 'od05 ca 9999')
    await user.click(d.getByRole('button', { name: '+ Add vehicle' }))
    await user.type(d.getByLabelText('New vehicle number'), 'OR 02 AA 1')
    await user.click(d.getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(customersApi.update).toHaveBeenCalledWith(1, {
      name: 'Sumati S. Sahoo', phone: '98765 43210', address: 'New address',
    }))
    expect(vehiclesApi.rename).toHaveBeenCalledTimes(1)
    expect(vehiclesApi.rename).toHaveBeenCalledWith(10, 'OD05CA9999')
    expect(vehiclesApi.create).toHaveBeenCalledWith({ customerId: 1, vehicleNo: 'OR02AA1' })
  })

  it('adds a new customer with an address', async () => {
    const user = userEvent.setup()
    render(<CustomersSection />)
    await screen.findByText('Sumati Sahoo')
    await user.click(screen.getByRole('button', { name: '+ Add' }))
    await user.type(await screen.findByPlaceholderText('Customer name'), 'New Customer')
    await user.type(screen.getByPlaceholderText('Street, town, PIN'), '5 Market Road')
    await user.click(screen.getByRole('button', { name: 'Add customer' }))

    await waitFor(() => expect(customersApi.create).toHaveBeenCalledWith({
      name: 'New Customer', phone: undefined, address: '5 Market Road',
    }))
  })

  it('moves a vehicle to another customer, saying first that past records stay put', async () => {
    const user = userEvent.setup()
    render(<CustomersSection />)
    const row = (await screen.findByText('Sumati Sahoo')).closest('tr') as HTMLElement
    await user.click(within(row).getByRole('button', { name: 'Edit' }))
    await user.click(await screen.findByRole('button', { name: 'Move OD33AB1234 to another customer' }))

    await user.type(screen.getByLabelText('Search customer to move to'), 'kalai')
    expect(screen.queryByRole('button', { name: /Sumati Sahoo/ })).not.toBeInTheDocument() // not offered back to the same owner
    await user.click(await screen.findByRole('button', { name: /Kalaivani Logistics/ }))

    expect(screen.getByText(/Will move to/)).toHaveTextContent('Kalaivani Logistics')
    expect(screen.getByText(/Past records stay with Sumati Sahoo/)).toBeInTheDocument()
    expect(vehiclesApi.move).not.toHaveBeenCalled() // nothing happens until Save

    await user.click(screen.getByRole('button', { name: 'Save changes' }))
    await waitFor(() => expect(vehiclesApi.move).toHaveBeenCalledWith(11, 2))
    expect(vehiclesApi.rename).not.toHaveBeenCalled()
  })

  it('Undo cancels a pending move', async () => {
    const user = userEvent.setup()
    render(<CustomersSection />)
    const row = (await screen.findByText('Sumati Sahoo')).closest('tr') as HTMLElement
    await user.click(within(row).getByRole('button', { name: 'Edit' }))
    await user.click(await screen.findByRole('button', { name: 'Move OD33AB1234 to another customer' }))
    await user.type(screen.getByLabelText('Search customer to move to'), 'kalai')
    await user.click(await screen.findByRole('button', { name: /Kalaivani Logistics/ }))
    await user.click(screen.getByRole('button', { name: 'Undo' }))
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(customersApi.update).toHaveBeenCalled())
    expect(vehiclesApi.move).not.toHaveBeenCalled()
  })
})
