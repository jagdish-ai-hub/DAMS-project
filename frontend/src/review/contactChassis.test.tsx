import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { RecordCard, type AnyDoc } from './reviewShared'

// not about documents — keep the card's Documents section out of this test's way
vi.mock('../cashier/AttachmentsPanel', () => ({ default: () => null }))

/** A receipt as the Accountant / Finance Manager see it — only what the card reads. */
function receipt(over: Record<string, unknown> = {}): AnyDoc {
  return {
    id: 1, documentNo: 'OOR-OCT26-R-010', workflowStatus: 'SUBMITTED', settled: false,
    jobCardId: 5, jobCardReference: 'OOR-JC-5', branchId: 1, branchCode: 'OOR', branchName: 'Rayagada',
    customerId: 3, customerName: 'Acme Transport', customerPhone: null, vehicleNo: 'OD33AB1234',
    contactPhone: null, chassisNo: null, dbmId: null, invoiceNo: null, invoiceAmount: null,
    b2b: false, gstNo: null, categoryId: 1, categoryName: 'Workshop', claimTypeId: null, claimTypeName: null,
    isClaim: false, businessStatusId: 1, businessStatusName: 'Received', pendingAmount: 0,
    settledViaClaimClose: false, claimFinalAmount: null, claimOverridden: false, claimOverrideReason: null,
    totalReceived: 0, canRecordPayment: false, createdBy: 1, createdByName: 'Cashier', lastModifiedBy: 1,
    createdAt: '2026-10-08T10:00:00Z', submittedAt: '2026-10-08T10:05:00Z', lines: [], history: [],
    ...over,
  } as unknown as AnyDoc
}

function renderCard(doc: AnyDoc) {
  return render(
    <MemoryRouter>
      <RecordCard doc={doc} canOverride={false} busy={false} onOverride={async () => {}} onError={() => {}} />
    </MemoryRouter>,
  )
}

describe('review card: Contact and Chassis # (rev 68)', () => {
  it('shows both next to the vehicle number', () => {
    renderCard(receipt({ contactPhone: '98765 43210', chassisNo: 'MC2ABC123XYZ' }))
    expect(screen.getByText('Vehicle #')).toBeInTheDocument()
    expect(screen.getByText('MC2ABC123XYZ')).toBeInTheDocument()
    expect(screen.getByText('98765 43210')).toBeInTheDocument()
    expect(screen.getByText('Chassis #')).toBeInTheDocument()
    expect(screen.getByText('Contact')).toBeInTheDocument()
  })

  it('shows a dash for an older receipt that never recorded them', () => {
    renderCard(receipt())
    const chassis = screen.getByText('Chassis #').parentElement!
    const contact = screen.getByText('Contact').parentElement!
    expect(chassis).toHaveTextContent('—')
    expect(contact).toHaveTextContent('—')
  })
})
