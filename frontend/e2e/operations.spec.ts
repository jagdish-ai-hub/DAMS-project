import { expect, test } from '@playwright/test'
import { loginAs, mockApi, mockLogin } from './helpers'

/**
 * FEAT-35…50 operations (Tier 1–4): dues, floor, chase, messages, reconcile,
 * staff, estimates, awaiting bills — plus the read-only auditor.
 */
const followup = {
  id: 1, receiveDocumentId: 500, documentNo: 'OOR-AUG26-R-005',
  customerName: 'Sharma Transport', customerPhone: '9876543210',
  branchId: 3, branchCode: 'OOR', dueDate: '2026-09-05', promiseNote: 'Payday Friday',
  status: 'PROMISED', overdue: true, daysOverdue: 4, pendingAmount: 3133,
  remindedCount: 1, lastRemindedAt: null, createdBy: 7, createdAt: '2026-08-20T05:00:00Z',
}

test.describe('auditor (read-only)', () => {
  test.beforeEach(async ({ page }) => {
    await mockApi(page, [
      ['/api/v1/dashboard/summary', {
        scope: 'org', period: 'today',
        kpis: { collections: 0, expenses: 0, net: 0, cashInHand: 0, pendingReview: 0 },
        trend: [], byMode: [], byCategory: [], branchComparison: [],
      }],
      ['/api/v1/dashboard/outstanding', []],
      ['/api/v1/dashboard/activity', []],
      ['/api/v1/masters/', []],
      ['/api/v1/ai/brief', null],
      ['/api/v1/ai/benchmark', null],
      ['/api/v1/ai/', []],
      ['/api/v1/budgets', []],
      ['/api/v1/receivers', []],
      ['/api/v1/branches', []],
    ])
    // No demo button for the auditor — real orgs invite one; sign in by form.
    await mockLogin(page, 'AUDITOR')
    await page.goto('/login')
    await page.getByPlaceholder('you@dealership.com').fill('ca@auditfirm.in')
    await page.getByPlaceholder('••••••••').fill('audit123')
    await page.getByRole('button', { name: /^sign in$/i }).click()
    await page.waitForURL('**/app**')
  })

  test('sees dashboard, audit and masters — nothing writable', async ({ page }) => {
    await expect(page.getByRole('link', { name: /^dashboard$/i })).toBeVisible()
    await expect(page.getByRole('link', { name: /override audit/i })).toBeVisible()
    await expect(page.getByRole('link', { name: /^masters$/i })).toBeVisible()
    await expect(page.getByRole('link', { name: /team & branches/i })).toHaveCount(0)
    await expect(page.getByRole('link', { name: /^cash$/i })).toHaveCount(0)
    await expect(page.getByRole('link', { name: /review queue/i })).toHaveCount(0)
  })

  test('masters is read-only: no add, no edit', async ({ page }) => {
    await page.goto('/app/masters')
    await expect(page.getByText('Rows are deactivated, never deleted.')).toBeVisible()
    await expect(page.getByRole('button', { name: /^\+ add$/i })).toHaveCount(0)
    await expect(page.getByRole('button', { name: /^edit$/i })).toHaveCount(0)
  })
})

test.describe('receivables', () => {
  test.beforeEach(async ({ page }) => {
    await mockApi(page, [
      ['/api/v1/followups/defaulters', []],
      ['/api/v1/followups', [followup]],
    ])
    await loginAs(page, 'OWNER', /^owner/i)
  })

  test('follow-ups list dues with promises and chase state', async ({ page }) => {
    await page.goto('/app/receivables')
    await expect(page.getByRole('heading', { name: /receivables/i })).toBeVisible()
    await expect(page.getByText('OOR-AUG26-R-005').first()).toBeVisible()
    await expect(page.getByText('Payday Friday')).toBeVisible()
    await expect(page.getByText('₹3,133 due')).toBeVisible()
    await expect(page.getByRole('button', { name: /^remind$/i })).toBeVisible()
  })

  test('defaulters tab ranks by exposure', async ({ page }) => {
    await mockApi(page, [
      ['/api/v1/followups/defaulters', [{
        customerId: 5, customerName: 'Sharma Transport', customerPhone: '9876543210',
        totalOutstanding: 3133, openFollowups: 1, overdueFollowups: 1, oldestOverdueDays: 4,
      }]],
      ['/api/v1/followups', [followup]],
    ])
    await page.goto('/app/receivables')
    await page.getByRole('button', { name: /^defaulters$/i }).click()
    await expect(page.getByText('Sharma Transport').first()).toBeVisible()
    await expect(page.getByText('₹3,133').first()).toBeVisible()
  })
})

