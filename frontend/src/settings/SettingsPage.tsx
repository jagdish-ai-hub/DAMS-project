import { useEffect, useState, type FormEvent } from 'react'
import { authApi } from '../api/auth'
import { orgSettingsApi, type OrgSettings } from '../api/orgSettings'
import { useAuth } from '../auth/useAuth'
import { card, cardTitle, ErrorBanner, Field, ghostBtn, primaryBtn, TextInput } from '../shell/ui'

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

export default function SettingsPage() {
  const { user } = useAuth()

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20, maxWidth: 560 }}>
      <h1 style={{ fontSize: '1.15rem', fontWeight: 700, color: 'var(--navy)' }}>Settings</h1>
      <ChangePasswordCard />
      {user?.role === 'OWNER' && <OrgSettingsCard />}
    </div>
  )
}

function ChangePasswordCard() {
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
    <section style={card}>
      <h2 style={cardTitle}>Change password</h2>
      <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <Field label="Current password"><TextInput value={current} onChange={setCurrent} type="password" required /></Field>
        <Field label="New password" hint="At least 8 characters"><TextInput value={next} onChange={setNext} type="password" required /></Field>
        <Field label="Confirm new password"><TextInput value={confirm} onChange={setConfirm} type="password" required /></Field>
        <ErrorBanner message={error} />
        {done && <div style={{ fontSize: '0.82rem', color: 'var(--green)', fontWeight: 600 }}>Password changed.</div>}
        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <button type="submit" style={primaryBtn(saving)} disabled={saving}>
            {saving ? 'Saving…' : 'Update password'}
          </button>
        </div>
      </form>
    </section>
  )
}

function OrgSettingsCard() {
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
    return <section style={card}><h2 style={cardTitle}>Organization</h2><ErrorBanner message={error} /></section>
  }

  return (
    <section style={card}>
      <h2 style={cardTitle}>Organization</h2>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <Field label="Organization name">
          <div style={{ display: 'flex', gap: 8 }}>
            <TextInput
              value={settings.name}
              onChange={(v) => setSettings({ ...settings, name: v })}
            />
            <button
              style={ghostBtn}
              disabled={savingKey === 'name'}
              onClick={() => save({ name: settings.name }, 'name')}
            >
              {savingKey === 'name' ? '…' : 'Save'}
            </button>
          </div>
        </Field>

        <div>
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
        </div>
        <ErrorBanner message={error} />
      </div>
    </section>
  )
}
