import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { QRCodeSVG } from 'qrcode.react'
import { mastersApi, type MasterRow } from '../api/masters'
import { inr, ghostBtn, SkeletonRows } from '../shell/ui'
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

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        e.stopPropagation()
        onClose()
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  const cleanAmount = Number(amount) > 0 ? Number(amount).toFixed(2) : '0.00'

  // Portalled to body (escapes the parent Modal's entrance transform), elevated
  // above the parent dim (z 70 vs 50), scrollable on short screens. No
  // `overflow-hidden` on the card so the lift shadow is never hard-clipped.
  return createPortal(
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Instant UPI Payment QR"
      onMouseDown={onClose}
      className="dams-anim-backdrop"
      style={{
        position: 'fixed', inset: 0, zIndex: 70, background: 'rgba(16,24,40,.55)',
        display: 'flex', justifyContent: 'center', padding: 'clamp(12px, 3vh, 32px) clamp(10px, 3vw, 16px)',
        overflowY: 'auto',
      }}
    >
      <div
        onMouseDown={(e) => e.stopPropagation()}
        className="dams-anim-modal"
        style={{
          background: 'var(--surface)', border: '1px solid var(--line)', borderRadius: 12,
          boxShadow: 'var(--shadow-lift)', width: '100%', maxWidth: 512, margin: 'auto 0',
          maxHeight: 'min(calc(100dvh - 32px), 720px)', display: 'flex', flexDirection: 'column',
        }}
      >
        {/* Modal Header */}
        <div
          className="flex items-center justify-between px-5 py-4 border-b border-[var(--line)]"
          style={{ background: 'var(--surface)', borderTopLeftRadius: 12, borderTopRightRadius: 12, flexShrink: 0 }}
        >
          <div className="flex items-center gap-2">
            <QrCode className="w-5 h-5" style={{ color: 'var(--purple)' }} />
            <h3 className="text-base font-bold" style={{ color: 'var(--ink)' }}>Instant UPI Payment QR</h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="rounded-md transition-colors"
            style={{
              minWidth: 44, minHeight: 44, display: 'flex', alignItems: 'center', justifyContent: 'center',
              background: 'none', border: 'none', color: 'var(--muted)', cursor: 'pointer',
            }}
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-6 text-center" style={{ overflowY: 'auto', flex: 1, minHeight: 0 }}>
          <div className="text-2xl font-extrabold tabular-nums mb-1" style={{ color: 'var(--ink)' }}>
            {inr(Number(cleanAmount))}
          </div>
          <div className="flex items-center justify-center gap-1.5 text-xs font-medium mb-4" style={{ color: 'var(--green)' }}>
            <ShieldCheck className="w-4 h-4" />
            <span>Exact amount pre-filled · Zero typo risk</span>
          </div>

          {error && <p className="text-sm mb-2" style={{ color: 'var(--red)' }}>{error}</p>}

          {vpas == null && !error && (
            <div style={{ padding: '12px 0' }}><SkeletonRows rows={2} height={120} /></div>
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
            <div className="mt-4 text-[11px] font-mono px-2.5 py-1 rounded border border-[var(--line)] inline-block" style={{ background: 'var(--bg)', color: 'var(--muted)' }}>
              Ref: {docRef}
            </div>
          )}
        </div>
      </div>
    </div>,
    document.body,
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