test.describe('floor and renewals', () => {
  test.beforeEach(async ({ page }) => {
    await mockApi(page, [
      ['/api/v1/job-cards/board', [{
        jobCardId: 11, reference: 'OOR-JC-11', branchId: 3, branchCode: 'OOR',
        customerName: 'Sharma Transport', vehicleNo: 'OD05CA4177',
        categoryName: 'Workshop', businessStatusName: 'WIP',
        openedDate: '2026-08-01', ageDays: 19, stuckReason: 'Waiting for Eicher approval',
        pendingAmount: 4000,
      }]],
      ['/api/v1/job-cards/renewals', [{
        jobCardId: 11, reference: 'OOR-JC-11', branchId: 3, branchCode: 'OOR',
        customerName: 'Das Logistics', customerPhone: '9123456780', vehicleNo: 'OD02AB1234',
        serviceDueDate: '2026-09-10', daysUntilDue: 5,
      }]],
    ])
    await loginAs(page, 'OWNER', /^owner/i)
  })

  test('WIP board shows stuck work oldest first', async ({ page }) => {
    await page.goto('/app/floor')
    await expect(page.getByText('OOR-JC-11').first()).toBeVisible()
    await expect(page.getByText('Stuck: Waiting for Eicher approval')).toBeVisible()
  })

  test('renewals tab offers reminders', async ({ page }) => {
    await page.goto('/app/floor')
    await page.getByRole('button', { name: /^renewals/i }).click()
    await expect(page.getByText('Das Logistics')).toBeVisible()
    await expect(page.getByRole('button', { name: /^remind$/i })).toBeVisible()
  })
})

test.describe('messages', () => {
  test('templates, send form and log render', async ({ page }) => {
    await mockApi(page, [
      ['/api/v1/messages/templates', [{ id: 1, code: 'due_reminder', channel: 'WHATSAPP', body: 'Hi {{name}}', active: true }]],
      ['/api/v1/messages/log', []],
    ])
    await loginAs(page, 'OWNER', /^owner/i)
    await page.goto('/app/messages')
    await expect(page.getByRole('heading', { name: /messages/i })).toBeVisible()
    await expect(page.getByText('Hi {{name}}')).toBeVisible()
    await expect(page.getByText('Nothing sent yet.')).toBeVisible()
  })
})

test.describe('reconcile', () => {
  test.beforeEach(async ({ page }) => {
    await mockApi(page, [
      ['/api/v1/review/receipts', []],
      ['/api/v1/review/expenses', []],
      ['/api/v1/review/cash', []],
      // Specific before generic — first match wins.
      ['/api/v1/recon/batches/2/lines', [
        { id: 21, txnDate: '2026-08-20', utr: '612345678901', amount: 8000, narration: 'UPI', matchedSettlementLineId: null, matchedDocumentNo: null, matchKind: null, ignored: false, resolved: false },
        { id: 22, txnDate: '2026-08-20', utr: '000000000999', amount: 500, narration: 'charges', matchedSettlementLineId: null, matchedDocumentNo: null, matchKind: null, ignored: true, resolved: true },
      ]],
      ['/api/v1/recon/batches', [{ id: 2, filename: 'stmt-aug.csv', lineCount: 3, resolvedCount: 1, uploadedBy: 50, uploadedAt: '2026-08-21T05:00:00Z' }]],
    ])
    await loginAs(page, 'ACCOUNTANT', /accountant/i)
  })

  test('batches and unmatched lines render', async ({ page }) => {
    await page.goto('/app/recon')
    await expect(page.getByRole('heading', { name: /reconcile bank credits/i })).toBeVisible()
    await expect(page.getByRole('option', { name: /stmt-aug\.csv — 1\/3 resolved/ })).toBeAttached()
    await expect(page.getByText('612345678901')).toBeVisible()
    await expect(page.getByText('unmatched').first()).toBeVisible()
  })
})

