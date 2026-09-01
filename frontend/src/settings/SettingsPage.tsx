import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { authApi } from '../api/auth'
import { orgSettingsApi, type OrgSettings } from '../api/orgSettings'
import { useAuth } from '../auth/useAuth'
import type { Role } from '../auth/AuthContext'
import { card, ErrorBanner, Field, ghostBtn, primaryBtn, Spinner, TextInput, initials } from '../shell/ui'

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

const ROLE_LABEL: Record<Role, string> = {
  SUPER_ADMIN: 'Super Admin',
  OWNER: 'Owner',
  FINANCE_MANAGER: 'Finance Manager',
  ACCOUNTANT: 'Accountant',
  CASHIER: 'Cashier',
}

export default function SettingsPage() {
  const { user } = useAuth()
  if (!user) return null

  const branchContext =
    user.role === 'CASHIER'
      ? user.homeBranchId ? `Home branch #${user.homeBranchId}` : 'No home branch set'
      : user.role === 'ACCOUNTANT'
        ? user.branchIds.length > 0
          ? `${user.branchIds.length} branch${user.branchIds.length === 1 ? '' : 'es'} assigned`
          : 'No branches assigned'
        : user.role === 'SUPER_ADMIN' ? 'Platform-wide' : 'All branches'

  return (
    <div style={{ maxWidth: 640, display: 'flex', flexDirection: 'column', gap: 14 }}>
      <h1 style={{ fontSize: '1.15rem', fontWeight: 700, color: 'var(--navy)' }}>Settings</h1>

      {/* Account summary — read-only. Gives the page something to land on. */}
      <section style={{ ...card, display: 'flex', alignItems: 'center', gap: 14 }}>
        <span style={{
          width: 44, height: 44, borderRadius: 12, background: 'var(--navy3)', color: 'var(--navy)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, flexShrink: 0,
        }}>
          {initials(user.name || '?')}
        </span>
        <div style={{ minWidth: 0 }}>
          <div style={{ fontWeight: 700, fontSize: '0.95rem' }}>{user.name || 'Account'}</div>
          <div style={{ fontSize: '0.8rem', color: 'var(--muted)', marginTop: 2 }}>
            {ROLE_LABEL[user.role]} · {branchContext}
            {user.orgId != null && <> · Org #{user.orgId}</>}
          </div>
        </div>
      </section>

      <Collapsible title="Change password" summary="Update the password you use to sign in.">
        <ChangePasswordForm />
      </Collapsible>

      {user.role === 'OWNER' && (
        <Collapsible title="Organization" summary="Name and cashier branch access for your dealership group." defaultOpen>
          <OrgSettingsForm />
        </Collapsible>
      )}
    </div>
  )
}

/** A settings row that reads as a menu item: a header you click to expand its controls. */
function Collapsible({ title, summary, defaultOpen = false, children }: {
  title: string
  summary: string
  defaultOpen?: boolean
  children: ReactNode
}) {
  const [open, setOpen] = useState(defaultOpen)
  return (
    <section style={{ ...card, padding: 0, overflow: 'hidden' }}>
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        style={{
          width: '100%', display: 'flex', alignItems: 'center', gap: 12,
          padding: '14px 18px', background: 'transparent', border: 'none',
          textAlign: 'left', cursor: 'pointer',
        }}
      >
        <span style={{ flex: 1, minWidth: 0 }}>
          <span style={{ display: 'block', fontWeight: 700, fontSize: '0.9rem', color: 'var(--ink)' }}>{title}</span>
          <span style={{ display: 'block', fontSize: '0.78rem', color: 'var(--muted)', marginTop: 2 }}>{summary}</span>
        </span>
        <span style={{
          color: 'var(--faint)', fontSize: '0.8rem', flexShrink: 0,
          transition: 'transform var(--dur) var(--ease)', transform: open ? 'rotate(90deg)' : 'none',
        }}>
          ▶
        </span>
      </button>
      {open && (
        <div className="dams-anim-notice" style={{ padding: '16px 18px', borderTop: '1px solid var(--line)' }}>
          {children}
        </div>
      )}
    </section>
  )
}

function ChangePasswordForm() {
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')
  const [done, setDone] = useState(false)
  const [saving, setSaving] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError('')
    setDone(false)
    if (next.length < 8) return setError('New password must be at least 8 characters.')
    if (next !== confirm) return setError('New passwords do not match.')
    setSaving(true)
    try {
      await authApi.changePassword({ currentPassword: current, newPassword: next })
      setCurrent(''); setNext(''); setConfirm(''); setDone(true)
    } catch (e2) {
      setError(apiError(e2, 'Could not change your password.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <Field label="Current password"><TextInput value={current} onChange={setCurrent} type="password" required /></Field>
      <Field label="New password" hint="At least 8 characters"><TextInput value={next} onChange={setNext} type="password" required /></Field>
      <Field label="Confirm new password"><TextInput value={confirm} onChange={setConfirm} type="password" required /></Field>
      <ErrorBanner message={error} />
      {done && <div style={{ fontSize: '0.82rem', color: 'var(--green)', fontWeight: 600 }}>Password changed.</div>}
      <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
        <button type="submit" style={primaryBtn(saving)} disabled={saving}>
          {saving ? <><Spinner /> Saving…</> : 'Update password'}
        </button>
      </div>
    </form>
  )
}

function OrgSettingsForm() {
  const [settings, setSettings] = useState<OrgSettings | null>(null)
  const [error, setError] = useState('')
  const [savingKey, setSavingKey] = useState<string>('')

  useEffect(() => {
    orgSettingsApi.get().then((r) => setSettings(r.data)).catch((e) => setError(apiError(e, 'Could not load settings.')))
  }, [])

  async function save(patch: { name?: string; multiBranchCashierAccess?: boolean }, key: string) {
    setError('')
    setSavingKey(key)
    try {
      const { data } = await orgSettingsApi.update(patch)
      setSettings(data)
    } catch (e) {
      setError(apiError(e, 'Could not save.'))
    } finally {
      setSavingKey('')
    }
  }

  if (!settings) {
    return error
      ? <ErrorBanner message={error} />
      : <div style={{ fontSize: '0.82rem', color: 'var(--muted)' }}>Loading…</div>
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Field label="Organization name">
        <div style={{ display: 'flex', gap: 8 }}>
          <TextInput value={settings.name} onChange={(v) => setSettings({ ...settings, name: v })} />
          <button
            style={ghostBtn}
            disabled={savingKey === 'name'}
            onClick={() => save({ name: settings.name }, 'name')}
          >
            {savingKey === 'name' ? '…' : 'Save'}
          </button>
        </div>
      </Field>

      <label style={{ display: 'flex', gap: 10, alignItems: 'flex-start', fontSize: '0.85rem' }}>
        <input
          type="checkbox"
          checked={settings.multiBranchCashierAccess}
          disabled={savingKey === 'toggle'}
          onChange={(e) => save({ multiBranchCashierAccess: e.target.checked }, 'toggle')}
          style={{ marginTop: 3 }}
        />
        <span>
          <strong>Multi-branch cashier access</strong>
          <div style={{ color: 'var(--muted)', fontSize: '0.8rem', marginTop: 2 }}>
            When on, cashiers can search and see customers &amp; job cards across all branches.
            It never changes which branch a cashier's own documents post under. Default off.
          </div>
        </span>
      </label>

      <ErrorBanner message={error} />
    </div>
  )
}
