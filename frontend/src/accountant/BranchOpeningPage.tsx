import { useEffect, useState } from 'react'
import { cashApi, type BranchOpening } from '../api/cash'
import { card, cardTitle, ErrorBanner, SkeletonRows, Badge, inr, primaryBtn, inputStyle, fmtDate, istToday } from '../shell/ui'
import { apiError } from '../review/reviewShared'
import HelpButton from '../help/HelpButton'

/**
 * Branch opening (rev 97, AGENT.md "Cash page"): the one-time starting cash balance of each of the Accountant's
 * branches. A branch can't close its day until this is recorded; the rule always said "the Accountant sets it" but
 * no screen offered it, so a new branch's cashier hit "ask your Accountant" with nowhere for the Accountant to go.
 */
export default function BranchOpeningPage() {
  const [rows, setRows] = useState<BranchOpening[] | null>(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  function load() {
    cashApi.openings()
      .then(({ data }) => setRows(data))
      .catch((e) => setError(apiError(e, 'Could not load the branches.')))
  }
  useEffect(load, [])

  const todo = (rows ?? []).filter((r) => !r.locked)

  return (
    <div style={{ maxWidth: 860, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 }}>
        <div>
          <h1 style={{ fontSize: '1.3rem', color: 'var(--navy)', marginBottom: 4 }}>Branch opening</h1>
          <div style={{ fontSize: '0.85rem', color: 'var(--muted)' }}>
            The cash a branch starts with. A branch can’t close its day until this is set — once, for good.
          </div>
        </div>
        <HelpButton slug="setting-the-branch-opening" />
      </div>

      <ErrorBanner message={error} />
      {notice && (
        <div role="status" style={{ background: 'var(--green-bg)', color: 'var(--green)', borderRadius: 8, padding: '9px 13px', fontSize: '0.84rem', fontWeight: 600 }}>
          {notice}
        </div>
      )}
      {rows == null && !error && <SkeletonRows rows={3} height={90} />}
      {rows != null && rows.length === 0 && (
        <div style={{ ...card, color: 'var(--faint)', fontSize: '0.85rem' }}>You are not assigned to any branch yet — ask the Owner.</div>
      )}
      {rows != null && rows.length > 0 && todo.length === 0 && (
        <div style={{ ...card, color: 'var(--muted)', fontSize: '0.85rem' }}>Every branch of yours already has its opening. Nothing to do.</div>
      )}

      {(rows ?? []).map((r) => (
        <section key={r.branchId} style={card} aria-label={`${r.branchCode} ${r.branchName}`}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap', marginBottom: r.locked ? 0 : 14 }}>
            <h2 style={{ ...cardTitle, margin: 0 }}>
              <span style={{ fontFamily: 'Consolas, monospace', color: 'var(--navy)' }}>{r.branchCode}</span> {r.branchName}
            </h2>
            {r.set
              ? <Badge tone="green">Set</Badge>
              : r.locked ? <Badge tone="gray">Closed days already</Badge> : <Badge tone="amber">Not set</Badge>}
            {r.set && r.amount != null && (
              <span style={{ marginLeft: 'auto', fontSize: '0.88rem' }}>
                <strong>{inr(r.amount)}</strong> <span style={{ color: 'var(--muted)' }}>from {fmtDate(r.openingDate)}</span>
              </span>
            )}
          </div>
          {!r.locked && (
            <OpeningForm
              branch={r}
              onSaved={(msg) => { setNotice(msg); setError(''); load() }}
              onError={setError}
            />
          )}
        </section>
      ))}
    </div>
  )
}

function OpeningForm({ branch, onSaved, onError }: { branch: BranchOpening; onSaved: (msg: string) => void; onError: (msg: string) => void }) {
  const [date, setDate] = useState('')
  const [amount, setAmount] = useState('')
  const [busy, setBusy] = useState(false)
  const [problem, setProblem] = useState('')

  async function save() {
    setProblem('')
    onError('')
    if (!date) { setProblem('Pick the date the drawer started.'); return }
    if (amount.trim() === '' || Number.isNaN(Number(amount)) || Number(amount) < 0) {
      setProblem('Enter the cash counted in the drawer — 0 if it started empty.')
      return
    }
    setBusy(true)
    try {
      await cashApi.setOpening({ branchId: branch.branchId, openingDate: date, amount: Number(amount) })
      onSaved(`${branch.branchCode} ${branch.branchName}: opening ${inr(Number(amount))} saved — the cashier can close the day now.`)
    } catch (e) {
      onError(apiError(e, 'Could not save the opening.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12, alignItems: 'end' }}>
        <label style={{ display: 'grid', gap: 4, fontSize: '0.78rem', fontWeight: 600, color: 'var(--muted)' }}>
          Date the drawer started
          <input type="date" aria-label={`${branch.branchCode} opening date`} value={date} max={istToday()}
            onChange={(e) => setDate(e.target.value)} style={inputStyle} />
        </label>
        <label style={{ display: 'grid', gap: 4, fontSize: '0.78rem', fontWeight: 600, color: 'var(--muted)' }}>
          Cash counted (₹)
          <input type="number" inputMode="decimal" min={0} step="0.01" placeholder="0 if empty" aria-label={`${branch.branchCode} opening amount`}
            value={amount} onChange={(e) => setAmount(e.target.value)} style={inputStyle} />
        </label>
        <button type="button" onClick={save} disabled={busy} style={primaryBtn(busy)}>
          {busy ? 'Saving…' : 'Save opening'}
        </button>
      </div>
      <div style={{ fontSize: '0.76rem', color: 'var(--muted)', marginTop: 8 }}>
        Cash entries before this date are not counted. This can be set only once.
      </div>
      {problem && <div role="alert" style={{ fontSize: '0.8rem', color: 'var(--red)', marginTop: 6 }}>{problem}</div>}
    </div>
  )
}