test.describe('staff advances', () => {
  test('members with outstanding and entry form render', async ({ page }) => {
    await mockApi(page, [
      ['/api/v1/staff/9/entries', []],
      ['/api/v1/staff', [{ id: 9, name: 'Ramesh', phone: null, active: true, outstanding: 3000 }]],
    ])
    await loginAs(page, 'OWNER', /^owner/i)
    await page.goto('/app/staff')
    await expect(page.getByRole('heading', { name: /staff advances/i })).toBeVisible()
    await expect(page.getByText('Ramesh').first()).toBeVisible()
    await expect(page.getByText('₹3,000').first()).toBeVisible()
  })
})

test.describe('estimates', () => {
  test('quote history and variance render per job', async ({ page }) => {
    await mockApi(page, [
      ['/api/v1/estimates', [{
        id: 400, jobCardId: 11, status: 'APPROVED', total: 10000,
        lines: [{ lineNo: 1, description: 'Clutch plate', amount: 7000 }],
        invoiceAmount: 12000, varianceVsInvoice: 2000,
        approvedBy: 8, decidedAt: null, decisionNote: null, createdBy: 7, createdAt: '2026-08-20T05:00:00Z',
      }]],
    ])
    await loginAs(page, 'FINANCE_MANAGER', /finance manager/i)
    // FM badge polling hits the fm queues — default {} would crash the AI banner; mock them.
    await page.goto('/app/estimates?jobCardId=11')
    await expect(page.getByText('Clutch plate')).toBeVisible()
    await expect(page.getByText(/billed ₹12,000/)).toBeVisible()
  })
})

test.describe('awaiting bills and claims chase', () => {
  test('awaiting bills lists parked expenses', async ({ page }) => {
    await mockApi(page, [
      ['/api/v1/review/receipts', []],
      ['/api/v1/review/expenses', []],
      ['/api/v1/review/cash', []],
      ['/api/v1/expenses/awaiting-bills', [{
        id: 600, documentNo: 'OOR-AUG26-E-005', workflowStatus: 'SUBMITTED', overLimit: false,
        branchId: 3, branchCode: 'OOR', branchName: 'Rayagada', jobCardId: null, jobCardReference: null,
        receiverId: 21, receiverName: 'Fuel Station', receiverPhone: null, customerId: null, customerName: null,
        vehicleNo: null, expenseCategoryId: 1, expenseCategoryName: 'Service', businessStatusId: 3,
        businessStatusName: 'Awaiting Receipt', businessStatusTriggersClaim: false, claimEligible: false,
        totalAmount: 1400, createdBy: 7, createdByName: 'Bikram', lastModifiedBy: 7,
        createdAt: '2026-08-10T05:00:00Z', submittedAt: null, lines: [], history: [], attachments: [],
      }]],
    ])
    await loginAs(page, 'ACCOUNTANT', /accountant/i)
    await page.goto('/app/awaiting-bills')
    await expect(page.getByRole('heading', { name: /awaiting bills/i })).toBeVisible()
    await expect(page.getByText('OOR-AUG26-E-005')).toBeVisible()
    await expect(page.getByText('Fuel Station')).toBeVisible()
  })

  test('claims chase lists open actions', async ({ page }) => {
    await mockApi(page, [
      ['/api/v1/review/fm/receipts', { awaitingApproval: [], openClaims: [], recentlyClosed: [] }],
      ['/api/v1/review/fm/expenses', { awaitingApproval: [], openClaims: [], recentlyClosed: [] }],
      ['/api/v1/review/fm/cash', { awaitingApproval: [], openClaims: [], recentlyClosed: [] }],
      ['/api/v1/cash/reopen-requests', []],
      ['/api/v1/ai/claims/insights', []],
      ['/api/v1/ai/', []],
      ['/api/v1/claim-actions', [{
        id: 300, jobCardId: 11, jobCardReference: 'OOR-JC-11', action: 'Call Eicher for the LR copy',
        ownerUserId: 8, ownerName: 'FM User', dueDate: '2026-09-02', overdue: false,
        doneAt: null, createdBy: 8, createdAt: '2026-08-20T05:00:00Z',
      }]],
    ])
    await loginAs(page, 'FINANCE_MANAGER', /finance manager/i)
    await page.goto('/app/claims-chase')
    await expect(page.getByRole('heading', { name: /claims chase/i })).toBeVisible()
    await expect(page.getByText('Call Eicher for the LR copy')).toBeVisible()
  })
})
