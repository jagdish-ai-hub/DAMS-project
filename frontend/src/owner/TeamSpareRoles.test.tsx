import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { branchesApi } from '../api/branches'
import { usersApi } from '../api/users'
import TeamAndBranchesPage from './TeamAndBranchesPage'

// Only the answer to "is Spare Parts on?" is replaced; the rest of the page is real.
const state = vi.hoisted(() => ({ enabled: false }))
vi.mock('../spareparts/SpareAccessContext', () => ({
  useSpareAccess: () => ({ access: { enabled: state.enabled }, loading: false, reload: () => undefined, visible: state.enabled, can: () => false }),
}))
vi.mock('../api/branches', () => ({ branchesApi: { list: vi.fn(), create: vi.fn(), update: vi.fn() } }))
vi.mock('../api/users', () => ({ usersApi: { list: vi.fn(), create: vi.fn(), update: vi.fn() } }))

const branch = (id: number, code: string) => ({ id, name: `${code} branch`, code, active: true, assignedUserCount: 0, createdAt: '' })

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(branchesApi.list).mockResolvedValue({ data: [branch(10, 'OOJ'), branch(11, 'OOB'), branch(12, 'OOR')] } as never)
  vi.mocked(usersApi.list).mockResolvedValue({ data: [] } as never)
})

async function openAddUser() {
  const user = userEvent.setup()
  render(<MemoryRouter><TeamAndBranchesPage /></MemoryRouter>)
  await user.click(await screen.findByRole('button', { name: '+ Add User' }))
  return { user, dialog: await screen.findByRole('dialog') }
}

describe('Team & Branches with Spare Parts (rev 91)', () => {
  it('offers the two Spare Parts roles only while the module is on', async () => {
    state.enabled = false
    const off = await openAddUser()
    const offOptions = within(within(off.dialog).getByLabelText('Role')).getAllByRole('option').map((o) => o.textContent)
    expect(offOptions).toEqual(['Owner', 'Finance Manager', 'Accountant', 'Cashier'])   // exactly as before the module existed
  })

  it('lists Inventory Manager and Store Keeper once the module is on, and asks for their branches like an Accountant', async () => {
    state.enabled = true
    const { user, dialog } = await openAddUser()
    const role = within(dialog).getByLabelText('Role')
    expect(within(role).getAllByRole('option').map((o) => o.textContent)).toEqual(
      ['Owner', 'Finance Manager', 'Accountant', 'Cashier', 'Inventory Manager', 'Store Keeper'])

    await user.selectOptions(role, 'STORE_KEEPER')

    expect(within(dialog).getByText('Branches assigned')).toBeInTheDocument()    // one or more branches, not a single home branch
    // (the whole group sits inside one <label>, so the boxes are identified by their own label text)
    const branchBoxes = within(dialog).getAllByRole('checkbox').map((c) => c.parentElement?.textContent)
    expect(branchBoxes).toEqual(expect.arrayContaining(['OOJ branch (OOJ)', 'OOB branch (OOB)', 'OOR branch (OOR)']))
  })
})
