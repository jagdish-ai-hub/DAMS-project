import { useEffect, useState, type CSSProperties, type FormEvent } from 'react'
import { adminApi, type Organization, type OrgCreationResult } from '../api/admin'

/**
 * Stage 1 Super Admin screen: list organizations, onboard a new one, and toggle active.
 * On create, the invite link is shown for the Super Admin to copy and send manually
 * (v1 has no email provider — see plan.md rev 3). The polished panel lands in Stage 10.
 */
export default function OrganizationsPage() {
  const [orgs, setOrgs] = useState<Organization[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [orgName, setOrgName] = useState('')
  const [ownerName, setOwnerName] = useState('')
  const [ownerEmail, setOwnerEmail] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [created, setCreated] = useState<OrgCreationResult | null>(null)

  async function loadOrgs() {
    setLoading(true)
    setError('')
    try {
      const { data } = await adminApi.listOrganizations()
      setOrgs(data)
    } catch {
      setError('Could not load organizations.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadOrgs()
  }, [])

  async function handleCreate(e: FormEvent) {
    e.preventDefault()
    setError('')
    setCreated(null)
    setSubmitting(true)
    try {
      const { data } = await adminApi.createOrganization({ orgName, ownerName, ownerEmail })
      setCreated(data)
      setOrgName('')
      setOwnerName('')
      setOwnerEmail('')
      loadOrgs()
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        ?? 'Could not create the organization.'
      setError(msg)
    } finally {
      setSubmitting(false)
    }
  }

  async function toggleActive(org: Organization) {
    try {
      await adminApi.toggleActive(org.id, !org.active)
      loadOrgs()
    } catch {
      setError(`Could not update "${org.name}".`)
    }
  }

  async function remove(org: Organization) {
    const typed = window.prompt(
      `This permanently deletes "${org.name}" and ALL its data — branches, users, masters. ` +
      `This cannot be undone.\n\nType the organization name to confirm:`,
    )
    if (typed == null) return
    if (typed.trim() !== org.name) {
      setError('Name did not match — nothing was deleted.')
      return
    }
    try {
      await adminApi.deleteOrganization(org.id)
      loadOrgs()
    } catch {
      setError(`Could not delete "${org.name}".`)
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <h1 style={{ fontSize: '1.15rem', fontWeight: 700, color: 'var(--navy)' }}>Organizations</h1>

      <section style={card}>
        <h2 style={cardTitle}>Onboard a new organization</h2>
        <form onSubmit={handleCreate} style={{ display: 'grid', gap: 12, maxWidth: 460 }}>
          <Field label="Organization name" value={orgName} onChange={setOrgName} required />
          <Field label="First owner — name" value={ownerName} onChange={setOwnerName} required />
          <Field label="First owner — email" type="email" value={ownerEmail} onChange={setOwnerEmail} required />
          <button type="submit" disabled={submitting} style={primaryBtn(submitting)}>
            {submitting ? 'Creating…' : 'Create organization & invite owner'}
          </button>
        </form>

        {created && (
          <div style={{
            marginTop: 14,
            background: 'var(--green-bg)',
            border: '1px solid #BFE3CD',
            borderRadius: 8,
            padding: '12px 14px',
            fontSize: '0.82rem',
          }}>
            <div style={{ fontWeight: 700, color: 'var(--green)', marginBottom: 6 }}>
              "{created.orgName}" created (org #{created.orgId}).
            </div>
            <div style={{ color: 'var(--ink)', marginBottom: 6 }}>
              Send this invite link to the owner — they set their own password:
            </div>
            <code style={{
              display: 'block',
              background: '#fff',
              border: '1px solid var(--line)',
              borderRadius: 6,
              padding: '8px 10px',
              wordBreak: 'break-all',
              fontSize: '0.78rem',
            }}>
              {created.inviteLink}
            </code>
            <button
              type="button"
              onClick={() => navigator.clipboard?.writeText(created.inviteLink)}
              style={{ ...ghostBtn, marginTop: 8 }}
            >
              Copy link
            </button>
          </div>
        )}
      </section>

      <section style={card}>
        <h2 style={cardTitle}>All organizations</h2>
        {error && <p style={{ color: 'var(--red)', fontSize: '0.82rem' }}>{error}</p>}
        {loading ? (
          <p style={{ color: 'var(--muted)', fontSize: '0.85rem' }}>Loading…</p>
        ) : orgs.length === 0 ? (
          <p style={{ color: 'var(--muted)', fontSize: '0.85rem' }}>No organizations yet.</p>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.84rem' }}>
            <thead>
              <tr style={{ textAlign: 'left', color: 'var(--muted)' }}>
                <th style={th}>#</th>
                <th style={th}>Name</th>
                <th style={th}>Status</th>
                <th style={th}></th>
              </tr>
            </thead>
            <tbody>
              {orgs.map((org) => (
                <tr key={org.id} style={{ borderTop: '1px solid var(--line)' }}>
                  <td style={td}>{org.id}</td>
                  <td style={td}>{org.name}</td>
                  <td style={td}>
                    <span style={{
                      fontSize: '0.74rem',
                      fontWeight: 700,
                      color: org.active ? 'var(--green)' : 'var(--gray)',
                    }}>
                      {org.active ? 'Active' : 'Inactive'}
                    </span>
                  </td>
                  <td style={{ ...td, textAlign: 'right', whiteSpace: 'nowrap' }}>
                    <button type="button" onClick={() => toggleActive(org)} style={ghostBtn}>
                      {org.active ? 'Deactivate' : 'Activate'}
                    </button>
                    <button
                      type="button"
                      onClick={() => remove(org)}
                      style={{ ...ghostBtn, color: 'var(--red)', borderColor: '#EBC2C2', marginLeft: 8 }}
                    >
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  )
}

function Field(props: {
  label: string
  value: string
  onChange: (v: string) => void
  type?: string
  required?: boolean
}) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
      <span style={{ fontSize: '0.76rem', fontWeight: 600, color: 'var(--muted)' }}>{props.label}</span>
      <input
        type={props.type ?? 'text'}
        value={props.value}
        required={props.required}
        onChange={(e) => props.onChange(e.target.value)}
        style={{
          border: '1.5px solid var(--line)',
          borderRadius: 8,
          padding: '9px 11px',
          fontSize: '0.86rem',
          outline: 'none',
        }}
      />
    </label>
  )
}

const card: CSSProperties = {
  background: 'var(--surface)',
  border: '1px solid var(--line)',
  borderRadius: 'var(--radius)',
  boxShadow: 'var(--shadow)',
  padding: '20px 22px',
}
const cardTitle: CSSProperties = {
  fontSize: '0.92rem',
  fontWeight: 700,
  color: 'var(--ink)',
  marginBottom: 14,
}
const th: CSSProperties = { padding: '6px 8px', fontWeight: 600 }
const td: CSSProperties = { padding: '9px 8px' }

function primaryBtn(disabled: boolean): CSSProperties {
  return {
    background: disabled ? 'var(--navy2)' : 'var(--navy)',
    color: '#fff',
    border: 'none',
    borderRadius: 8,
    padding: '10px 14px',
    fontWeight: 700,
    fontSize: '0.85rem',
    cursor: disabled ? 'not-allowed' : 'pointer',
  }
}
const ghostBtn: CSSProperties = {
  background: 'transparent',
  border: '1px solid var(--line)',
  borderRadius: 7,
  padding: '5px 10px',
  fontSize: '0.78rem',
  fontWeight: 600,
  color: 'var(--navy)',
}
