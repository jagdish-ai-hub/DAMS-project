import { expect, test } from '@playwright/test'
import { loginAs, mockApi } from './helpers'

/**
 * Auth is the front door: /login is the only public screen, every /app route
 * needs a session, and the JWT's role — never a client-side toggle — picks
 * the landing view.
 */
test.describe('auth', () => {
  test('unauthenticated /app redirects to the login screen', async ({ page }) => {
    await page.goto('/app')
    await expect(page).toHaveURL(/\/login/)
    await expect(page.getByRole('heading', { name: /sign in to dams/i })).toBeVisible()
  })

  test('cashier demo login lands on the cashier home', async ({ page }) => {
    await mockApi(page, [['/api/v1/my-entries', []]])
    await loginAs(page, 'CASHIER', /cashier/i)
    await expect(page.getByRole('link', { name: /^home$/i })).toBeVisible()
    await expect(page.getByRole('link', { name: /^cash$/i })).toBeVisible()
    await expect(page.getByRole('link', { name: /my entries/i })).toBeVisible()
    await expect(page.getByRole('heading', { name: /find a customer/i })).toBeVisible()
  })

  test('wrong credentials show the backend error, not a blank screen', async ({ page }) => {
    await page.route('**/api/v1/auth/login', async (route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'Invalid email or password' }),
      })
    })
    await page.goto('/login')
    await page.getByPlaceholder('you@dealership.com').fill('cashier@jjmotors.demo')
    await page.getByPlaceholder('••••••••').fill('wrong-password')
    await page.getByRole('button', { name: /^sign in$/i }).click()
    await expect(page.getByText('Invalid email or password')).toBeVisible()
    await expect(page).toHaveURL(/\/login/)
  })

  test('expired session links explain what happened', async ({ page }) => {
    await page.goto('/login?expired=1')
    await expect(page.getByText(/your session ended/i)).toBeVisible()
  })

  test('role isolation: accountant sees the queue, never cashier screens', async ({ page }) => {
    await mockApi(page, [
      ['/api/v1/review/receipts', []],
      ['/api/v1/review/expenses', []],
      ['/api/v1/review/cash', []],
    ])
    await loginAs(page, 'ACCOUNTANT', /accountant/i)
    await expect(page.getByRole('link', { name: /review queue/i })).toBeVisible()
    await expect(page.getByRole('link', { name: /my entries/i })).toHaveCount(0)
    await expect(page.getByRole('link', { name: /^cash$/i })).toHaveCount(0)
  })

  test('role isolation: owner sees the dashboard, never the cash page', async ({ page }) => {
    await mockApi(page, [
      ['/api/v1/dashboard/summary', {
        scope: 'org', period: 'today',
        kpis: { collections: 0, expenses: 0, net: 0, cashInHand: 0, pendingReview: 0 },
        trend: [], byMode: [], byCategory: [], branchComparison: [],
      }],
      ['/api/v1/dashboard/outstanding', []],
      ['/api/v1/dashboard/activity', []],
    ])
    await loginAs(page, 'OWNER', /^owner/i)
    await expect(page.getByRole('link', { name: /^dashboard$/i })).toBeVisible()
    await expect(page.getByRole('link', { name: /team & branches/i })).toBeVisible()
    await expect(page.getByRole('link', { name: /^cash$/i })).toHaveCount(0)
  })
})
