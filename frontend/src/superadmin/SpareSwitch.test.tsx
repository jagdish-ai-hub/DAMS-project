import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { adminApi, type Organization } from '../api/admin'
import OrganizationsPage from './OrganizationsPage'

vi.mock('../api/admin', () => ({
  adminApi: {
    listOrganizations: vi.fn(), createOrganization: vi.fn(), toggleActive: vi.fn(), deleteOrganization: vi.fn(), setSparePartsEnabled: vi.fn(),
  },
}))

const org = (id: number, name: string, sparePartsEnabled: boolean): Organization => ({
  id, name, multiBranchCashierAccess: false, sparePartsEnabled, active: true, createdAt: '2026-09-01T00:00:00Z',
})

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(adminApi.listOrganizations).mockResolvedValue({ data: [org(1, 'OORIBA MOTORS DEMO', false), org(2, 'OORIBA MOTORS', false)] } as never)
  vi.mocked(adminApi.setSparePartsEnabled).mockResolvedValue({ data: {} } as never)
})

describe('Super Admin: Spare Parts switch (rev 91)', () => {
  it('shows the module as Off for every organization until it is turned on', async () => {
    render(<MemoryRouter><OrganizationsPage /></MemoryRouter>)

    const demo = await screen.findByRole('switch', { name: 'Spare Parts for OORIBA MOTORS DEMO' })
    expect(demo).toHaveAttribute('aria-checked', 'false')
    expect(screen.getByRole('switch', { name: 'Spare Parts for OORIBA MOTORS' })).toHaveAttribute('aria-checked', 'false')
  })

  it('asks before turning it on for a live organization, and changes nothing if cancelled', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter><OrganizationsPage /></MemoryRouter>)

    await user.click(await screen.findByRole('switch', { name: 'Spare Parts for OORIBA MOTORS' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText('Turn Spare Parts on?')).toBeInTheDocument()
    expect(within(dialog).getByText('OORIBA MOTORS')).toBeInTheDocument()

    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }))
    expect(adminApi.setSparePartsEnabled).not.toHaveBeenCalled()
  })

  it('turns it on for the chosen organization only, after confirmation', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter><OrganizationsPage /></MemoryRouter>)

    await user.click(await screen.findByRole('switch', { name: 'Spare Parts for OORIBA MOTORS DEMO' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Turn on' }))

    await waitFor(() => expect(adminApi.setSparePartsEnabled).toHaveBeenCalledWith(1, true))
    expect(adminApi.setSparePartsEnabled).toHaveBeenCalledTimes(1)
  })

  it('says nothing is deleted when turning it off', async () => {
    vi.mocked(adminApi.listOrganizations).mockResolvedValue({ data: [org(1, 'OORIBA MOTORS DEMO', true)] } as never)
    const user = userEvent.setup()
    render(<MemoryRouter><OrganizationsPage /></MemoryRouter>)

    await user.click(await screen.findByRole('switch', { name: 'Spare Parts for OORIBA MOTORS DEMO' }))
    const dialog = await screen.findByRole('dialog')

    expect(within(dialog).getByText('Turn Spare Parts off?')).toBeInTheDocument()
    expect(within(dialog).getByText(/nothing is deleted/i)).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Turn off' }))
    await waitFor(() => expect(adminApi.setSparePartsEnabled).toHaveBeenCalledWith(1, false))
  })
})
