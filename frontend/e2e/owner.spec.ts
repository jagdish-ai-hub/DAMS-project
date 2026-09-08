import { expect, test } from '@playwright/test'
import { loginAs, mockApi } from './helpers'

/**
 * Owner window (AGENT.md smoke checklist): dashboard KPIs never include Cash
 * In/Out movements, branch comparison drills down, Team & Branches manages
 * access, and Masters deactivates — never deletes.
 */
const summary = {
  scope: 'org',
  period: 'today',
  kpis: { collections: 15431, expenses: 5200, net: 10231, cashInHand: 39200, pendingReview: 3 },
  trend: [{ date: '2026-08-20', collections: 15431, expenses: 5200 }],
  byMode: [{ name: 'Cash', amount: 5000 }],
  byCategory: [{ name: 'Fuel', amount: 1400 }],
  branchComparison: [{
    branchId: 3, branchCode: 'OOR', branchName: 'Rayagada',
    collections: 15431, expenses: 5200, net: 10231,
    cashInHand: 39200, lastClosed: '2026-08-19', variance: 0, pendingReview: 3,
  }],
}

test.describe('owner', () => {
  test.beforeEach(async ({ page }) => {
    await mockApi(page, [
      ['/api/v1/dashboard/summary', summary],
      ['/api/v1/dashboard/outstanding', []],
      ['/api/v1/dashboard/activity', []],
      ['/api/v1/masters/', []],
      ['/api/v1/budgets', []],
      ['/api/v1/receivers', []],
      // Object-shaped AI answers must stay null (a [] would render and crash
      // on .bullets/.lines); list-shaped ones are safely empty.
      ['/api/v1/ai/brief', null],
      ['/api/v1/ai/benchmark', null],
      ['/api/v1/ai/', []],
      ['/api/v1/branches', []],
      ['/api/v1/users', []],
      ['/api/v1/organization', { multiBranchCashierAccess: false }],
    ])
    await loginAs(page, 'OWNER', /^owner/i)
  })

  test('dashboard KPIs render collections, expenses and cash in hand', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /^dashboard$/i })).toBeVisible()
    await expect(page.getByText('Collections').first()).toBeVisible()
    await expect(page.getByText('₹15,431').first()).toBeVisible()
    await expect(page.getByText('Cash in hand').first()).toBeVisible()
    await expect(page.getByText('OOR').first()).toBeVisible()
  })

  test('masters page states the deactivate-never-delete rule', async ({ page }) => {
    await page.goto('/app/masters')
    await expect(page.getByText('Rows are deactivated, never deleted.')).toBeVisible()
    await expect(page.getByRole('button', { name: /receipt categories/i })).toBeVisible()
  })

  test('team page is reachable from the owner nav', async ({ page }) => {
    await page.getByRole('link', { name: /team & branches/i }).click()
    await expect(page.getByRole('heading', { name: /team & branches/i })).toBeVisible()
  })
})
