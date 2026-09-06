import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { mastersApi, type MasterRow, type MasterRequest, type MasterTypeSlug } from '../api/masters'
import {
  Badge, ErrorBanner, Field, Modal, TextInput,
  card, ghostBtn, inputStyle, primaryBtn, td, th,
} from '../shell/ui'

type Extra = 'claim' | 'mode' | 'sub' | undefined
const TABS: { slug: MasterTypeSlug; label: string; extra: Extra }[] = [
  { slug: 'receive-categories', label: 'Receipt categories', extra: 'claim' },
  { slug: 'receive-statuses', label: 'Receipt statuses', extra: undefined },
  { slug: 'settlement-modes', label: 'Settlement modes', extra: 'mode' },
  { slug: 'expense-categories', label: 'Expense departments', extra: undefined },
  { slug: 'expense-sub-categories', label: 'Expense sub-categories', extra: 'sub' },
  { slug: 'expense-modes', label: 'Expense modes', extra: undefined },
  { slug: 'expense-statuses', label: 'Expense statuses', extra: undefined },
  { slug: 'banks', label: 'Banks', extra: undefined },
]

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

export default function MastersPage() {
  const [tab, setTab] = useState(TABS[0])
  const [rows, setRows] = useState<MasterRow[]>([])
  const [categories, setCategories] = useState<MasterRow[]>([])
  const [parentId, setParentId] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [modal, setModal] = useState<{ editing: MasterRow | null } | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      if (tab.extra === 'sub') {
        const cats = await mastersApi.list('expense-categories')
        setCategories(cats.data)
        const pid = parentId ?? cats.data[0]?.id ?? null
        setParentId(pid)
        const r = pid != null ? await mastersApi.list('expense-sub-categories', pid) : { data: [] }
        setRows(r.data)
      } else {
        const r = await mastersApi.list(tab.slug)
        setRows(r.data)
      }
    } catch (e) {
      setError(apiError(e, 'Could not load this list.'))
    } finally {
      setLoading(false)
    }
  }, [tab, parentId])

  useEffect(() => {
    load()
  }, [load])

  function switchTab(next: typeof TABS[number]) {
    setParentId(null)
    setRows([])
    setTab(next)
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <h1 style={{ fontSize: '1.15rem', fontWeight: 700, color: 'var(--navy)' }}>Masters</h1>
      <p style={{ fontSize: '0.82rem', color: 'var(--muted)', marginTop: -8 }}>
        Every dropdown the app shows comes from these lists. Rows are deactivated, never deleted.
      </p>

      <div className="flex flex-col md:flex-row gap-4 items-start">
        {/* left rail on desktop, horizontal tab bar on mobile */}
        <nav
          style={{ ...card, padding: 8 }}
          className="w-full md:w-56 flex flex-row md:flex-col gap-1 overflow-x-auto md:overflow-x-visible scrollbar-thin"
        >
          {TABS.map((t) => (
            <button
              key={t.slug}
              onClick={() => switchTab(t)}
              style={{
                textAlign: 'left', border: 'none', borderRadius: 8, padding: '9px 12px',
                fontSize: '0.83rem', fontWeight: 600, cursor: 'pointer',
                background: t.slug === tab.slug ? 'var(--navy3)' : 'transparent',
                color: t.slug === tab.slug ? 'var(--navy)' : 'var(--ink)',
                minHeight: 38, whiteSpace: 'nowrap',
              }}
            >
              {t.label}
            </button>
          ))}
        </nav>

        {/* table */}
        <section style={{ ...card }} className="flex-1 w-full min-w-0">
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 14, flexWrap: 'wrap' }}>
            <h2 style={{ fontSize: '0.92rem', fontWeight: 700, color: 'var(--ink)', flex: 1 }}>{tab.label}</h2>
            {tab.extra === 'sub' && (
              <select
                value={parentId ?? ''}
                onChange={(e) => setParentId(Number(e.target.value))}
                style={{ ...inputStyle, width: 'auto', minHeight: 36 }}
              >
                {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            )}
            <button
              style={{ ...primaryBtn(), minHeight: 36 }}
              disabled={tab.extra === 'sub' && parentId == null}
              onClick={() => setModal({ editing: null })}
            >
              + Add
            </button>
          </div>

          <ErrorBanner message={error} />
          <div style={{ overflowX: 'auto', WebkitOverflowScrolling: 'touch' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 500, fontSize: '0.84rem' }}>
              <thead>
                <tr>
                  <th style={th}>Name</th>
                  {tab.extra === 'claim' && <th style={th}>Claim?</th>}
                  {tab.extra === 'mode' && <th style={th}>Requires</th>}
                  {tab.extra === 'sub' && <th style={th}>Limit</th>}
                  <th style={th}>Status</th><th style={th}></th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.id}>
                    <td style={td}>{r.name}</td>
                    {tab.extra === 'claim' && <td style={td}>{r.isClaim ? <Badge tone="amber">Claim</Badge> : '—'}</td>}
                    {tab.extra === 'mode' && (
                      <td style={td}>
                        {[r.requiresBank && 'bank', r.requiresRef && 'ref'].filter(Boolean).join(' + ') || '—'}
                      </td>
                    )}
                    {tab.extra === 'sub' && <td style={td}>{r.limitAmount != null ? `₹${r.limitAmount}` : '—'}</td>}
                    <td style={td}>{r.active ? <Badge tone="green">Active</Badge> : <Badge>Inactive</Badge>}</td>
                    <td style={{ ...td, textAlign: 'right' }}>
                      <button style={{ ...ghostBtn, minHeight: 36, padding: '4px 12px' }} onClick={() => setModal({ editing: r })}>Edit</button>
                    </td>
                  </tr>
                ))}
                {!loading && rows.length === 0 && (
                  <tr><td style={td} colSpan={6}>Nothing here yet.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </section>
      </div>

      {modal && (
        <MasterModal
          tab={tab}
          parentId={parentId}
          editing={modal.editing}
          onClose={() => setModal(null)}
          onSaved={() => { setModal(null); load() }}
        />
      )}
    </div>
  )
}

