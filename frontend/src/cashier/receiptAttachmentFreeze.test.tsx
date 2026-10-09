import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import NewReceiptPage from './NewReceiptPage'
import { receiptsApi } from '../api/receipts'

vi.mock('../api/masters', () => {
  const ok = (rows: unknown[]) => Promise.resolve({ data: rows })
  const row = (id: number, name: string) => ({ id, name, active: true, requiresBank: false, isCash: name === 'Cash' })
  return {
    mastersApi: {
      list: vi.fn((key: string) => ok(
        key === 'settlement-modes' ? [row(1, 'Cash')] : key === 'receive-categories' ? [row(1, 'Workshop')] : [])),
      listSelectable: vi.fn(() => ok([row(1, 'Received')])),
    },
  }
})
vi.mock('../api/customers', () => ({
  customersApi: { search: vi.fn(() => Promise.resolve({ data: [] })), get: vi.fn(), vehicles: vi.fn(() => Promise.resolve({ data: [] })) },
}))
vi.mock('../api/vehicles', () => ({ vehiclesApi: { byNumber: vi.fn() } }))
vi.mock('../api/jobCards', () => ({
  jobCardsApi: { search: vi.fn(() => Promise.resolve({ data: [] })), get: vi.fn(), patch: vi.fn() },
}))
vi.mock('../api/receipts', () => ({ receiptsApi: { create: vi.fn(), get: vi.fn() } }))
// The panel itself is tested elsewhere; here only whether the form tells it the receipt is frozen.
vi.mock('./AttachmentsPanel', () => ({ default: (p: { frozen: boolean }) => <div data-testid="panel">frozen={String(p.frozen)}</div> }))
vi.mock('./PrintReceiptModal', () => ({ default: () => null }))
vi.mock('./UpiQrModal', () => ({ default: () => null }))

const get = vi.mocked(receiptsApi.get)

/** OOR-OCT26-R-011 as it is live: fully paid (settled) yet only QUERIED by the Accountant. */
function doc(workflowStatus: string, settled: boolean) {
  return {
    id: 123, documentNo: 'OOR-OCT26-R-011', workflowStatus, settled, jobCardId: 5, branchId: 3, branchCode: 'OOR',
    customerId: 3, customerName: 'Acme Transport', vehicleNo: 'OD33AB1234', contactPhone: null, chassisNo: null,
    dbmId: null, invoiceNo: null, invoiceAmount: 7656, b2b: false, gstNo: null, categoryId: 1, claimTypeId: null,
    businessStatusId: 1, businessStatusName: 'Received', history: [],
    lines: [{ id: 1, lineNo: 1, transactionDate: '2026-10-09', settlementModeId: 1, amount: 7656, bankId: null, transactionRef: null, remark: null }],
  }
}

async function frozenFor(workflowStatus: string, settled: boolean) {
  get.mockResolvedValue({ data: doc(workflowStatus, settled) } as never)
  render(<MemoryRouter initialEntries={['/app/new-receipt?editDoc=123']}><NewReceiptPage /></MemoryRouter>)
  await screen.findByText(/This entry was queried|Editing/)
  return screen.getByTestId('panel').textContent
}

describe('New Receipt: documents stay open until the receipt is approved (rev 72)', () => {
  beforeEach(() => vi.clearAllMocks())

  it('a fully paid but QUERIED receipt can take the missing bill (R-011)', async () => {
    expect(await frozenFor('QUERIED', true)).toBe('frozen=false')
  })

  it('a fully paid receipt waiting for the Finance Manager is still open', async () => {
    expect(await frozenFor('VERIFIED', true)).toBe('frozen=false')
  })

  it('an approved receipt is frozen', async () => {
    expect(await frozenFor('APPROVED', true)).toBe('frozen=true')
  })

  it('a rejected receipt is frozen', async () => {
    expect(await frozenFor('REJECTED', false)).toBe('frozen=true')
  })
})
