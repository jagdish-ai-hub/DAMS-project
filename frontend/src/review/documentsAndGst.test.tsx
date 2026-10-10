import { render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { RecordCard, type AnyDoc } from './reviewShared'
import { receiptsApi } from '../api/receipts'
import { expensesApi } from '../api/expenses'

vi.mock('../api/receipts', () => ({
  receiptsApi: {
    documentAttachments: vi.fn(), lineAttachments: vi.fn(), attachToDocument: vi.fn(), attachToLine: vi.fn(),
    signedUrl: vi.fn(), updateAttachmentComment: vi.fn(), deleteAttachment: vi.fn(),
  },
}))
vi.mock('../api/expenses', () => ({
  expensesApi: {
    documentAttachments: vi.fn(), lineAttachments: vi.fn(), attachToDocument: vi.fn(), attachToLine: vi.fn(),
    signedUrl: vi.fn(), updateAttachmentComment: vi.fn(), deleteAttachment: vi.fn(),
  },
}))

const file = (id: number, filename: string, over: Record<string, unknown> = {}) => ({
  id, filename, contentType: 'application/pdf', sizeBytes: 2048, comment: null, frozen: false,
  uploadedAt: '2026-10-09T13:00:00Z', ...over,
})

/** OOR-OCT26-R-012 as it is live: fully paid ("settled") but only VERIFIED, one file on the whole receipt. */
function receipt(over: Record<string, unknown> = {}): AnyDoc {
  return {
    id: 124, documentNo: 'OOR-OCT26-R-012', workflowStatus: 'VERIFIED', settled: true,
    jobCardId: 5, jobCardReference: 'OOR-JC-5', branchId: 3, branchCode: 'OOR', branchName: 'Rayagada',
    customerId: 3, customerName: 'Acme Transport', customerPhone: null, vehicleNo: 'OD33AB1234',
    contactPhone: null, chassisNo: null, dbmId: null, invoiceNo: null, invoiceAmount: null,
    b2b: false, gstNo: null, categoryId: 1, categoryName: 'Workshop', claimTypeId: null, claimTypeName: null,
    isClaim: false, businessStatusId: 1, businessStatusName: 'Received', pendingAmount: 0,
    settledViaClaimClose: false, claimFinalAmount: null, claimOverridden: false, claimOverrideReason: null,
    totalReceived: 5654, canRecordPayment: false, createdBy: 5, createdByName: 'Cashier', lastModifiedBy: 5,
    createdAt: '2026-10-09T10:00:00Z', submittedAt: '2026-10-09T10:05:00Z', history: [],
    lines: [{ id: 1, lineNo: 1, settlementModeName: 'Cash', settlementModeId: 1, amount: 3654, transactionDate: '2026-10-09',
      bankName: null, transactionRef: null, remark: null, originalAmount: null, overrideReason: null }],
    ...over,
  } as unknown as AnyDoc
}

function renderCard(doc: AnyDoc, canUploadDocs = false) {
  return render(
    <MemoryRouter>
      <RecordCard doc={doc} canOverride={false} busy={false} onOverride={async () => {}} onError={() => {}} canUploadDocs={canUploadDocs} />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(receiptsApi.documentAttachments).mockResolvedValue({ data: [file(14, 'PI-NL01AJ7639_WRTY.pdf')] } as never)
  vi.mocked(receiptsApi.lineAttachments).mockResolvedValue({ data: [file(15, 'line-bill.pdf', { comment: 'cash voucher' })] } as never)
  vi.mocked(expensesApi.documentAttachments).mockResolvedValue({ data: [] } as never)
  vi.mocked(expensesApi.lineAttachments).mockResolvedValue({ data: [] } as never)
})

describe('review card: Documents (rev 72)', () => {
  it('the Finance Manager / Owner can SEE the uploaded documents — whole receipt and per line — but not change them', async () => {
    renderCard(receipt())
    expect(await screen.findByText('PI-NL01AJ7639_WRTY.pdf')).toBeInTheDocument()
    expect(screen.getByText('line-bill.pdf')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'View attachment PI-NL01AJ7639_WRTY.pdf' })).toBeInTheDocument()
    expect(screen.queryByText(/Add documents/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Remove attachment/ })).not.toBeInTheDocument()
    expect(screen.queryByText(/Add note|Edit note/)).not.toBeInTheDocument()
  })

  it('the Accountant sees them too — and can add documents and notes, but not remove the Cashier\'s', async () => {
    renderCard(receipt(), true)
    expect(await screen.findByText('PI-NL01AJ7639_WRTY.pdf')).toBeInTheDocument()
    expect(screen.getByText(/Add documents/)).toBeInTheDocument()
    expect(screen.getAllByText(/Add note|Edit note/).length).toBeGreaterThan(0)
    expect(screen.queryByRole('button', { name: /Remove attachment/ })).not.toBeInTheDocument()
  })

  it('a fully paid receipt that is only queried / verified is NOT frozen for the Accountant (R-011)', async () => {
    renderCard(receipt({ workflowStatus: 'QUERIED', settled: true }), true)
    await screen.findByText('PI-NL01AJ7639_WRTY.pdf')
    expect(screen.getByText(/Add documents/)).toBeInTheDocument()
    expect(screen.queryByText(/documents are frozen/)).not.toBeInTheDocument()
  })

  it('an approved receipt is frozen — the Accountant sees the files and the reason, no upload', async () => {
    renderCard(receipt({ workflowStatus: 'APPROVED' }), true)
    await screen.findByText('PI-NL01AJ7639_WRTY.pdf')
    expect(screen.getByText(/documents are frozen/)).toBeInTheDocument()
    expect(screen.queryByText(/Add documents/)).not.toBeInTheDocument()
  })

  it('an expense shows its documents through the expense API', async () => {
    vi.mocked(expensesApi.documentAttachments).mockResolvedValue({ data: [file(30, 'fuel-bill.pdf')] } as never)
    const expense = {
      id: 9, documentNo: 'OOR-OCT26-E-003', workflowStatus: 'SUBMITTED', branchId: 3, branchCode: 'OOR',
      expenseCategoryName: 'Service', receiverName: 'City Fuel', businessStatusId: 1, businessStatusName: 'Paid',
      vehicleNo: null, dbmId: null, receiveDocumentNos: [], createdAt: '2026-10-09T10:00:00Z', totalAmount: 100,
      lines: [], history: [], createdBy: 5, lastModifiedBy: 5,
    } as unknown as AnyDoc
    renderCard(expense)
    expect(await screen.findByText('fuel-bill.pdf')).toBeInTheDocument()
    expect(receiptsApi.documentAttachments).not.toHaveBeenCalled()
  })
})

