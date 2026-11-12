import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { sparePartsApi, type ImportPreview, type SpareAccess, type StockRow, type TransferRow } from '../api/spareParts'
import PermissionsSection from './PermissionsSection'
import RequestsPage from './RequestsPage'
import StockPage from './StockPage'
import UploadPage from './UploadPage'

// The access hook is replaced so each test chooses who is signed in; the access rules themselves are tested in SpareAccess.test.tsx.
const state = vi.hoisted(() => ({ access: null as unknown as SpareAccess }))
vi.mock('./SpareAccessContext', () => ({
  useSpareAccess: () => ({
    access: state.access,
    loading: false,
    reload: () => undefined,
    visible: state.access.enabled && state.access.canView,
    can: (p: string, b?: number | null) =>
      !state.access.owner && state.access.permissions.includes(p as never) && (b == null || state.access.actBranchIds.includes(b)),
  }),
}))
vi.mock('../api/spareParts', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/spareParts')>()),
  sparePartsApi: {
    stock: vi.fn(), transfers: vi.fn(), previewImport: vi.fn(), confirmImport: vi.fn(), addPart: vi.fn(),
    sendTransfer: vi.fn(), receiveTransfer: vi.fn(), declineTransfer: vi.fn(), cancelTransfer: vi.fn(), createTransfer: vi.fn(),
    permissions: vi.fn(), updatePermissions: vi.fn(), movements: vi.fn(), downloadStock: vi.fn(),
  },
}))

const OOJ = { id: 10, code: 'OOJ', name: 'Jeypore' }
const OOB = { id: 11, code: 'OOB', name: 'Berhampur' }
const OOR = { id: 12, code: 'OOR', name: 'Rayagada' }

function accessFor(over: Partial<SpareAccess>): SpareAccess {
  return {
    enabled: true, canView: true, canSeeDashboard: false, owner: false, role: 'STORE_KEEPER',
    actBranchIds: [12], permissions: ['BILL_PART', 'RAISE_REQUEST', 'SEND_RECEIVE'], branches: [OOJ, OOB, OOR], ...over,
  }
}

const fender: StockRow = {
  partId: 1, partCode: 'IA309072', description: 'Fender Assy RH 1114 Sleepar', unitPrice: 3885,
  cells: [{ branchId: 10, quantity: 3, bin: 'CORNER WALL' }], totalQuantity: 3, inTransit: 1, totalValue: 11655,
}
const washer: StockRow = {
  partId: 2, partCode: 'MF450155', description: 'PLAIN WASHER', unitPrice: 25,
  cells: [{ branchId: 12, quantity: 6, bin: 'DRAW-2' }], totalQuantity: 6, inTransit: 0, totalValue: 150,
}

function renderPage(ui: React.ReactElement) {
  return render(<MemoryRouter>{ui}</MemoryRouter>)
}

beforeEach(() => {
  vi.clearAllMocks()
  state.access = accessFor({})
  Element.prototype.scrollIntoView = vi.fn() // jsdom has none; ErrorBanner scrolls itself into view
})