function MasterModal(props: {
  tab: { slug: MasterTypeSlug; label: string; extra: Extra }
  parentId: number | null
  editing: MasterRow | null
  onClose: () => void
  onSaved: () => void
}) {
  const { tab, editing } = props
  const [name, setName] = useState(editing?.name ?? '')
  const [sortOrder, setSortOrder] = useState(String(editing?.sortOrder ?? ''))
  const [active, setActive] = useState(editing?.active ?? true)
  const [isClaim, setIsClaim] = useState(editing?.isClaim ?? false)
  const [requiresBank, setRequiresBank] = useState(editing?.requiresBank ?? false)
  const [requiresRef, setRequiresRef] = useState(editing?.requiresRef ?? false)
  const [limitAmount, setLimitAmount] = useState(editing?.limitAmount != null ? String(editing.limitAmount) : '')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError('')
    setSaving(true)
    try {
      const body: MasterRequest = { name }
      if (sortOrder !== '') body.sortOrder = Number(sortOrder)
      if (editing) body.active = active
      if (tab.extra === 'claim') body.isClaim = isClaim
      if (tab.extra === 'mode') { body.requiresBank = requiresBank; body.requiresRef = requiresRef }
      if (tab.extra === 'sub') {
        body.expenseCategoryId = editing?.expenseCategoryId ?? props.parentId ?? undefined
        body.limitAmount = limitAmount === '' ? null : Number(limitAmount)
      }
      if (editing) await mastersApi.update(tab.slug, editing.id, body)
      else await mastersApi.create(tab.slug, body)
      props.onSaved()
    } catch (e2) {
      setError(apiError(e2, 'Could not save.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={editing ? `Edit — ${tab.label}` : `Add — ${tab.label}`} onClose={props.onClose}>
      <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <Field label="Name"><TextInput value={name} onChange={setName} required /></Field>
        <Field label="Sort order" hint="Optional — controls dropdown order">
          <TextInput value={sortOrder} onChange={setSortOrder} type="number" placeholder="0" />
        </Field>

        {tab.extra === 'claim' && (
          <label style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: '0.85rem' }}>
            <input type="checkbox" checked={isClaim} onChange={(e) => setIsClaim(e.target.checked)} />
            This is a claim category (Finance Manager closes it with a final override)
          </label>
        )}
        {tab.extra === 'mode' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <label style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: '0.85rem' }}>
              <input type="checkbox" checked={requiresBank} onChange={(e) => setRequiresBank(e.target.checked)} />
              Requires a bank name
            </label>
            <label style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: '0.85rem' }}>
              <input type="checkbox" checked={requiresRef} onChange={(e) => setRequiresRef(e.target.checked)} />
              Requires a transaction reference
            </label>
          </div>
        )}
        {tab.extra === 'sub' && (
          <Field label="Per-line limit (₹)" hint="Optional — over this, the expense is flagged (not blocked)">
            <TextInput value={limitAmount} onChange={setLimitAmount} type="number" placeholder="e.g. 2000" />
          </Field>
        )}

        {editing && (
          <Field label="Status">
            <div style={{ display: 'flex', gap: 6 }}>
              {[true, false].map((v) => (
                <button
                  key={String(v)} type="button" onClick={() => setActive(v)}
                  style={{
                    ...ghostBtn,
                    minHeight: 36,
                    padding: '6px 14px',
                    background: active === v ? 'var(--navy)' : 'transparent',
                    color: active === v ? '#fff' : 'var(--navy)',
                    borderColor: active === v ? 'var(--navy)' : 'var(--line)',
                  }}
                >
                  {v ? 'Active' : 'Inactive'}
                </button>
              ))}
            </div>
          </Field>
        )}

        <ErrorBanner message={error} />
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <button type="button" style={{ ...ghostBtn, minHeight: 36 }} onClick={props.onClose}>Cancel</button>
          <button type="submit" style={{ ...primaryBtn(saving), minHeight: 36 }} disabled={saving}>
            {saving ? 'Saving…' : editing ? 'Save changes' : 'Add'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