describe('review card: GST (rev 72)', () => {
  it('a B2B receipt shows the customer type and the GST number', async () => {
    renderCard(receipt({ b2b: true, gstNo: '21ABCDE1234F1Z5' }))
    await waitFor(() => expect(screen.getByText('Customer type').parentElement).toHaveTextContent('B2B'))
    expect(screen.getByText('GST #').parentElement).toHaveTextContent('21ABCDE1234F1Z5')
  })

  it('a B2C receipt shows B2C and a blank GST number', async () => {
    renderCard(receipt({ b2b: false, gstNo: null }))
    await waitFor(() => expect(screen.getByText('Customer type').parentElement).toHaveTextContent('B2C'))
    expect(screen.getByText('GST #').parentElement).toHaveTextContent(/^GST #$/)
  })

  it('an expense has no customer type or GST rows', async () => {
    const expense = {
      id: 9, documentNo: 'OOR-OCT26-E-003', workflowStatus: 'SUBMITTED', branchId: 3, branchCode: 'OOR',
      expenseCategoryName: 'Service', receiverName: 'City Fuel', businessStatusId: 1, businessStatusName: 'Paid',
      vehicleNo: null, dbmId: null, receiveDocumentNos: [], createdAt: '2026-10-09T10:00:00Z', totalAmount: 100,
      lines: [], history: [], createdBy: 5, lastModifiedBy: 5,
    } as unknown as AnyDoc
    renderCard(expense)
    await screen.findByText('OOR-OCT26-E-003')
    expect(screen.queryByText('GST #')).not.toBeInTheDocument()
  })
})
