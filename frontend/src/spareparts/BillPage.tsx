import { useCallback, useEffect, useState } from 'react'
import { apiMessage, sparePartsApi, type MovementRow } from '../api/spareParts'
import { ErrorBanner, SkeletonRows, card, cardTitle, fmtDateTime, td, th } from '../shell/ui'
import BillForm from './BillForm'
import { useSpareAccess } from './SpareAccessContext'
import { FlashBanner, Notice, SignedQty, mono, useFlash } from './spareUi'

/** Bill a part to a job card, with the latest bills underneath so the person sees it landed. */
export default function BillPage() {
  const { access, can } = useSpareAccess()
  const myBranches = access.branches.filter((b) => access.actBranchIds.includes(b.id))
  const flash = useFlash()
  const [recent, setRecent] = useState<MovementRow[] | null>(null)
  const [error, setError] = useState('')
  // Re-mounting the form after a bill clears it for the next one.
  const [formKey, setFormKey] = useState(0)

  const load = useCallback(async () => {
    try {
      const { data } = await sparePartsApi.movements({ type: 'BILLED', limit: 12 })
      setRecent(data)
    } catch (e) {
      setError(apiMessage(e, 'Could not load recent bills.'))
    }
  }, [])

  useEffect(() => { load() }, [load])

  if (!can('BILL_PART')) {
    return (
      <section style={card}>
        <Notice tone="warn">
          Your role is not allowed to bill parts. {access.owner
            ? 'The Owner has view-only access; use Switch role to act as Inventory Manager at a branch.'
            : 'The Owner can allow this in Masters, under Spare Parts.'}
        </Notice>
      </section>
    )
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <FlashBanner message={flash.message} />
      <section style={card}>
        <h2 style={cardTitle}>Bill a part to a job card</h2>
        <BillForm
          key={formKey}
          myBranches={myBranches}
          allBranches={access.branches}
          onDone={(m) => { flash.show(m); setFormKey((k) => k + 1); load() }}
        />
      </section>

      <ErrorBanner message={error} />
      <section style={{ ...card, padding: 0, overflow: 'hidden' }}>
        <h2 style={{ ...cardTitle, padding: '16px 18px 0', margin: '0 0 10px' }}>Latest bills</h2>
        {recent == null ? (
          <div style={{ padding: 16 }}><SkeletonRows rows={3} height={36} /></div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.82rem', minWidth: 640 }}>
              <thead>
                <tr style={{ background: 'var(--bg)' }}>
                  <th style={th}>When</th><th style={th}>Branch</th><th style={th}>Part</th>
                  <th style={{ ...th, textAlign: 'right' }}>Qty</th><th style={th}>Job card</th><th style={th}>Vehicle</th><th style={th}>By</th>
                </tr>
              </thead>
              <tbody>
                {recent.map((m) => (
                  <tr key={m.id}>
                    <td style={{ ...td, whiteSpace: 'nowrap' }}>{fmtDateTime(m.createdAt)}</td>
                    <td style={{ ...td, ...mono }}>{m.branchCode}</td>
                    <td style={td}><span style={{ ...mono, fontWeight: 700 }}>{m.partCode}</span><div style={{ fontSize: '0.74rem', color: 'var(--muted)' }}>{m.description}</div></td>
                    <td style={{ ...td, textAlign: 'right' }}><SignedQty value={m.qtyChange} /></td>
                    <td style={{ ...td, ...mono }}>{m.reference ?? '—'}</td>
                    <td style={{ ...td, ...mono }}>{m.vehicleNo ?? '—'}</td>
                    <td style={td}>{m.userName}</td>
                  </tr>
                ))}
                {recent.length === 0 && <tr><td style={{ ...td, padding: '24px 16px', color: 'var(--muted)' }} colSpan={7}>Nothing has been billed yet.</td></tr>}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}
