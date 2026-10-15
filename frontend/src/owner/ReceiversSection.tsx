import { useEffect, useState, type FormEvent } from 'react'
import { receiversApi, type Receiver } from '../api/receivers'
import { mastersApi, type MasterRow } from '../api/masters'
import {
  Badge, ErrorBanner, Field, Modal, TextInput, inputStyle,
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
export default function ReceiversSection() {
  const [rows, setRows] = useState<Receiver[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [modal, setModal] = useState<{ editing: Receiver | null } | null>(null)
  const [categories, setCategories] = useState<MasterRow[]>([])
  // rev 77 — filter the list by name or phone number as you type
  const [query, setQuery] = useState('')

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

  const needle = query.trim().toLowerCase()
  const digits = query.replace(/\D/g, '')
  const shownRows = needle === '' ? rows : rows.filter((r) =>
    r.name.toLowerCase().includes(needle)
    || (digits.length > 0 && (r.phone ?? '').replace(/\D/g, '').includes(digits)))

  useEffect(() => { load() }, [])
  useEffect(() => {
    mastersApi.list('expense-categories').then(({ data }) => setCategories(data.filter((c) => c.active))).catch(() => setCategories([]))
  }, [])

  return (
    <section style={card} aria-label="Receivers and vendors">
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 14, flexWrap: 'wrap' }}>
        <h2 style={{ ...cardTitle, marginBottom: 0, flex: 1 }}>Receivers / vendors</h2>
        <button style={{ ...primaryBtn(), minHeight: 36 }} onClick={() => setModal({ editing: null })}>+ Add</button>
      </div>
      <ErrorBanner message={error} />
      <input
        type="search"
        aria-label="Search vendors"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Search by name or phone number"
        style={{ ...inputStyle, marginBottom: 12, maxWidth: 360 }}
      />
      <div style={{ overflowX: 'auto', WebkitOverflowScrolling: 'touch' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 620, fontSize: '0.84rem' }}>
          <thead>
            <tr>
              <th style={th}>Name</th><th style={th}>Phone</th><th style={th}>Expense categories</th>
              <th style={th}>Status</th><th style={th}></th>
            </tr>
          </thead>
          <tbody>
            {shownRows.map((r) => (
              <tr key={r.id}>
                <td style={td}>{r.name}</td>
                <td style={td}>{r.phone ?? '—'}</td>
                <td style={td}>
                  {r.categoryIds.length === 0
                    ? <span style={{ color: 'var(--muted)' }}>Not mapped</span>
                    : r.categoryIds.map((id) => categories.find((c) => c.id === id)?.name ?? `#${id}`).join(', ')}
                </td>
                <td style={td}>{r.active ? <Badge tone="green">Active</Badge> : <Badge>Inactive</Badge>}</td>
                <td style={{ ...td, textAlign: 'right' }}>
                  <button style={{ ...ghostBtn, minHeight: 36, padding: '4px 12px' }} onClick={() => setModal({ editing: r })}>Edit</button>
                </td>
              </tr>
            ))}
            {!loading && shownRows.length === 0 && (
              <tr><td style={td} colSpan={5}>{rows.length === 0 ? 'Nothing here yet.' : 'No vendor matches your search.'}</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {modal && (
        <ReceiverModal
          editing={modal.editing}
          categories={categories}
          onClose={() => setModal(null)}
          onSaved={() => { setModal(null); load() }}
        />
      )}
    </section>
  )
}

function ReceiverModal(props: { editing: Receiver | null; categories: MasterRow[]; onClose: () => void; onSaved: () => void }) {
  const { editing } = props
  const [categoryIds, setCategoryIds] = useState<number[]>(editing?.categoryIds ?? [])
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
      const body = { name: name.trim(), phone: phone.trim() === '' ? undefined : phone.trim(), active, categoryIds }
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
    <Modal title={editing == null ? 'Add vendor' : 'Edit vendor'} onClose={props.onClose} maxWidth={520}>
      <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <ErrorBanner message={error} />
        <Field label="Name">
          <TextInput value={name} onChange={setName} placeholder="Vendor name" required maxLength={160} />
        </Field>
        <Field label="Phone (optional)">
          <TextInput value={phone} onChange={setPhone} placeholder="Phone number" maxLength={32} />
        </Field>
        <Field label="Expense categories this vendor supplies">
          <div role="group" aria-label="Expense categories" style={{ display: 'flex', flexWrap: 'wrap', gap: '6px 16px' }}>
            {props.categories.map((c) => (
              <label key={c.id} style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: '0.84rem' }}>
                <input
                  type="checkbox"
                  checked={categoryIds.includes(c.id)}
                  onChange={(e) => setCategoryIds((cur) => e.target.checked ? [...cur, c.id] : cur.filter((x) => x !== c.id))}
                />
                {c.name}
              </label>
            ))}
            {props.categories.length === 0 && <span style={{ fontSize: '0.8rem', color: 'var(--muted)' }}>No expense categories yet.</span>}
          </div>
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
