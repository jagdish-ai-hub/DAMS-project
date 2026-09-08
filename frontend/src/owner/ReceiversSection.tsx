import { useEffect, useState, type FormEvent } from 'react'
import { receiversApi, type Receiver } from '../api/receivers'
import {
  Badge, ErrorBanner, Field, Modal, TextInput,
  card, cardTitle, ghostBtn, primaryBtn, td, th,
} from '../shell/ui'

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

/**
 * Receivers / vendors master (AGENT.md Owner masters). Same deactivate-never-delete
 * rule as every other master list — deactivating hides the vendor from expense
 * forms without rewriting history.
 */
export default function ReceiversSection({ readOnly = false }: { readOnly?: boolean }) {
  const [rows, setRows] = useState<Receiver[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [modal, setModal] = useState<{ editing: Receiver | null } | null>(null)

  async function load() {
    setLoading(true)
    setError('')
    try {
      const { data } = await receiversApi.list()
      setRows(data)
    } catch (e) {
      setError(apiError(e, 'Could not load vendors.'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  return (
    <section style={card} aria-label="Receivers and vendors">
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 14, flexWrap: 'wrap' }}>
        <h2 style={{ ...cardTitle, marginBottom: 0, flex: 1 }}>Receivers / vendors</h2>
        {!readOnly && (
          <button style={{ ...primaryBtn(), minHeight: 36 }} onClick={() => setModal({ editing: null })}>+ Add</button>
        )}
      </div>
      <ErrorBanner message={error} />
      <div style={{ overflowX: 'auto', WebkitOverflowScrolling: 'touch' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 500, fontSize: '0.84rem' }}>
          <thead>
            <tr>
              <th style={th}>Name</th><th style={th}>Phone</th>
              <th style={th}>Status</th><th style={th}></th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id}>
                <td style={td}>{r.name}</td>
                <td style={td}>{r.phone ?? '—'}</td>
                <td style={td}>{r.active ? <Badge tone="green">Active</Badge> : <Badge>Inactive</Badge>}</td>
                <td style={{ ...td, textAlign: 'right' }}>
                  {!readOnly && (
                    <button style={{ ...ghostBtn, minHeight: 36, padding: '4px 12px' }} onClick={() => setModal({ editing: r })}>Edit</button>
                  )}
                </td>
              </tr>
            ))}
            {!loading && rows.length === 0 && (
              <tr><td style={td} colSpan={4}>Nothing here yet.</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {modal && (
        <ReceiverModal
          editing={modal.editing}
          onClose={() => setModal(null)}
          onSaved={() => { setModal(null); load() }}
        />
      )}
    </section>
  )
}

function ReceiverModal(props: { editing: Receiver | null; onClose: () => void; onSaved: () => void }) {
  const { editing } = props
  const [name, setName] = useState(editing?.name ?? '')
  const [phone, setPhone] = useState(editing?.phone ?? '')
  const [active, setActive] = useState(editing?.active ?? true)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError('')
    setSaving(true)
    try {
      const body = { name: name.trim(), phone: phone.trim() === '' ? undefined : phone.trim(), active }
      if (editing == null) {
        await receiversApi.create(body)
      } else {
        await receiversApi.update(editing.id, body)
      }
      props.onSaved()
    } catch (err) {
      setError(apiError(err, 'Could not save this vendor.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={editing == null ? 'Add vendor' : 'Edit vendor'} onClose={props.onClose} maxWidth={440}>
      <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <ErrorBanner message={error} />
        <Field label="Name">
          <TextInput value={name} onChange={setName} placeholder="Vendor name" required maxLength={160} />
        </Field>
        <Field label="Phone (optional)">
          <TextInput value={phone} onChange={setPhone} placeholder="Phone number" maxLength={32} />
        </Field>
        <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: '0.84rem' }}>
          <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
          Active (uncheck to deactivate — history is kept, the vendor just hides from forms)
        </label>
        <button type="submit" disabled={saving || name.trim() === ''} style={{ ...primaryBtn(saving || name.trim() === ''), minHeight: 38 }}>
          {saving ? 'Saving…' : editing == null ? 'Add vendor' : 'Save changes'}
        </button>
      </form>
    </Modal>
  )
}
