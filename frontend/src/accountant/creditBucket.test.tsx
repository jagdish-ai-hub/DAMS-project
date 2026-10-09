import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReviewQueueItem } from '../api/review'
import ReviewQueuePage from './ReviewQueuePage'

const queue = vi.fn()

vi.mock('../api/review', () => ({
  reviewApi: {
    receiptQueue: () => queue(), expenseQueue: () => queue(), cashQueue: () => queue(),
    verifiedQueue: () => Promise.resolve({ data: [] }),
  },
}))
vi.mock('../api/masters', () => ({ mastersApi: { list: () => Promise.resolve({ data: [] }) } }))
vi.mock('../auth/useAuth', () => ({
  useAuth: () => ({ user: { userId: 2, orgId: 1, role: 'ACCOUNTANT', primaryRole: 'ACCOUNTANT', name: 'Acc', branchIds: [], isActing: false } }),
}))

function item(id: number, party: string, over: Partial<ReviewQueueItem> = {}): ReviewQueueItem {
  return {
    type: 'receipt', id, documentNo: `OOR-OCT26-R-${id}`, branchId: 1, branchCode: 'OOR', partyName: party,
    categoryName: 'Workshop', amount: 1000, overLimit: false, hasOverride: false,
    submittedAt: '2026-10-09T10:00:00Z', workflowStatus: 'SUBMITTED', isClaim: false,
    isCashEligible: false, isCredit: false, preApproved: false, approvalRequestedAt: null, ...over,
  }
}

beforeEach(() => {
  queue.mockResolvedValue({
    data: [
      item(1, 'Allcash Anil', { isCashEligible: true }),
      // Manoj: Received, ₹3,000 cash + ₹4,000 bank — neither cash-eligible nor credit
      item(2, 'Manoj'),
      item(3, 'Credit Chandan', { isCredit: true }),
      item(4, 'Claim Kiran', { isClaim: true, isCredit: true }),
    ],
  })
})

async function openCreditBucket() {
  render(<MemoryRouter><ReviewQueuePage /></MemoryRouter>)
  await screen.findAllByText('Manoj')
  await userEvent.click(screen.getByRole('button', { name: /^Cash\/Credit/ }))
  await userEvent.click(screen.getByTitle(/leave this list once the status is changed/))
}

describe('Accountant Credit bucket (rev 69)', () => {
  it('holds only receipts whose status is Credit — a part-cash/part-bank Received receipt is not in it', async () => {
    await openCreditBucket()
    expect((await screen.findAllByText('Credit Chandan')).length).toBeGreaterThan(0)
    expect(screen.queryAllByText('Manoj')).toHaveLength(0)
    expect(screen.queryAllByText('Allcash Anil')).toHaveLength(0)
  })

  it('leaves a claim in Claim Transaction even when its status is Credit', async () => {
    await openCreditBucket()
    expect(screen.queryAllByText('Claim Kiran')).toHaveLength(0)
  })

  it('still lists Manoj under All', async () => {
    render(<MemoryRouter><ReviewQueuePage /></MemoryRouter>)
    expect((await screen.findAllByText('Manoj')).length).toBeGreaterThan(0)
    expect(screen.getAllByText('Credit Chandan').length).toBeGreaterThan(0)
  })
})
