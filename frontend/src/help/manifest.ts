import type { Role } from '../auth/AuthContext'

/**
 * Help Center (Stage 11) — role-scoped, step-by-step articles authored as Markdown and
 * bundled with the frontend. No backend, no `help_article` table. Each article file lives
 * at `help/<role>/<slug>.md`; this manifest fixes their order and display titles, and maps
 * the signed-in role to its article set.
 */

export type HelpRole = 'cashier' | 'accountant' | 'finance-manager' | 'owner' | 'super-admin'

export type HelpEntry = { slug: string; title: string }

export const HELP_MANIFEST: Record<HelpRole, HelpEntry[]> = {
  cashier: [
    { slug: 'your-role-in-dams', title: 'Your role in DAMS' },
    { slug: 'finding-a-customer', title: 'Finding a customer' },
    { slug: 'creating-a-receipt', title: 'Creating a receipt' },
    { slug: 'adding-a-payment', title: 'Adding a payment' },
    { slug: 'recording-an-expense', title: 'Recording an expense' },
    { slug: 'cash-in-and-out', title: 'Cash in and out' },
    { slug: 'closing-the-day', title: 'Closing the day' },
    { slug: 'fixing-a-queried-entry', title: 'Fixing a queried entry' },
  ],
  accountant: [
    { slug: 'your-role-in-dams', title: 'Your role in DAMS' },
    { slug: 'reviewing-the-queue', title: 'Reviewing the queue' },
    { slug: 'reviewing-cash', title: 'Reviewing cash movements' },
    { slug: 'overriding-an-amount', title: 'Overriding an amount' },
    { slug: 'closing-an-expense', title: 'Closing an expense' },
  ],
  'finance-manager': [
    { slug: 'your-role-in-dams', title: 'Your role in DAMS' },
    { slug: 'approving-entries', title: 'Approving entries' },
    { slug: 'closing-a-claim', title: 'Closing a claim' },
    { slug: 'override-audit', title: 'Override audit' },
  ],
  owner: [
    { slug: 'your-role-in-dams', title: 'Your role in DAMS' },
    { slug: 'reading-the-dashboard', title: 'Reading the dashboard' },
    { slug: 'comparing-branches', title: 'Comparing branches' },
    { slug: 'team-and-branches', title: 'Team & branches' },
    { slug: 'masters', title: 'Masters' },
  ],
  'super-admin': [
    { slug: 'your-role-in-dams', title: 'Your role in DAMS' },
    { slug: 'onboarding-an-organization', title: 'Onboarding an organization' },
  ],
}

export const ROLE_TO_HELP: Record<Role, HelpRole> = {
  CASHIER: 'cashier',
  ACCOUNTANT: 'accountant',
  FINANCE_MANAGER: 'finance-manager',
  OWNER: 'owner',
  SUPER_ADMIN: 'super-admin',
}

// Every article body, bundled as a raw string at build time.
const BODIES = import.meta.glob('./*/*.md', { query: '?raw', import: 'default', eager: true }) as Record<string, string>

export function articleBody(role: HelpRole, slug: string): string {
  return BODIES[`./${role}/${slug}.md`] ?? ''
}

/** First `# Heading` line of a body, for search snippets / fallback titles. */
export function firstHeading(body: string): string {
  const m = body.match(/^#\s+(.+)$/m)
  return m ? m[1].trim() : ''
}
