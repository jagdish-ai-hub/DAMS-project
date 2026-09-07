import { useEffect, useState } from 'react'
import type { ReceiveDocument } from '../api/receipts'
import { inr, fmtDate, fmtDateTime, ghostBtn, primaryBtn } from '../shell/ui'
import { Printer, X } from 'lucide-react'

interface Props {
  doc: ReceiveDocument
  onClose: () => void
}

export default function PrintReceiptModal({ doc, onClose }: Props) {
  const [printFormat, setPrintFormat] = useState<'thermal' | 'a4'>('thermal')

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  const handlePrint = () => {
    window.print()
  }

  const docNo = doc.documentNo || `Draft #${doc.id}`
  const branchTitle = doc.branchName ? `${doc.branchName} (${doc.branchCode || ''})` : (doc.branchCode || 'Dealership Branch')

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 overflow-y-auto">
      <style>{`
        @media print {
          body * {
            visibility: hidden;
          }
          #dams-printable-receipt, #dams-printable-receipt * {
            visibility: visible;
          }
          #dams-printable-receipt {
            position: absolute;
            left: 0;
            top: 0;
            width: ${printFormat === 'thermal' ? '80mm' : '100%'};
            margin: 0;
            padding: ${printFormat === 'thermal' ? '8px' : '24px'};
            background: white !important;
            color: black !important;
            font-size: ${printFormat === 'thermal' ? '12px' : '14px'};
          }
          .no-print {
            display: none !important;
          }
        }
      `}</style>

      <div className="bg-[var(--surface)] border border-[var(--line)] rounded-xl max-w-2xl w-full shadow-2xl overflow-hidden my-auto max-h-[90vh] flex flex-col no-print">
        {/* Modal Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-[var(--line)] bg-[var(--surface-muted)]">
          <div className="flex items-center gap-2">
            <Printer className="w-5 h-5 text-[var(--purple,#6B3FA0)]" />
            <h3 className="text-base font-bold text-[var(--text)]">Print Payment Receipt</h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded-md text-[var(--muted)] hover:text-[var(--text)] hover:bg-[var(--line)] transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Format Selector Bar */}
        <div className="px-5 py-3 border-b border-[var(--line)] flex items-center justify-between bg-[var(--bg)] flex-wrap gap-2">
          <div className="flex items-center gap-2">
            <span className="text-xs font-semibold text-[var(--muted)]">Format:</span>
            <div className="inline-flex rounded-lg border border-[var(--line)] p-0.5 bg-[var(--surface)]">
              <button
                type="button"
                onClick={() => setPrintFormat('thermal')}
                className={`px-3 py-1 text-xs font-medium rounded-md transition-colors ${
                  printFormat === 'thermal'
                    ? 'bg-[var(--purple,#6B3FA0)] text-white'
                    : 'text-[var(--muted)] hover:text-[var(--text)]'
                }`}
              >
                80mm POS Slip
              </button>
              <button
                type="button"
                onClick={() => setPrintFormat('a4')}
                className={`px-3 py-1 text-xs font-medium rounded-md transition-colors ${
                  printFormat === 'a4'
                    ? 'bg-[var(--purple,#6B3FA0)] text-white'
                    : 'text-[var(--muted)] hover:text-[var(--text)]'
                }`}
              >
                A4 Voucher
              </button>
            </div>
          </div>
          <div className="text-xs text-[var(--muted)]">
            Printer: <span className="font-medium text-[var(--text)]">{printFormat === 'thermal' ? 'Standard 80mm Roll' : 'Standard Sheet'}</span>
          </div>
        </div>

        {/* Receipt Preview Area */}
        <div className="p-6 overflow-y-auto flex-1 bg-neutral-100 dark:bg-neutral-900 flex justify-center">
          <div
            id="dams-printable-receipt"
            className={`bg-white text-black font-sans shadow-md border border-neutral-300 p-6 ${
              printFormat === 'thermal' ? 'w-[320px] text-xs' : 'w-full max-w-xl text-sm'
            }`}
          >
            {/* Dealership Header */}
            <div className="text-center pb-3 border-b border-dashed border-neutral-400">
              <div className="font-extrabold text-base tracking-wider uppercase">DAMS WORKSHOP SERVICE</div>
              <div className="text-xs font-medium text-neutral-600">{branchTitle}</div>
              <div className="text-[10px] text-neutral-500 uppercase mt-0.5">Money Receipt / Payment Voucher</div>
            </div>

            {/* Receipt Meta */}
            <div className="py-2.5 border-b border-dashed border-neutral-400 space-y-1 text-[11px]">
              <div className="flex justify-between">
                <span className="font-semibold text-neutral-600">Receipt No:</span>
                <span className="font-bold font-mono text-neutral-900">{docNo}</span>
              </div>
              <div className="flex justify-between">
                <span className="font-semibold text-neutral-600">Date &amp; Time:</span>
                <span className="font-medium">{doc.submittedAt ? fmtDateTime(doc.submittedAt) : fmtDate(doc.createdAt)}</span>
              </div>
              <div className="flex justify-between">
                <span className="font-semibold text-neutral-600">Job Card:</span>
                <span className="font-bold font-mono">{doc.jobCardReference} {doc.dbmId ? `(DBM: ${doc.dbmId})` : ''}</span>
              </div>
              {doc.categoryName && (
                <div className="flex justify-between">
                  <span className="font-semibold text-neutral-600">Category:</span>
                  <span className="font-medium">{doc.categoryName}</span>
                </div>
              )}
            </div>

            {/* Customer & Vehicle Info */}
            <div className="py-2.5 border-b border-dashed border-neutral-400 space-y-1 text-[11px]">
              <div className="flex justify-between">
                <span className="font-semibold text-neutral-600">Customer:</span>
                <span className="font-bold">{doc.customerName || 'Walk-in Customer'}</span>
              </div>
              {doc.customerPhone && (
                <div className="flex justify-between">
                  <span className="font-semibold text-neutral-600">Phone:</span>
                  <span>{doc.customerPhone}</span>
                </div>
              )}
              {doc.vehicleNo && (
                <div className="flex justify-between">
                  <span className="font-semibold text-neutral-600">Vehicle No:</span>
                  <span className="font-bold font-mono text-neutral-900">{doc.vehicleNo}</span>
                </div>
              )}
              {doc.invoiceNo && (
                <div className="flex justify-between">
                  <span className="font-semibold text-neutral-600">Invoice No:</span>
                  <span className="font-mono">{doc.invoiceNo}</span>
                </div>
              )}
            </div>

            {/* Itemized Payments Table */}
            <div className="py-3 border-b border-dashed border-neutral-400">
              <div className="font-bold text-[11px] mb-1.5 uppercase text-neutral-700">Settlement Lines</div>
              <table className="w-full text-left text-[11px]">
                <thead>
                  <tr className="border-b border-neutral-300 text-neutral-600">
                    <th className="py-1">Mode</th>
                    <th className="py-1">Ref / Bank</th>
                    <th className="py-1 text-right">Amount</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-neutral-200">
                  {doc.lines && doc.lines.length > 0 ? (
                    doc.lines.map((line) => (
                      <tr key={line.id}>
                        <td className="py-1 font-medium">{line.settlementModeName || 'Payment'}</td>
                        <td className="py-1 text-neutral-600 font-mono text-[10px]">
                          {line.transactionRef || line.bankName || '—'}
                        </td>
                        <td className="py-1 text-right font-bold tabular-nums">{inr(line.amount)}</td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan={3} className="py-2 text-center text-neutral-500">No settlement lines</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            {/* Totals & Balance Summary */}
            <div className="py-2.5 border-b border-neutral-800 space-y-1 text-[11px]">
              {doc.invoiceAmount !== null && doc.invoiceAmount !== undefined && (
                <div className="flex justify-between text-neutral-700">
                  <span>Invoice Total:</span>
                  <span className="font-semibold tabular-nums">{inr(doc.invoiceAmount)}</span>
                </div>
              )}
              <div className="flex justify-between text-sm font-extrabold text-neutral-900 pt-1 border-t border-neutral-300">
                <span>Total Received:</span>
                <span className="tabular-nums">{inr(doc.totalReceived)}</span>
              </div>
              <div className="flex justify-between text-neutral-700 pt-0.5">
                <span>Remaining Pending:</span>
                <span className={`font-bold tabular-nums ${doc.pendingAmount > 0 ? 'text-amber-800' : 'text-green-800'}`}>
                  {inr(doc.pendingAmount)}
                </span>
              </div>
            </div>

            {/* Signatures & Footer */}
            <div className="pt-4 space-y-4 text-[10px] text-neutral-600">
              <div className="flex justify-between pt-4">
                <div className="text-center w-28">
                  <div className="border-b border-neutral-400 pb-4"></div>
                  <div className="mt-1">Customer Sign</div>
                </div>
                <div className="text-center w-28">
                  <div className="border-b border-neutral-400 pb-4"></div>
                  <div className="mt-1 font-medium text-neutral-900">{doc.createdByName ? `By: ${doc.createdByName}` : 'Authorized Sign'}</div>
                </div>
              </div>
              <div className="text-center text-[9px] text-neutral-500 pt-2">
                Thank you for choosing our service!
                <br />
                Computer Generated Invoice Voucher · DAMS
              </div>
            </div>
          </div>
        </div>

        {/* Modal Action Footer */}
        <div className="px-5 py-4 border-t border-[var(--line)] bg-[var(--surface)] flex justify-end gap-3 flex-wrap">
          <button
            type="button"
            onClick={onClose}
            style={{ ...ghostBtn, minHeight: 38 }}
          >
            Close
          </button>
          <button
            type="button"
            onClick={handlePrint}
            style={{ ...primaryBtn(false), minHeight: 38 }}
            className="flex items-center gap-2"
          >
            <Printer className="w-4 h-4" />
            <span>Print {printFormat === 'thermal' ? 'Slip (80mm)' : 'Voucher (A4)'}</span>
          </button>
        </div>
      </div>
    </div>
  )
}
