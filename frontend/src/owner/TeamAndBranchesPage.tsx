import { useEffect, useState, type FormEvent } from 'react'
import { branchesApi, type Branch, type BranchRequest } from '../api/branches'
import { usersApi, type TeamUser, type UserRequest } from '../api/users'
import type { Role } from '../auth/AuthContext'
import {
  Badge, ErrorBanner, Field, Modal, TextInput,
  card, cardTitle, ghostBtn, inputStyle, primaryBtn, td, th,
} from '../shell/ui'

const ROLE_OPTS: { value: Role; label: string; branchMode: 'none' | 'single' | 'multi' }[] = [
  { value: 'OWNER', label: 'Owner', branchMode: 'none' },
  { value: 'FINANCE_MANAGER', label: 'Finance Manager', branchMode: 'none' },
  { value: 'ACCOUNTANT', label: 'Accountant', branchMode: 'multi' },
  { value: 'CASHIER', label: 'Cashier', branchMode: 'single' },
]

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

export default function TeamAndBranchesPage() {
  const [branches, setBranches] = useState<Branch[]>([])
  const [users, setUsers] = useState<TeamUser[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [branchModal, setBranchModal] = useState<{ editing: Branch | null } | null>(null)
  const [userModal, setUserModal] = useState<{ editing: TeamUser | null } | null>(null)

  async function reload() {
    setLoading(true)
    setError('')
    try {
      const [b, u] = await Promise.all([branchesApi.list(), usersApi.list()])
      setBranches(b.data)
      setUsers(u.data)
    } catch (e) {
      setError(apiError(e, 'Could not load the team.'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    reload()
  }, [])

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <h1 style={{ fontSize: '1.15rem', fontWeight: 700, color: 'var(--navy)' }}>Team &amp; Branches</h1>
      <ErrorBanner message={error} />

      {/* Branches */}
      <section style={card}>
        <div style={{ display: 'flex', alignItems: 'center', marginBottom: 14 }}>
          <h2 style={{ ...cardTitle, marginBottom: 0, flex: 1 }}>Branches</h2>
          <button style={primaryBtn()} onClick={() => setBranchModal({ editing: null })}>+ Add Branch</button>
        </div>
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.84rem' }}>
            <thead>
              <tr>
                <th style={th}>Branch</th><th style={th}>Code</th>
                <th style={th}>Users assigned</th><th style={th}>Status</th><th style={th}></th>
              </tr>
            </thead>
            <tbody>
              {branches.map((b) => (
                <tr key={b.id}>
                  <td style={td}>{b.name}</td>
                  <td style={td}><code>{b.code}</code></td>
                  <td style={td}>{b.assignedUserCount}</td>
                  <td style={td}>
                    {b.active ? <Badge tone="green">Active</Badge> : <Badge>Inactive</Badge>}
                  </td>
                  <td style={{ ...td, textAlign: 'right' }}>
                    <button style={ghostBtn} onClick={() => setBranchModal({ editing: b })}>Edit</button>
                  </td>
                </tr>
              ))}
              {!loading && branches.length === 0 && (
                <tr><td style={td} colSpan={5}>No branches yet.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      {/* Users */}
      <section style={card}>
        <div style={{ display: 'flex', alignItems: 'center', marginBottom: 14 }}>
          <h2 style={{ ...cardTitle, marginBottom: 0, flex: 1 }}>Users</h2>
          <button style={primaryBtn()} onClick={() => setUserModal({ editing: null })}>+ Add User</button>
        </div>
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.84rem' }}>
            <thead>
              <tr>
                <th style={th}>User</th><th style={th}>Role</th>
                <th style={th}>Branch access</th><th style={th}>Status</th><th style={th}></th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id}>
                  <td style={td}>
                    <div style={{ fontWeight: 600 }}>{u.name}</div>
                    <div style={{ fontSize: '0.76rem', color: 'var(--muted)' }}>{u.email}</div>
                  </td>
                  <td style={td}>{ROLE_OPTS.find((r) => r.value === u.role)?.label ?? u.role}</td>
                  <td style={td}>{u.branchAccessLabel}</td>
                  <td style={td}>
                    {!u.active ? <Badge>Inactive</Badge>
                      : u.invitePending ? <Badge tone="amber">Invite pending</Badge>
                      : <Badge tone="green">Active</Badge>}
                  </td>
                  <td style={{ ...td, textAlign: 'right' }}>
                    <button style={ghostBtn} onClick={() => setUserModal({ editing: u })}>Edit</button>
                  </td>
                </tr>
              ))}
              {!loading && users.length === 0 && (
                <tr><td style={td} colSpan={5}>No users yet.</td></tr>
              )}
            </tbody>
          </table>
        </div>
        <p style={{ fontSize: '0.76rem', color: 'var(--faint)', marginTop: 10 }}>
          Deactivating a user keeps their history intact — it never deletes past entries.
        </p>
      </section>

      {branchModal && (
        <BranchModal
          editing={branchModal.editing}
          onClose={() => setBranchModal(null)}
          onSaved={() => { setBranchModal(null); reload() }}
        />
      )}
      {userModal && (
        <UserModal
          editing={userModal.editing}
          branches={branches}
          onClose={() => setUserModal(null)}
          onSaved={() => { setUserModal(null); reload() }}
        />
      )}
    </div>
  )
}

