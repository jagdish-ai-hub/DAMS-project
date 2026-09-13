import { useEffect, useMemo, useState } from 'react'
import { mastersApi, type MasterRow } from '../api/masters'
import { receiptsApi, type ReceiveDocument } from '../api/receipts'
import { Modal, Field, ErrorBanner, inputStyle, primaryBtn, ghostBtn, inr, istToday, Spinner } from '../shell/ui'
import { QrCode } from 'lucide-react'
import UpiQrModal from './UpiQrModal'

/**
 * Add Payment (intial ui prototypes/cashier-home.html). Appends ONE settlement line to the
 * job card's existing open receive document — never creates a second document.
 */
export default function AddPaymentModal(props: {
  receiptId: number
  customerName: string
  jobReference: string
  documentNo: string | null
  balanceDue: number
  /** Warranty / AMC / CG job card — its lines may be ₹0. */
  isClaim?: boolean
  onClose: () => void
  onDone: (updated: ReceiveDocument) => void
}) {
  const [modes, setModes] = useState<MasterRow[]>([])
  const [banks, setBanks] = useState<MasterRow[]>([])
  const [date, setDate] = useState(istToday())
  const [modeId, setModeId] = useState<number | ''>('')
  const [amount, setAmount] = useState(props.balanceDue > 0 ? String(props.balanceDue) : '')
  const [bankId, setBankId] = useState<number | ''>('')
  const [transactionRef, setTransactionRef] = useState('')
  const [remark, setRemark] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [showUpiQr, setShowUpiQr] = useState(false)

  useEffect(() => {
    Promise.all([mastersApi.list('settlement-modes'), mastersApi.list('banks')])
      .then(([m, b]) => {
        const activeModes = m.data.filter((x) => x.active)
        setModes(activeModes)
        setBanks(b.data.filter((x) => x.active))
        setModeId(activeModes[0]?.id ?? '')
      })
      .catch((e) => setError(apiError(e, 'Could not load payment options.')))
  }, [])

  const mode = useMemo(() => modes.find((m) => m.id === modeId), [modes, modeId])

  async function save() {
    // A Warranty/AMC/CGW claim may collect nothing from the customer — the OEM covers it and
    // the real figure lands at Close Claim — so a claim line may be ₹0. Plain lines must be > 0.
    if (amount.trim() === '') {
      setError('Enter an amount')
      return
    }
    if (props.isClaim ? Number(amount) < 0 : !(Number(amount) > 0)) {
      setError(props.isClaim ? 'Amount cannot be negative' : 'Enter an amount above 0')
      return
    }
    if (modeId === '') {
      setError('Pick a settlement mode')
      return
    }
    if (mode?.requiresBank && bankId === '') {
      setError(`${mode.name} needs a bank`)
      return
    }
    setBusy(true)
    setError('')
    try {
      const { data } = await receiptsApi.addLine(props.receiptId, {
        transactionDate: date,
        settlementModeId: Number(modeId),
        amount: Number(amount),
        bankId: bankId === '' ? null : Number(bankId),
        transactionRef: transactionRef || undefined,
        remark: remark || undefined,
      })
      if (file) {
        const newLine = data.lines[data.lines.length - 1]
        if (newLine) {
          try {
            await receiptsApi.attachToLine(props.receiptId, newLine.lineNo, file)
          } catch {
            /* line saved; attachment can be added later from View Receipts */
          }
        }
      }
      props.onDone(data)
    } catch (e) {
      setError(apiError(e, 'Could not add the payment.'))
    } finally {
      setBusy(false)
    }
  }

  const nextLineLabel = props.documentNo ? `${props.documentNo}-L(next)` : 'the existing receipt'

  return (
    <Modal
      title="Add Payment"
      subtitle={`${props.customerName} · ${props.jobReference}${props.documentNo ? ` · ${props.documentNo}` : ''}`}
      onClose={props.onClose}
      footer={
        <>
          <button type="button" onClick={props.onClose} style={{ ...ghostBtn, minHeight: 38 }} disabled={busy}>Cancel</button>
          <button type="button" onClick={save} style={{ ...primaryBtn(busy), minHeight: 38, display: 'inline-flex', alignItems: 'center', gap: 6 }} disabled={busy}>
            {busy ? <><Spinner /> Adding…</> : 'Add Payment'}
          </button>
        </>
      }
    >
      <div style={{ background: 'var(--amber-bg)', color: 'var(--amber)', borderRadius: 8, padding: '9px 12px', fontSize: '0.82rem', fontWeight: 600 }}>
        Balance due: {inr(props.balanceDue)} — this adds a line to {nextLineLabel}.
      </div>
      <ErrorBanner message={error} />

      <Field label="Date">
        <input type="date" value={date} onChange={(e) => setDate(e.target.value)} style={inputStyle} />
      </Field>
      <Field label="Settlement Mode *">
        <select value={modeId} onChange={(e) => setModeId(Number(e.target.value))} style={inputStyle}>
          {modes.map((m) => <option key={m.id} value={m.id}>{m.name}</option>)}
        </select>
      </Field>
      <Field label="Amount *">
        <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
          <input type="number" value={amount} onChange={(e) => setAmount(e.target.value)} style={{ ...inputStyle, flex: 1 }} />
          {(mode?.name?.toLowerCase().includes('upi') || Number(amount) > 0) && (
            <button
              type="button"
              onClick={() => setShowUpiQr(true)}
              title="Show UPI QR Code"
              style={{
                ...ghostBtn,
                padding: '6px 10px',
                minHeight: 38,
                display: 'inline-flex',
                alignItems: 'center',
                gap: 4,
                color: 'var(--navy)',
              }}
            >
              <QrCode size={16} />
              <span style={{ fontSize: '0.75rem', fontWeight: 600 }}>UPI QR</span>
            </button>
          )}
        </div>
      </Field>
      {mode?.requiresBank && (
        <Field label="Bank">
          <select value={bankId} onChange={(e) => setBankId(e.target.value === '' ? '' : Number(e.target.value))} style={inputStyle}>
            <option value="">Select…</option>
            {banks.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
          </select>
        </Field>
      )}
      {mode?.requiresRef && (
        <Field label="Transaction ID">
          <input value={transactionRef} onChange={(e) => setTransactionRef(e.target.value)} style={inputStyle} />
        </Field>
      )}
      <Field label="Remark">
        <input value={remark} onChange={(e) => setRemark(e.target.value)} style={inputStyle} />
      </Field>

      <label style={{
        border: '1.5px dashed var(--line)', borderRadius: 8, padding: '9px 12px', fontSize: '0.82rem',
        color: 'var(--navy2)', fontWeight: 600, cursor: 'pointer', display: 'block',
      }}>
        📎 {file ? `${file.name} (change)` : 'Attach receipt (optional)'}
        <input
          type="file"
          accept=".pdf,image/*"
          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          style={{ display: 'none' }}
        />
      </label>

      {showUpiQr && (
        <UpiQrModal
          amount={Number(amount) || 0}
          customerName={props.customerName}
          docRef={props.documentNo ?? props.jobReference}
          onClose={() => setShowUpiQr(false)}
        />
      )}
    </Modal>
  )
}

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}
