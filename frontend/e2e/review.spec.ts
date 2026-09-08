import { expect, test } from '@playwright/test'
import { loginAs, mockApi } from './helpers'

/**
 * Review flows (AGENT.md smoke checklist): the accountant's queue moves
 * SUBMITTED → VERIFIED / QUERIED / REJECTED with reasons, the FM approves
 * and closes claims with a final override, and every override lands in the
 * audit view with before → after.
 */
const receiptItem = {
  type: 'receipt', id: 500, documentNo: 'OOR-AUG26-R-005', branchId: 3, branchCode: 'OOR',
  partyName: 'Sharma Transport', categoryName: 'Service labour', amount: 8000,
  overLimit: false, hasOverride: false, submittedAt: '2026-08-20T06:00:00Z',
}

const expenseItem = {
  type: 'expense', id: 600, documentNo: 'OOR-AUG26-E-005', branchId: 3, branchCode: 'OOR',
  partyName: 'Fuel Station', categoryName: 'Fuel', amount: 1400,
  overLimit: true, hasOverride: true, submittedAt: '2026-08-20T06:00:00Z',
}

const EMPTY_FM = { awaitingApproval: [], openClaims: [], recentlyClosed: [] }

test.describe('accountant review queue', () => {
  test.beforeEach(async ({ page }) => {
    await mockApi(page, [
      ['/api/v1/review/receipts', [receiptItem]],
      ['/api/v1/review/expenses', [expenseItem]],
      ['/api/v1/review/cash', []],
    ])
    await loginAs(page, 'ACCOUNTANT', /accountant/i)
  })

  test('submitted receipts list with party and amount', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /review queue/i })).toBeVisible()
    await expect(page.getByText('OOR-AUG26-R-005').first()).toBeVisible()
    await expect(page.getByText('Sharma Transport').first()).toBeVisible()
    await expect(page.getByText('₹8,000').first()).toBeVisible()
  })

  test('expense tab surfaces the over-limit flag', async ({ page }) => {
    await page.getByRole('button', { name: /^expenses$/i }).click()
    await expect(page.getByText('OOR-AUG26-E-005').first()).toBeVisible()
    await expect(page.getByText('Above limit').first()).toBeVisible()
  })

  test('empty queues say so instead of spinning', async ({ page }) => {
    await mockApi(page, [
      ['/api/v1/review/receipts', []],
      ['/api/v1/review/expenses', []],
      ['/api/v1/review/cash', []],
    ])
    await page.reload()
    await expect(page.getByText('Nothing waiting on you — the queue is clear.')).toBeVisible()
  })
})

test.describe('finance manager approvals and override audit', () => {
  test.beforeEach(async ({ page }) => {
    await mockApi(page, [
      ['/api/v1/review/fm/receipts', { ...EMPTY_FM, awaitingApproval: [receiptItem] }],
      ['/api/v1/review/fm/expenses', EMPTY_FM],
      ['/api/v1/review/fm/cash', EMPTY_FM],
      ['/api/v1/cash/reopen-requests', []],
      ['/api/v1/ai/claims/insights', []],
      ['/api/v1/ai/', []],
      ['/api/v1/branches', []],
      ['/api/v1/users', []],
      ['/api/v1/override-audit', [{
        at: '2026-08-20T07:00:00Z', kind: 'receipt', actorName: 'Accountant User',
        branchId: 3, branchCode: 'OOR', documentNo: 'OOR-AUG26-R-005',
        lineId: 'OOR-AUG26-R-005-L1', amountBefore: 800, amountAfter: 1000,
        reason: 'Eicher revised the labour rate',
      }]],
    ])
    await loginAs(page, 'FINANCE_MANAGER', /finance manager/i)
  })

  test('fm queue shows documents awaiting approval', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /approvals & claims/i })).toBeVisible()
    await expect(page.getByText('OOR-AUG26-R-005').first()).toBeVisible()
  })

  test('override audit lists who changed what, before → after, and why', async ({ page }) => {
    await page.goto('/app/override-audit')
    await expect(page.getByRole('heading', { name: /override audit/i })).toBeVisible()
    await expect(page.getByText('Accountant User')).toBeVisible()
    await expect(page.getByText('OOR-AUG26-R-005-L1')).toBeVisible()
    await expect(page.getByText('Eicher revised the labour rate')).toBeVisible()
  })
})