// ---------- Branch modal ----------

function BranchModal(props: { editing: Branch | null; onClose: () => void; onSaved: () => void }) {
  const [name, setName] = useState(props.editing?.name ?? '')
  const [code, setCode] = useState(props.editing?.code ?? '')
  const [active, setActive] = useState(props.editing?.active ?? true)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError('')
    setSaving(true)
    try {
      const body: BranchRequest = { name, code, ...(props.editing ? { active } : {}) }
      if (props.editing) await branchesApi.update(props.editing.id, body)
      else await branchesApi.create(body)
      props.onSaved()
    } catch (e2) {
      setError(apiError(e2, 'Could not save the branch.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      title={props.editing ? 'Edit branch' : 'Add branch'}
      subtitle={props.editing ? props.editing.name : 'New branch location'}
      onClose={props.onClose}
    >
      <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <Field label="Branch name"><TextInput value={name} onChange={setName} required placeholder="e.g. Koraput" /></Field>
        <Field label="Branch code" hint="2–5 letters or digits, unique in your organization">
          <TextInput value={code} onChange={(v) => setCode(v.toUpperCase())} required maxLength={5} placeholder="e.g. OOK" />
        </Field>
        {props.editing && (
          <Field label="Status">
            <StatusToggle value={active} onChange={setActive} />
          </Field>
        )}
        <ErrorBanner message={error} />
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <button type="button" style={ghostBtn} onClick={props.onClose}>Cancel</button>
          <button type="submit" style={primaryBtn(saving)} disabled={saving}>
            {saving ? 'Saving…' : props.editing ? 'Save changes' : 'Add branch'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

// ---------- User modal ----------

function UserModal(props: { editing: TeamUser | null; branches: Branch[]; onClose: () => void; onSaved: () => void }) {
  const activeBranches = props.branches.filter((b) => b.active || props.editing)
  const [name, setName] = useState(props.editing?.name ?? '')
  const [email, setEmail] = useState(props.editing?.email ?? '')
  const [role, setRole] = useState<Role>(props.editing?.role ?? 'CASHIER')
  const [homeBranchId, setHomeBranchId] = useState<number | null>(
    props.editing?.homeBranchId ?? activeBranches[0]?.id ?? null,
  )
  const [branchIds, setBranchIds] = useState<number[]>(props.editing?.branchIds ?? [])
  const [active, setActive] = useState(props.editing?.active ?? true)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [inviteLink, setInviteLink] = useState('')

  const branchMode = ROLE_OPTS.find((r) => r.value === role)?.branchMode ?? 'none'

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError('')
    setSaving(true)
    try {
      const body: UserRequest = {
        name,
        email,
        role,
        ...(branchMode === 'single' ? { homeBranchId } : {}),
        ...(branchMode === 'multi' ? { branchIds } : {}),
        ...(props.editing ? { active } : {}),
      }
      if (props.editing) {
        await usersApi.update(props.editing.id, body)
        props.onSaved()
      } else {
        const { data } = await usersApi.create(body)
        if (data.inviteLink) setInviteLink(data.inviteLink)
        else props.onSaved()
      }
    } catch (e2) {
      setError(apiError(e2, 'Could not save the user.'))
    } finally {
      setSaving(false)
    }
  }

  if (inviteLink) {
    return (
      <Modal title="User invited" subtitle={name} onClose={props.onSaved}>
        <p style={{ fontSize: '0.85rem', color: 'var(--muted)' }}>
          Send this link to <strong>{email}</strong> — they set their own password:
        </p>
        <code style={{ display: 'block', background: 'var(--bg)', border: '1px solid var(--line)', borderRadius: 6, padding: '9px 10px', wordBreak: 'break-all', fontSize: '0.78rem' }}>
          {inviteLink}
        </code>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <button style={ghostBtn} onClick={() => navigator.clipboard?.writeText(inviteLink)}>Copy link</button>
          <button style={primaryBtn()} onClick={props.onSaved}>Done</button>
        </div>
      </Modal>
    )
  }

  return (
    <Modal
      title={props.editing ? 'Edit user' : 'Add user'}
      subtitle={props.editing ? props.editing.name : 'New team member'}
      onClose={props.onClose}
    >
      <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <Field label="Full name"><TextInput value={name} onChange={setName} required placeholder="e.g. Priya Nair" /></Field>
        <Field label="Email" hint={props.editing ? 'Login email cannot be changed here' : 'The invite is sent here'}>
          <TextInput value={email} onChange={setEmail} type="email" required disabled={!!props.editing} placeholder="you@example.com" />
        </Field>
        <Field label="Role">
          <select value={role} onChange={(e) => setRole(e.target.value as Role)} style={inputStyle}>
            {ROLE_OPTS.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
          </select>
        </Field>

        {branchMode === 'none' && (
          <div style={{ fontSize: '0.8rem', color: 'var(--muted)', background: 'var(--navy3)', borderRadius: 8, padding: '9px 11px' }}>
            This role has access to all branches — no selection needed.
          </div>
        )}
        {branchMode === 'single' && (
          <Field label="Home branch" hint="Every document this cashier creates posts under this branch">
            <select
              value={homeBranchId ?? ''}
              onChange={(e) => setHomeBranchId(Number(e.target.value))}
              style={inputStyle}
              required
            >
              {activeBranches.map((b) => <option key={b.id} value={b.id}>{b.name} ({b.code})</option>)}
            </select>
          </Field>
        )}
        {branchMode === 'multi' && (
          <Field label="Branches assigned" hint="Pick one or more">
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {activeBranches.map((b) => (
                <label key={b.id} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: '0.85rem' }}>
                  <input
                    type="checkbox"
                    checked={branchIds.includes(b.id)}
                    onChange={(e) =>
                      setBranchIds((prev) =>
                        e.target.checked ? [...prev, b.id] : prev.filter((x) => x !== b.id))
                    }
                  />
                  {b.name} ({b.code})
                </label>
              ))}
            </div>
          </Field>
        )}

        {props.editing && (
          <Field label="Status"><StatusToggle value={active} onChange={setActive} /></Field>
        )}
        <ErrorBanner message={error} />
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <button type="button" style={ghostBtn} onClick={props.onClose}>Cancel</button>
          <button type="submit" style={primaryBtn(saving)} disabled={saving}>
            {saving ? 'Saving…' : props.editing ? 'Save changes' : 'Add user'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function StatusToggle({ value, onChange }: { value: boolean; onChange: (v: boolean) => void }) {
  return (
    <div style={{ display: 'flex', gap: 6 }}>
      {[true, false].map((v) => (
        <button
          key={String(v)}
          type="button"
          onClick={() => onChange(v)}
          style={{
            ...ghostBtn,
            background: value === v ? 'var(--navy)' : 'transparent',
            color: value === v ? '#fff' : 'var(--navy)',
            borderColor: value === v ? 'var(--navy)' : 'var(--line)',
          }}
        >
          {v ? 'Active' : 'Inactive'}
        </button>
      ))}
    </div>
  )
}
