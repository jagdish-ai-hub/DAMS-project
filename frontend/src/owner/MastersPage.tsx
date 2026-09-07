import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { mastersApi, type MasterRow, type MasterRequest, type MasterTypeSlug } from '../api/masters'
import { budgetsApi, monthKeyOf } from '../api/budgets'
import {
  Badge, ErrorBanner, Field, Modal, TextInput,
  card, ghostBtn, inputStyle, primaryBtn, td, th, inr, istToday,
} from '../shell/ui'
import AiMastersStrip from './AiMastersStrip'
import ReceiversSection from './ReceiversSection'
import HelpButton from '../help/HelpButton'

type Extra = 'claim' | 'mode' | 'sub' | 'trigger' | undefined
const TABS: { slug: MasterTypeSlug; label: string; extra: Extra }[] = [
  { slug: 'receive-categories', label: 'Receipt categories', extra: 'claim' },
  { slug: 'receive-statuses', label: 'Receipt statuses', extra: undefined },
  { slug: 'settlement-modes', label: 'Settlement modes', extra: 'mode' },
  { slug: 'expense-categories', label: 'Expense departments', extra: undefined },
  { slug: 'expense-sub-categories', label: 'Expense sub-categories', extra: 'sub' },
  { slug: 'expense-modes', label: 'Expense modes', extra: 'mode' },
  { slug: 'expense-statuses', label: 'Expense statuses', extra: 'trigger' },
  { slug: 'banks', label: 'Banks', extra: undefined },
]

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

/** Tolerant parse of GET /masters/{type}/usage — backend MasterUsageResponse is
 *  {id, name, useCount, usedLast90d}; older shapes {id, usedCount}/{id: count}
 *  are also accepted. Unknown shapes yield an empty map. */
function toUsageMap(data: unknown): Record<number, number> {
  const map: Record<number, number> = {}
  if (Array.isArray(data)) {
    for (const r of data as { id?: unknown; useCount?: unknown; usedCount?: unknown; count?: unknown }[]) {
      if (r && typeof r.id === 'number') {
        const n = r.useCount ?? r.usedCount ?? r.count
        map[r.id] = typeof n === 'number' ? n : Number(n) || 0
      }
    }
  } else if (data && typeof data === 'object') {
    for (const [k, v] of Object.entries(data as Record<string, unknown>)) {
      map[Number(k)] = typeof v === 'number' ? v : Number(v) || 0
    }
  }
  return map
}

