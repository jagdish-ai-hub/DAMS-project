import type { Page } from '@playwright/test'

export type Role = 'SUPER_ADMIN' | 'OWNER' | 'FINANCE_MANAGER' | 'ACCOUNTANT' | 'CASHIER' | 'AUDITOR'

const b64url = (o: unknown) =>
  Buffer.from(JSON.stringify(o)).toString('base64url')

/** Unsigned JWT shaped like the backend's — the client only decodes it, never verifies. */
export function jwtFor(role: Role): string {
  const header = b64url({ alg: 'HS256', typ: 'JWT' })
  const payload = b64url({
    sub: '7',
    orgId: 1,
    role,
    branchIds: [2, 3],
    homeBranchId: 3,
    exp: Math.floor(Date.now() / 1000) + 8 * 60 * 60,
  })
  return `${header}.${payload}.test-signature`
}

const NAMES: Record<Role, string> = {
  SUPER_ADMIN: 'Platform Admin',
  OWNER: 'JJ Owner',
  FINANCE_MANAGER: 'FM User',
  ACCOUNTANT: 'Accountant User',
  CASHIER: 'Bikram Nayak',
  AUDITOR: 'External CA',
}

/**
 * Mock every /api/v1 call with first-match-wins rules.
 * Unhandled paths return {} so a page under test never hangs on a
 * badge poll or auxiliary fetch the spec does not care about.
 */
export async function mockApi(page: Page, rules: Array<[match: string, json: unknown]>) {
  await page.route('**/api/v1/**', async (route) => {
    const url = route.request().url()
    for (const [match, json] of rules) {
      if (url.includes(match)) {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json) })
        return
      }
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' })
  })
}

/** Mock the login endpoint for one role. */
export async function mockLogin(page: Page, role: Role) {
  await page.route('**/api/v1/auth/login', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        accessToken: jwtFor(role),
        role,
        orgId: 1,
        homeBranchId: 3,
        name: NAMES[role],
      }),
    })
  })
}

/**
 * Full login through the real form using the seeded demo button
 * (label + Enter →), then wait for the authenticated shell.
 */
export async function loginAs(page: Page, role: Role, buttonName: RegExp) {
  await mockLogin(page, role)
  await page.goto('/login')
  await page.getByRole('button', { name: buttonName }).click()
  await page.waitForURL('**/app**')
}
