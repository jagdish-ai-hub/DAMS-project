import { useState } from 'react'
import { QRCodeSVG } from 'qrcode.react'
import { inr, ghostBtn, primaryBtn } from '../shell/ui'
import { QrCode, Copy, Check, X, ShieldCheck } from 'lucide-react'

interface Props {
  amount: number
  payeeName?: string
  vpa?: string
  docRef?: string
  customerName?: string
  onClose: () => void
}

export default function UpiQrModal({
  amount,
  payeeName = 'JJ Motors',
  vpa = 'jjmotors@icici',
  docRef = 'SERVICE',
  customerName,
  onClose,
}: Props) {
  const [copied, setCopied] = useState(false)

  // Standard NPCI UPI URI string
  const cleanAmount = Number(amount) > 0 ? Number(amount).toFixed(2) : '0.00'
  const transactionNote = encodeURIComponent(`Payment ${docRef}${customerName ? ' - ' + customerName : ''}`)
  const encodedPayee = encodeURIComponent(payeeName)
  const upiUri = `upi://pay?pa=${vpa}&pn=${encodedPayee}&am=${cleanAmount}&cu=INR&tn=${transactionNote}&tr=${encodeURIComponent(docRef)}`

  const copyUpi = () => {
    navigator.clipboard.writeText(upiUri).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    })
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="bg-[var(--surface)] border border-[var(--line)] rounded-xl max-w-sm w-full shadow-2xl overflow-hidden animate-in fade-in zoom-in-95 duration-150">
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

        {/* QR Display Body */}
        <div className="p-6 text-center flex flex-col items-center">
          <div className="text-xs font-semibold text-[var(--muted)] uppercase tracking-wider mb-1">
            {payeeName}
          </div>
          <div className="text-2xl font-extrabold text-[var(--text)] tabular-nums mb-3">
            {inr(Number(cleanAmount))}
          </div>

          {/* QR Code Container */}
          <div className="p-4 bg-white rounded-2xl shadow-inner border border-neutral-200 inline-block mb-3">
            <QRCodeSVG
              value={upiUri}
              size={190}
              level="M"
              includeMargin={false}
            />
          </div>

          <div className="flex items-center gap-1.5 text-xs text-[var(--green,#2E7D32)] font-medium mb-2">
            <ShieldCheck className="w-4 h-4" />
            <span>Exact amount pre-filled · Zero typo risk</span>
          </div>

          <div className="text-xs text-[var(--muted)] max-w-[260px] leading-relaxed">
            Customer scans with <span className="font-semibold text-[var(--text)]">PhonePe</span>, <span className="font-semibold text-[var(--text)]">GPay</span>, <span className="font-semibold text-[var(--text)]">Paytm</span>, or BHIM.
          </div>

          {docRef && (
            <div className="mt-3 text-[11px] font-mono bg-[var(--bg)] px-2.5 py-1 rounded border border-[var(--line)] text-[var(--muted)]">
              Ref: {docRef}
            </div>
          )}
        </div>

        {/* Modal Footer */}
        <div className="px-5 py-3.5 border-t border-[var(--line)] bg-[var(--surface-muted)] flex items-center justify-between gap-2">
          <button
            type="button"
            onClick={copyUpi}
            style={{ ...ghostBtn, fontSize: '0.76rem', minHeight: 34, padding: '4px 10px' }}
            className="flex items-center gap-1.5"
            title="Copy UPI payment URI link"
          >
            {copied ? <Check className="w-3.5 h-3.5 text-green-600" /> : <Copy className="w-3.5 h-3.5" />}
            <span>{copied ? 'Copied!' : 'Copy Link'}</span>
          </button>
          <button
            type="button"
            onClick={onClose}
            style={{ ...primaryBtn(false), fontSize: '0.78rem', minHeight: 34, padding: '4px 16px' }}
          >
            Done
          </button>
        </div>
      </div>
    </div>
  )
}
