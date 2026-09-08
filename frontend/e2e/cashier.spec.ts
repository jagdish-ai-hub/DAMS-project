import { expect, test } from '@playwright/test'
import { loginAs, mockApi } from './helpers'

/**
 * Cashier day (AGENT.md smoke checklist): universal search → history →
 * Add Payment appends a line; drawer position follows
 * Opening + cash receipts + Cash In − cash expenses − Cash Out; Close Cash
 * with a variance demands a remark; My Entries drives fix-and-resubmit.
 */
const drawer = {
  branchId: 3,
  branchCode: 'OOR',
  branchName: 'Rayagada',
  date: '2026-08-20',
  opening: 30000,
  openingSet: true,
  cashReceipts: 5000,
  cashIn: 20000,
  cashExpenses: 800,
  cashOut: 15000,
  computedPosition: 39200,
  closed: false,
  close: null,
  movements: [],
}

const queriedEntries = [
  {
    id: 1, kind: 'RECEIPT', documentNo: 'OOR-AUG26-R-005', workflowStatus: 'QUERIED',
    settled: false, claimOverridden: false, jobCardId: 11, jobCardReference: 'OOR-JC-11',
    partyName: 'Sharma Transport', total: 8000, pendingAmount: 3133, lineCount: 2,
    overLimit: false, today: true, queried: true,
    createdAt: '2026-08-20T05:00:00Z', submittedAt: '2026-08-20T06:00:00Z',
  },
  {
    id: 2, kind: 'RECEIPT', documentNo: 'OOR-AUG26-R-006', workflowStatus: 'APPROVED',
    settled: false, claimOverridden: true, jobCardId: 12, jobCardReference: 'OOR-JC-12',
    partyName: 'Das Logistics', total: 12000, pendingAmount: 0, lineCount: 1,
    overLimit: false, today: true, queried: false,
    createdAt: '2026-08-20T05:00:00Z', submittedAt: '2026-08-20T06:00:00Z',
  },
]

test.describe('cashier', () => {
  test.beforeEach(async ({ page }) => {
    await mockApi(page, [
      ['/api/v1/my-entries', []],
      ['/api/v1/masters/banks', []],
      ['/api/v1/cash/drawer', drawer],
      ['/api/v1/cash/reopen-requests', []],
      ['/api/v1/search', {
        query: 'sharma',
        hits: [{
          customerId: 5, customerName: 'Sharma Transport', phone: '9876543210',
          vehicles: ['OD05CA4177'], jobCardCount: 2,
          totalInvoiced: 15431, totalOutstanding: 3133,
          matchField: 'Name', branchCodes: ['OOR'],
        }],
      }],
    ])
    await loginAs(page, 'CASHIER', /cashier/i)
  })

  test('universal search finds the customer with match context', async ({ page }) => {
    await page.getByPlaceholder(/sumati sahoo/i).fill('sharma')
    await expect(page.getByText('Sharma Transport')).toBeVisible()
    await expect(page.getByText(/name match/i)).toBeVisible()
    await expect(page.getByText(/OD05CA4177/)).toBeVisible()
  })

  test('cash page shows the live drawer breakdown and computed figure', async ({ page }) => {
    await page.goto('/app/cash')
    await expect(page.getByRole('heading', { name: /drawer position/i })).toBeVisible()
    await expect(page.getByText('Opening', { exact: true })).toBeVisible()
    await expect(page.getByText('Computed position')).toBeVisible()
    // 30000 + 5000 + 20000 − 800 − 15000, Indian grouping.
    await expect(page.getByText('₹39,200').first()).toBeVisible()
  })

  test('my entries highlights queried items for fix-and-resubmit', async ({ page }) => {
    await mockApi(page, [['/api/v1/my-entries', queriedEntries]])
    await page.goto('/app/my-entries')
    await expect(page.getByRole('heading', { name: /my entries/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /fix & resubmit/i })).toBeVisible()
    await expect(page.getByText('Overridden · Final')).toBeVisible()
  })
})