export default function MastersPage() {
  const [tab, setTab] = useState(TABS[0])
  const [rows, setRows] = useState<MasterRow[]>([])
  const [categories, setCategories] = useState<MasterRow[]>([])
  const [parentId, setParentId] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [modal, setModal] = useState<{ editing: MasterRow | null } | null>(null)
  // 90-day usage counts for the deactivate guard (null = backend has no usage
  // endpoint yet — guard stays hidden instead of erroring).
  const [usageById, setUsageById] = useState<Record<number, number> | null>(null)
  // This month's expense budgets by category id (expense-categories tab only;
  // null = no budgets endpoint yet — column stays hidden).
  const [budgetsByCat, setBudgetsByCat] = useState<Record<number, number> | null>(null)
  const budgetMonth = monthKeyOf(istToday())

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
      // Usage guard — best effort, skipped silently when unimplemented (404).
      mastersApi.usage(tab.slug)
        .then(({ data }) => setUsageById(toUsageMap(data)))
        .catch(() => setUsageById(null))
      // Monthly budgets for the expense-categories tab — same 404 tolerance.
      if (tab.slug === 'expense-categories' && budgetMonth) {
        budgetsApi.list(budgetMonth)
          .then(({ data }) => {
            const byCat: Record<number, number> = {}
            for (const row of data) byCat[row.categoryId] = row.cap
            setBudgetsByCat(byCat)
          })
          .catch(() => setBudgetsByCat(null))
      } else {
        setBudgetsByCat(null)
      }
    } catch (e) {
      setError(apiError(e, 'Could not load this list.'))
    } finally {
      setLoading(false)
    }
  }, [tab, parentId, budgetMonth])

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
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <h1 style={{ fontSize: '1.15rem', fontWeight: 700, color: 'var(--navy)' }}>Masters</h1>
        <HelpButton slug="masters" />
      </div>
      <p style={{ fontSize: '0.82rem', color: 'var(--muted)', marginTop: -8 }}>
        Every dropdown the app shows comes from these lists. Rows are deactivated, never deleted.
      </p>
      <AiMastersStrip />

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
                  {tab.extra === 'mode' && <th style={th}>Cash?</th>}
                  {tab.extra === 'trigger' && <th style={th}>Triggers claim?</th>}
                  {tab.extra === 'sub' && <th style={th}>Limit</th>}
                  {tab.slug === 'expense-categories' && budgetsByCat != null && <th style={th}>Budget · {budgetMonth}</th>}
                  <th style={th}>Status</th><th style={th}></th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.id}>
                    <td style={td}>
                      <div>{r.name}</div>
                      {usageById != null && (usageById[r.id] ?? 0) > 0 && (
                        <div style={{ fontSize: '0.7rem', color: 'var(--faint)', marginTop: 2 }}>
                          used {usageById[r.id]}x in 90 days
                        </div>
                      )}
                    </td>
                    {tab.extra === 'claim' && <td style={td}>{r.isClaim ? <Badge tone="amber">Claim</Badge> : '—'}</td>}
                    {tab.extra === 'mode' && (
                      <td style={td}>
                        {[r.requiresBank && 'bank', r.requiresRef && 'ref'].filter(Boolean).join(' + ') || '—'}
                      </td>
                    )}
                    {tab.extra === 'mode' && (
                      <td style={td}>{r.isCash ? <Badge tone="green">Cash</Badge> : '—'}</td>
                    )}
                    {tab.extra === 'trigger' && (
                      <td style={td}>{r.triggersClaim ? <Badge tone="amber">To claim</Badge> : '—'}</td>
                    )}
                    {tab.extra === 'sub' && <td style={td}>{r.limitAmount != null ? `₹${r.limitAmount}` : '—'}</td>}
                    {tab.slug === 'expense-categories' && budgetsByCat != null && (
                      <td style={td}>
                        <BudgetCell
                          key={`${r.id}-${budgetsByCat[r.id] ?? 'none'}`}
                          categoryId={r.id}
                          categoryName={r.name}
                          monthKey={budgetMonth}
                          initialCap={budgetsByCat[r.id]}
                        />
                      </td>
                    )}
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

      <ReceiversSection />

      {modal && (
        <MasterModal
          tab={tab}
          parentId={parentId}
          editing={modal.editing}
          usageCount={modal.editing ? (usageById?.[modal.editing.id] ?? 0) : 0}
          usageKnown={usageById != null}
          critical={tab.extra === 'claim' || tab.extra === 'mode'}
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
  usageCount: number
  usageKnown: boolean
  critical: boolean
  onClose: () => void
  onSaved: () => void
}) {
  const { tab, editing, usageCount, usageKnown, critical } = props
  const [name, setName] = useState(editing?.name ?? '')
  const [sortOrder, setSortOrder] = useState(String(editing?.sortOrder ?? ''))
  const [active, setActive] = useState(editing?.active ?? true)
  const [isClaim, setIsClaim] = useState(editing?.isClaim ?? false)
  const [requiresBank, setRequiresBank] = useState(editing?.requiresBank ?? false)
  const [requiresRef, setRequiresRef] = useState(editing?.requiresRef ?? false)
  const [isCash, setIsCash] = useState(editing?.isCash ?? false)
  const [triggersClaim, setTriggersClaim] = useState(editing?.triggersClaim ?? false)
  const [limitAmount, setLimitAmount] = useState(editing?.limitAmount != null ? String(editing.limitAmount) : '')
  const [confirmDeactivate, setConfirmDeactivate] = useState(false)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  // Deactivate guard: rows used in the last 90 days warn first. Claim and
  // mode rows are load-bearing (claim-closing, drawer math) — deactivating a
  // used one is blocked outright. Other rows need an explicit confirmation.
  const deactivating = editing != null && !active
  const used = usageKnown && usageCount > 0
  const blocked = deactivating && used && critical
  const needsConfirm = deactivating && used && !critical

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError('')
    setSaving(true)
    try {
      const body: MasterRequest = { name }
      if (sortOrder !== '') body.sortOrder = Number(sortOrder)
      if (editing) body.active = active
      if (tab.extra === 'claim') body.isClaim = isClaim
      if (tab.extra === 'mode') { body.requiresBank = requiresBank; body.requiresRef = requiresRef; body.isCash = isCash }
      if (tab.extra === 'trigger') body.triggersClaim = triggersClaim
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
            <label style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: '0.85rem' }}>
              <input type="checkbox" checked={isCash} onChange={(e) => setIsCash(e.target.checked)} />
              Cash mode — counts toward the cash drawer position
            </label>
          </div>
        )}
        {tab.extra === 'trigger' && (
          <label style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: '0.85rem' }}>
            <input type="checkbox" checked={triggersClaim} onChange={(e) => setTriggersClaim(e.target.checked)} />
            Transfer-to-claim status — expenses moved to a warranty / AMC / goodwill claim
          </label>
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
        {blocked && (
          <div style={{
            background: 'var(--red-bg)', border: '1px solid #EBC2C2', color: 'var(--red)',
            borderRadius: 8, padding: '10px 12px', fontSize: '0.82rem',
          }}>
            Used {usageCount}x in 90 days — this {critical ? 'claim/mode' : ''} row is still in use and
            cannot be deactivated. Deactivate-never-delete stays: keep it active.
          </div>
        )}
        {needsConfirm && (
          <label style={{ display: 'flex', gap: 8, alignItems: 'flex-start', fontSize: '0.83rem', color: 'var(--amber)' }}>
            <input type="checkbox" checked={confirmDeactivate} onChange={(e) => setConfirmDeactivate(e.target.checked)} style={{ marginTop: 3 }} />
            <span>Used {usageCount}x in 90 days — still deactivate? History is kept; the row just hides from forms.</span>
          </label>
        )}
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <button type="button" style={{ ...ghostBtn, minHeight: 36 }} onClick={props.onClose}>Cancel</button>
          <button
            type="submit"
            style={{ ...primaryBtn(saving || blocked || (needsConfirm && !confirmDeactivate)), minHeight: 36 }}
            disabled={saving || blocked || (needsConfirm && !confirmDeactivate)}
          >
            {saving ? 'Saving…' : editing ? 'Save changes' : 'Add'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

/**
 * Monthly budget input for one expense category (Owner only — this page is
 * Owner-only). Saves via POST /api/v1/budgets; failures surface inline and
 * never break the surrounding masters CRUD.
 */
function BudgetCell({ categoryId, categoryName, monthKey, initialCap }: {
  categoryId: number
  categoryName: string
  monthKey: string
  initialCap: number | undefined
}) {
  const [cap, setCap] = useState(initialCap != null ? String(initialCap) : '')
  const [savedCap, setSavedCap] = useState<number | undefined>(initialCap)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const dirty = cap !== (savedCap != null ? String(savedCap) : '')

  async function save() {
    if (!dirty || saving) return
    const n = Number(cap)
    if (cap.trim() === '' || !Number.isFinite(n) || n < 0) {
      setError('Enter a non-negative amount.')
      return
    }
    setError('')
    setSaving(true)
    try {
      const { data } = await budgetsApi.upsert(categoryId, monthKey, n)
      setSavedCap(data.cap)
      setCap(String(data.cap))
    } catch (e) {
      setError(apiError(e, `Could not save the budget for ${categoryName}.`))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 6, minWidth: 150 }}>
      <span style={{ color: 'var(--muted)' }}>₹</span>
      <input
        type="number"
        min={0}
        aria-label={`Monthly budget for ${categoryName}`}
        value={cap}
        placeholder="No cap"
        onChange={(e) => setCap(e.target.value)}
        onBlur={() => void save()}
        onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); void save() } }}
        style={{ ...inputStyle, width: 100, minHeight: 34, padding: '5px 8px', fontSize: '0.8rem' }}
      />
      {saving ? (
        <span style={{ fontSize: '0.72rem', color: 'var(--faint)' }}>…</span>
      ) : (
        dirty && (
          <button type="button" onClick={() => void save()} style={{ ...ghostBtn, minHeight: 34, padding: '4px 10px' }}>
            Set
          </button>
        )
      )}
      {savedCap != null && !dirty && (
        <span style={{ fontSize: '0.7rem', color: 'var(--faint)', fontVariantNumeric: 'tabular-nums' }}>{inr(savedCap)}</span>
      )}
      {error && (
        <span style={{ fontSize: '0.7rem', color: 'var(--red)' }}>{error}</span>
      )}
    </div>
  )
}
