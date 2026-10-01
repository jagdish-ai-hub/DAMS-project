import type { Role } from './AuthContext'

/** Human role names — one copy, shared by the account menu, settings, role switcher and history. */
export const ROLE_LABEL: Record<Role, string> = {
  SUPER_ADMIN: 'Super Admin',
  OWNER: 'Owner',
  FINANCE_MANAGER: 'Finance Manager',
  ACCOUNTANT: 'Accountant',
  CASHIER: 'Cashier',
}

/**
 * A history / audit actor line. Entries always show the real person; while they were switched
 * into another role the server also records which ("Ajay Kumar · as Cashier" — plan.md rev 55).
 */
export function actorLabel(name: string, actorRole?: string | null): string {
  if (!actorRole) return name
  const label = ROLE_LABEL[actorRole as Role] ?? actorRole
  return `${name} · as ${label}`
}