describe('Stock screen', () => {
  beforeEach(() => {
    vi.mocked(sparePartsApi.stock).mockResolvedValue({ data: { rows: [fender, washer], truncated: false } } as never)
  })

  it('shows every branch as a column with what it holds, the bin, and parts that are on the road', async () => {
    renderPage(<StockPage />)

    const row = (await screen.findByText('IA309072')).closest('tr')!
    expect(within(row).getByText('CORNER WALL')).toBeInTheDocument()
    expect(within(row).getByText(/1 on the road/)).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'OOJ' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'OOB' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'OOR' })).toBeInTheDocument()
  })

  it('offers Bill only where the branch holds the part, and Ask a branch only where another branch does', async () => {
    renderPage(<StockPage />)

    const fenderRow = (await screen.findByText('IA309072')).closest('tr')!   // held by OOJ only; this keeper is at OOR
    expect(within(fenderRow).queryByRole('button', { name: 'Bill' })).not.toBeInTheDocument()
    expect(within(fenderRow).getByRole('button', { name: 'Ask a branch' })).toBeInTheDocument()

    const washerRow = screen.getByText('MF450155').closest('tr')!             // held by OOR itself
    expect(within(washerRow).getByRole('button', { name: 'Bill' })).toBeInTheDocument()
    expect(within(washerRow).queryByRole('button', { name: 'Ask a branch' })).not.toBeInTheDocument()
    expect(within(washerRow).queryByRole('button', { name: 'Correct' })).not.toBeInTheDocument()   // not ticked for a Store Keeper
  })

  it('gives the Owner a look at everything and no action button at all', async () => {
    state.access = accessFor({ owner: true, role: 'OWNER', actBranchIds: [], permissions: [] })
    renderPage(<StockPage />)

    await screen.findByText('IA309072')
    expect(screen.queryByRole('button', { name: 'Bill' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Ask a branch' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /export excel/i })).toBeInTheDocument()   // the Owner may export
  })

  it('shows the Export button to a Store Keeper only when the Owner has allowed it', async () => {
    renderPage(<StockPage />)
    await screen.findByText('IA309072')
    expect(screen.queryByRole('button', { name: /export excel/i })).not.toBeInTheDocument()
  })

  it('says how to get stock in when there is none', async () => {
    vi.mocked(sparePartsApi.stock).mockResolvedValue({ data: { rows: [], truncated: false } } as never)
    renderPage(<StockPage />)
    expect(await screen.findByText(/no stock has been added yet/i)).toBeInTheDocument()
  })
})

describe('Upload screen', () => {
  const preview: ImportPreview = {
    fileName: 'Spare parts.xlsx', mode: 'ADD', total: 2, ok: 1, errors: 1, newParts: 1,
    rows: [
      { rowNumber: 3, branchCode: 'OOR', partCode: 'IA306245', description: 'RUB-DRIP-LH', bin: 'BOX-2', quantity: '1', unitPrice: '700', billedQty: '', jobCard: '', currentQty: 0, resultQty: 1, newPart: true, error: null },
      { rowNumber: 4, branchCode: 'XYZ', partCode: 'IA306698', description: 'CAP L', bin: '', quantity: '1', unitPrice: '', billedQty: '', jobCard: '', currentQty: null, resultQty: null, newPart: true, error: "'XYZ' is not a branch of this organization" },
    ],
  }

  it('asks which mode every time, shows each row with its result, and posts only the good rows after confirmation', async () => {
    state.access = accessFor({ role: 'INVENTORY_MANAGER', permissions: ['UPLOAD_ADD', 'BILL_PART'], actBranchIds: [10, 11, 12] })
    vi.mocked(sparePartsApi.previewImport).mockResolvedValue({ data: preview } as never)
    vi.mocked(sparePartsApi.confirmImport).mockResolvedValue({ data: { importId: 1, posted: 1, rejected: 0, newParts: 1, rejectedRows: [] } } as never)
    const user = userEvent.setup()
    renderPage(<UploadPage />)

    expect(screen.getByRole('radio', { name: /add to stock/i })).toBeChecked()
    await user.click(screen.getByRole('radio', { name: /set stock to the file/i }))
    await user.upload(screen.getByLabelText('Excel file'), new File(['x'], 'Spare parts.xlsx'))
    await user.click(screen.getByRole('button', { name: 'Check the file' }))

    expect(sparePartsApi.previewImport).toHaveBeenCalledWith(expect.any(File), 'SET')
    expect(await screen.findByText("'XYZ' is not a branch of this organization")).toBeInTheDocument()
    expect(screen.getByText('1 with a problem')).toBeInTheDocument()
    expect(vi.mocked(sparePartsApi.confirmImport)).not.toHaveBeenCalled()   // nothing posts until confirmed

    await user.click(screen.getByRole('button', { name: 'Post 1 row' }))

    await waitFor(() => expect(sparePartsApi.confirmImport).toHaveBeenCalled())
    const [, , rows] = vi.mocked(sparePartsApi.confirmImport).mock.calls[0]
    expect(rows).toHaveLength(1)                                            // the bad row is not sent
    expect(rows[0].partCode).toBe('IA306245')
    expect(await screen.findByText('Upload finished')).toBeInTheDocument()
  })

  it('tells a Store Keeper that uploading is not allowed for their role', () => {
    renderPage(<UploadPage />)
    expect(screen.getByText(/not allowed to upload or add parts/i)).toBeInTheDocument()
    expect(screen.queryByLabelText('Excel file')).not.toBeInTheDocument()
  })

  it('shows the server’s reason when the file cannot be read', async () => {
    state.access = accessFor({ role: 'INVENTORY_MANAGER', permissions: ['UPLOAD_ADD'], actBranchIds: [12] })
    vi.mocked(sparePartsApi.previewImport).mockRejectedValue({ response: { data: { message: "No header row with 'PART ID' was found" } } })
    const user = userEvent.setup()
    renderPage(<UploadPage />)

    await user.upload(screen.getByLabelText('Excel file'), new File(['x'], 'bad.xlsx'))
    await user.click(screen.getByRole('button', { name: 'Check the file' }))

    expect(await screen.findByText(/no header row with 'part id'/i)).toBeInTheDocument()
  })
})

describe('Requests screen', () => {
  const base: TransferRow = {
    id: 12, requestNo: 'SP-12', status: 'REQUESTED', fromBranchId: 10, fromBranchCode: 'OOJ', toBranchId: 12, toBranchCode: 'OOR',
    partId: 1, partCode: 'IA309072', description: 'Fender Assy RH', qtyRequested: 1, qtySent: null, jobCardNo: '4010614834', note: null,
    declineReason: null, requestedByName: 'Ajay', requestedAt: '2026-11-10T08:00:00Z', sentByName: null, sentAt: null,
    receivedByName: null, receivedAt: null, canSend: false, canReceive: false, canDecline: false, canCancel: false,
  }

  it('shows only the buttons the server said this person may use', async () => {
    vi.mocked(sparePartsApi.transfers).mockResolvedValue({
      data: [{ ...base, canSend: true, canDecline: true }, { ...base, id: 13, requestNo: 'SP-13', status: 'SENT', qtySent: 1, canReceive: true }, { ...base, id: 14, requestNo: 'SP-14', status: 'RECEIVED', qtySent: 1 }],
    } as never)
    renderPage(<RequestsPage />)

    const first = (await screen.findByText('SP-12')).closest('article')!
    expect(within(first).getByRole('button', { name: 'Mark as sent' })).toBeInTheDocument()
    expect(within(first).getByRole('button', { name: 'Decline' })).toBeInTheDocument()
    expect(within(first).queryByRole('button', { name: 'Mark as received' })).not.toBeInTheDocument()

    const second = screen.getByText('SP-13').closest('article')!
    expect(within(second).getByRole('button', { name: 'Mark as received' })).toBeInTheDocument()
    expect(within(second).getByText('On the road')).toBeInTheDocument()

    const third = screen.getByText('SP-14').closest('article')!
    expect(within(third).queryByRole('button')).not.toBeInTheDocument()
  })

  it('marks a part received with one click and reloads', async () => {
    vi.mocked(sparePartsApi.transfers).mockResolvedValue({ data: [{ ...base, status: 'SENT', qtySent: 1, canReceive: true }] } as never)
    vi.mocked(sparePartsApi.receiveTransfer).mockResolvedValue({ data: {} } as never)
    const user = userEvent.setup()
    renderPage(<RequestsPage />)

    await user.click(await screen.findByRole('button', { name: 'Mark as received' }))

    await waitFor(() => expect(sparePartsApi.receiveTransfer).toHaveBeenCalledWith(12))
    expect(await screen.findByText(/SP-12 received/)).toBeInTheDocument()
  })

  it('will not decline without a reason, and sends the reason when given', async () => {
    vi.mocked(sparePartsApi.transfers).mockResolvedValue({ data: [{ ...base, canDecline: true }] } as never)
    vi.mocked(sparePartsApi.declineTransfer).mockResolvedValue({ data: {} } as never)
    const user = userEvent.setup()
    renderPage(<RequestsPage />)

    await user.click(await screen.findByRole('button', { name: 'Decline' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByLabelText(/reason/i)).toBeRequired()          // the form blocks an empty reason
    await user.type(within(dialog).getByLabelText(/reason/i), 'Reserved for a customer')
    await user.click(within(dialog).getByRole('button', { name: 'Decline' }))

    await waitFor(() => expect(sparePartsApi.declineTransfer).toHaveBeenCalledWith(12, 'Reserved for a customer'))
  })

  it('lets a Store Keeper start a request but not an Owner', async () => {
    vi.mocked(sparePartsApi.transfers).mockResolvedValue({ data: [] } as never)
    const { unmount } = renderPage(<RequestsPage />)
    expect(await screen.findByRole('button', { name: '+ New request' })).toBeInTheDocument()
    unmount()

    state.access = accessFor({ owner: true, role: 'OWNER', actBranchIds: [], permissions: [] })
    renderPage(<RequestsPage />)
    await screen.findByText(/no branch has asked/i)
    expect(screen.queryByRole('button', { name: '+ New request' })).not.toBeInTheDocument()
  })
})

describe('Masters: Spare Parts permissions', () => {
  const grid = {
    permissions: [{ key: 'BILL_PART', label: 'Bill a part to a job card' }, { key: 'CORRECT_QTY', label: 'Correct a quantity' }],
    roles: [
      { role: 'INVENTORY_MANAGER', label: 'Inventory Manager', values: { BILL_PART: true, CORRECT_QTY: true } },
      { role: 'STORE_KEEPER', label: 'Store Keeper', values: { BILL_PART: true, CORRECT_QTY: false } },
    ],
  }

  it('sends only the cells the Owner changed', async () => {
    state.access = accessFor({ owner: true, role: 'OWNER', actBranchIds: [], permissions: [] })
    vi.mocked(sparePartsApi.permissions).mockResolvedValue({ data: grid } as never)
    vi.mocked(sparePartsApi.updatePermissions).mockResolvedValue({
      data: { ...grid, roles: [grid.roles[0], { ...grid.roles[1], values: { BILL_PART: true, CORRECT_QTY: true } }] },
    } as never)
    const user = userEvent.setup()
    render(<PermissionsSection />)

    const tick = await screen.findByRole('checkbox', { name: 'Store Keeper: Correct a quantity' })
    expect(tick).not.toBeChecked()
    expect(screen.getByRole('button', { name: /save changes/i })).toBeDisabled()    // nothing changed yet

    await user.click(tick)
    await user.click(screen.getByRole('button', { name: /save changes/i }))

    await waitFor(() => expect(sparePartsApi.updatePermissions).toHaveBeenCalledWith({ STORE_KEEPER: { CORRECT_QTY: true } }))
    expect(await screen.findByText(/saved/i)).toBeInTheDocument()
  })

  it('renders nothing for anyone but the Owner, and nothing while the module is off', () => {
    state.access = accessFor({})   // a Store Keeper
    const { container, unmount } = render(<PermissionsSection />)
    expect(container).toBeEmptyDOMElement()
    unmount()

    state.access = accessFor({ enabled: false, owner: true, role: 'OWNER' })
    const off = render(<PermissionsSection />)
    expect(off.container).toBeEmptyDOMElement()
    expect(sparePartsApi.permissions).not.toHaveBeenCalled()
  })
})
