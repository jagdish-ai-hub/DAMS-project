import { useEffect, useState } from 'react'
import { QRCodeSVG } from 'qrcode.react'
import { mastersApi, type MasterRow } from '../api/masters'
import { inr, ghostBtn } from '../shell/ui'
import { QrCode, Copy, Check, X, ShieldCheck } from 'lucide-react'

interface Props {
  amount: number
  docRef?: string
  customerName?: string
  onClose: () => void
}

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

/**
 * Shows one QR card per UPI ID the Owner has configured under Masters → UPI IDs
 * (each org's own real VPA — no hardcoded demo account). Zero configured shows a
 * message pointing there instead of a fake, useless QR code.
 */
export default function UpiQrModal({ amount, docRef = 'SERVICE', customerName, onClose }: Props) {
  const [vpas, setVpas] = useState<MasterRow[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let live = true
    mastersApi.list('upi-vpas')
      .then(({ data }) => { if (live) setVpas(data.filter((v) => v.active)) })
      .catch((e) => { if (live) setError(apiError(e, 'Could not load UPI IDs.')) })
    return () => { live = false }
  }, [])

  const cleanAmount = Number(amount) > 0 ? Number(amount).toFixed(2) : '0.00'

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="bg-[var(--surface)] border border-[var(--line)] rounded-xl max-w-lg w-full shadow-2xl overflow-hidden animate-in fade-in zoom-in-95 duration-150">
        {/* Modal Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-[var(--line)] bg-[var(--surface-muted)]">
          <div className="flex items-center gap-2">
            <QrCode className="w-5 h-5 text-[var(--purple,#6B3FA0)]" />
            <h3 className="text-base font-bold text-[var(--text)]">Instant UPI Payment QR</h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded-md text-[var(--muted)] hover:text-[var(--text)] hover:bg-[var(--line)] transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-6 text-center">
          <div className="text-2xl font-extrabold text-[var(--text)] tabular-nums mb-1">
            {inr(Number(cleanAmount))}
          </div>
          <div className="flex items-center justify-center gap-1.5 text-xs text-[var(--green,#2E7D32)] font-medium mb-4">
            <ShieldCheck className="w-4 h-4" />
            <span>Exact amount pre-filled · Zero typo risk</span>
          </div>

          {error && <p className="text-sm text-[var(--red,#B91C1C)] mb-2">{error}</p>}

          {vpas == null && !error && (
            <p className="text-sm text-[var(--muted)] py-6">Loading UPI IDs…</p>
          )}

          {vpas != null && vpas.length === 0 && (
            <p className="text-sm text-[var(--muted)] py-6 max-w-[280px] mx-auto leading-relaxed">
              No UPI ID configured yet. Ask your Owner to add one under Masters → UPI IDs.
            </p>
          )}

          {vpas != null && vpas.length > 0 && (
            <div className={vpas.length > 1 ? 'grid grid-cols-1 sm:grid-cols-2 gap-3' : ''}>
              {vpas.map((v) => (
                <UpiCard key={v.id} vpa={v} amount={cleanAmount} docRef={docRef} customerName={customerName} />
              ))}
            </div>
          )}

          {docRef && (
            <div className="mt-4 text-[11px] font-mono bg-[var(--bg)] px-2.5 py-1 rounded border border-[var(--line)] text-[var(--muted)] inline-block">
              Ref: {docRef}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function UpiCard({ vpa, amount, docRef, customerName }: {
  vpa: MasterRow
  amount: string
  docRef?: string
  customerName?: string
}) {
  const [copied, setCopied] = useState(false)
  const transactionNote = encodeURIComponent(`Payment ${docRef}${customerName ? ' - ' + customerName : ''}`)
  const encodedPayee = encodeURIComponent(vpa.name)
  const upiUri = `upi://pay?pa=${vpa.vpa}&pn=${encodedPayee}&am=${amount}&cu=INR&tn=${transactionNote}&tr=${encodeURIComponent(docRef ?? '')}`

  function copyUpi() {
    navigator.clipboard.writeText(upiUri).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    })
  }

  return (
    <div className="flex flex-col items-center border border-[var(--line)] rounded-xl p-4">
      <div className="text-xs font-semibold text-[var(--muted)] uppercase tracking-wider mb-2">
        {vpa.name}
      </div>
      <div className="p-3 bg-white rounded-2xl shadow-inner border border-neutral-200 inline-block mb-2">
        <QRCodeSVG value={upiUri} size={160} level="M" includeMargin={false} />
      </div>
      <div className="text-[11px] font-mono text-[var(--faint)] mb-2">{vpa.vpa}</div>
      <button
        type="button"
        onClick={copyUpi}
        style={{ ...ghostBtn, fontSize: '0.76rem', minHeight: 32, padding: '4px 10px' }}
        className="flex items-center gap-1.5"
        title="Copy UPI payment URI link"
      >
        {copied ? <Check className="w-3.5 h-3.5 text-green-600" /> : <Copy className="w-3.5 h-3.5" />}
        <span>{copied ? 'Copied!' : 'Copy Link'}</span>
      </button>
    </div>
  )
}
