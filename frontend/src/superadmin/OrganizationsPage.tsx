import { useEffect, useState, type FormEvent } from 'react'
import { adminApi, type Organization, type OrgCreationResult } from '../api/admin'
import {
  card, cardTitle, th, td, primaryBtn, ghostBtn, dangerBtn,
  ErrorBanner, Modal, Badge, Field, TextInput, SkeletonRows, fmtDate,
} from '../shell/ui'

/**
 * Super Admin panel (Stage 10): list every organization, onboard a new one, toggle
 * active, and permanently delete. No new backend — the Stage 1 admin endpoints already
 * cover it. On create the invite link is shown for the Super Admin to copy and send by
 * hand (v1 has no email provider — plan.md rev 3).
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
  const [copied, setCopied] = useState(false)

  const [busyId, setBusyId] = useState<number | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<Organization | null>(null)

  async function loadOrgs() {
    setLoading(true)
    try {
      const { data } = await adminApi.listOrganizations()
      setOrgs(data)
      setError('')
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
    setCopied(false)
    setSubmitting(true)
    try {
      const { data } = await adminApi.createOrganization({ orgName, ownerName, ownerEmail })
      setCreated(data)
      setOrgName('')
      setOwnerName('')
      setOwnerEmail('')
      loadOrgs()
    } catch (err: unknown) {
      setError(
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        ?? 'Could not create the organization.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  async function toggleActive(org: Organization) {
    setBusyId(org.id)
    try {
      await adminApi.toggleActive(org.id, !org.active)
      await loadOrgs()
    } catch {
      setError(`Could not update "${org.name}".`)
    } finally {
      setBusyId(null)
    }
  }

  async function doDelete(org: Organization) {
    setBusyId(org.id)
    try {
      await adminApi.deleteOrganization(org.id)
      setConfirmDelete(null)
      await loadOrgs()
    } catch {
      setError(`Could not delete "${org.name}".`)
    } finally {
      setBusyId(null)
    }
  }

  function copyLink() {
    if (!created) return
    navigator.clipboard?.writeText(created.inviteLink)
    setCopied(true)
    window.setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <h1 style={{ fontSize: '1.15rem', fontWeight: 700, color: 'var(--navy)' }}>Organizations</h1>

      <ErrorBanner message={error} />

      <section style={card}>
        <h2 style={cardTitle}>Onboard a new organization</h2>
        <form onSubmit={handleCreate} style={{ display: 'grid', gap: 12, maxWidth: 460 }}>
          <Field label="Organization name">
            <TextInput value={orgName} onChange={setOrgName} required />
          </Field>
          <Field label="First owner — name">
            <TextInput value={ownerName} onChange={setOwnerName} required />
          </Field>
          <Field label="First owner — email">
            <TextInput value={ownerEmail} onChange={setOwnerEmail} type="email" required />
          </Field>
          <button type="submit" disabled={submitting} style={primaryBtn(submitting)}>
            {submitting ? 'Creating…' : 'Create organization & invite owner'}
          </button>
        </form>

        {created && (
          <div style={{
            marginTop: 14, background: 'var(--green-bg)', border: '1px solid #BFE3CD',
            borderRadius: 8, padding: '12px 14px', fontSize: '0.82rem',
          }}>
            <div style={{ fontWeight: 700, color: 'var(--green)', marginBottom: 6 }}>
              “{created.orgName}” created (org #{created.orgId}).
            </div>
            <div style={{ color: 'var(--ink)', marginBottom: 6 }}>
              Send this invite link to the owner — they set their own password:
            </div>
            <code style={{
              display: 'block', background: '#fff', border: '1px solid var(--line)',
              borderRadius: 6, padding: '8px 10px', wordBreak: 'break-all', fontSize: '0.78rem',
            }}>
              {created.inviteLink}
            </code>
            <button type="button" onClick={copyLink} style={{ ...ghostBtn, marginTop: 8 }}>
              {copied ? 'Copied ✓' : 'Copy link'}
            </button>
          </div>
        )}
      </section>

      <section style={card}>
        <h2 style={cardTitle}>All organizations</h2>
        {loading ? (
          <SkeletonRows rows={5} height={44} />
        ) : orgs.length === 0 ? (
          <p style={{ color: 'var(--muted)', fontSize: '0.85rem' }}>No organizations yet.</p>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.84rem' }}>
              <thead>
                <tr>
                  <th style={th}>#</th>
                  <th style={th}>Name</th>
                  <th style={th}>Created</th>
                  <th style={th}>Cashier access</th>
                  <th style={th}>Status</th>
                  <th style={th}></th>
                </tr>
              </thead>
              <tbody>
                {orgs.map((org) => (
                  <tr key={org.id}>
                    <td style={td}>{org.id}</td>
                    <td style={{ ...td, fontWeight: 600 }}>{org.name}</td>
                    <td style={td}>{fmtDate(org.createdAt)}</td>
                    <td style={td}>{org.multiBranchCashierAccess ? 'All branches' : 'Home branch'}</td>
                    <td style={td}>
                      <Badge tone={org.active ? 'green' : 'gray'}>{org.active ? 'Active' : 'Inactive'}</Badge>
                    </td>
                    <td style={{ ...td, textAlign: 'right', whiteSpace: 'nowrap' }}>
                      <button
                        type="button"
                        onClick={() => toggleActive(org)}
                        disabled={busyId === org.id}
                        style={ghostBtn}
                      >
                        {org.active ? 'Deactivate' : 'Activate'}
                      </button>
                      <button
                        type="button"
                        onClick={() => setConfirmDelete(org)}
                        disabled={busyId === org.id}
                        style={{ ...ghostBtn, color: 'var(--red)', borderColor: '#EBC2C2', marginLeft: 8 }}
                      >
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {confirmDelete && (
        <DeleteOrgModal
          org={confirmDelete}
          busy={busyId === confirmDelete.id}
          onCancel={() => setConfirmDelete(null)}
          onConfirm={() => doDelete(confirmDelete)}
        />
      )}
    </div>
  )
}

function DeleteOrgModal(props: {
  org: Organization
  busy: boolean
  onCancel: () => void
  onConfirm: () => void
}) {
  const [typed, setTyped] = useState('')
  const match = typed.trim() === props.org.name

  return (
    <Modal title="Delete organization" subtitle={props.org.name} onClose={props.onCancel}>
      <p style={{ fontSize: '0.84rem', color: 'var(--ink)', margin: 0 }}>
        This permanently deletes <strong>{props.org.name}</strong> and all of its data —
        branches, users, masters, job cards, documents, audit history. This cannot be undone.
      </p>
      <Field label={`Type the organization name to confirm`}>
        <TextInput value={typed} onChange={setTyped} placeholder={props.org.name} />
      </Field>
      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
        <button type="button" onClick={props.onCancel} style={ghostBtn} disabled={props.busy}>Cancel</button>
        <button
          type="button"
          onClick={props.onConfirm}
          disabled={!match || props.busy}
          style={{ ...dangerBtn, opacity: !match || props.busy ? 0.5 : 1 }}
        >
          {props.busy ? 'Deleting…' : 'Delete permanently'}
        </button>
      </div>
    </Modal>
  )
}
