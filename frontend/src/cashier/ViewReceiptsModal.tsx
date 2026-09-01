import { useCallback, useEffect, useState } from 'react'
import { receiptsApi, type Attachment } from '../api/receipts'
import { Modal, ErrorBanner, ghostBtn, dangerBtn, SkeletonRows } from '../shell/ui'

/**
 * "View Receipts" (AGENT.md: a button that opens on click — not inline thumbnails).
 * Lists the PDF/image attachments on a whole document or one settlement line; each opens
 * via a short-lived signed URL. Upload / delete are disabled once the parent is frozen.
 */
export default function ViewReceiptsModal(props: {
  receiptId: number
  lineNo?: number
  subtitle: string
  frozen: boolean
  onClose: () => void
  onChanged?: () => void
}) {
  const { receiptId, lineNo } = props
  const [items, setItems] = useState<Attachment[] | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    const req = lineNo == null
      ? receiptsApi.documentAttachments(receiptId)
      : receiptsApi.lineAttachments(receiptId, lineNo)
    req.then(({ data }) => setItems(data)).catch((e) => setError(apiError(e, 'Could not load receipts.')))
  }, [receiptId, lineNo])

  useEffect(load, [load])

  async function open(id: number) {
    try {
      const { data } = await receiptsApi.signedUrl(id)
      window.open(data.url, '_blank', 'noopener')
    } catch (e) {
      setError(apiError(e, 'Could not open the receipt.'))
    }
  }

  async function upload(file: File) {
    setBusy(true)
    setError('')
    try {
      if (lineNo == null) await receiptsApi.attachToDocument(receiptId, file)
      else await receiptsApi.attachToLine(receiptId, lineNo, file)
      load()
      props.onChanged?.()
    } catch (e) {
      setError(apiError(e, 'Could not attach the file.'))
    } finally {
      setBusy(false)
    }
  }

  async function remove(id: number) {
    setBusy(true)
    setError('')
    try {
      await receiptsApi.deleteAttachment(id)
      load()
      props.onChanged?.()
    } catch (e) {
      setError(apiError(e, 'Could not delete the receipt.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title="Receipts" subtitle={props.subtitle} onClose={props.onClose}>
      <ErrorBanner message={error} />

      {items == null ? (
        <SkeletonRows rows={3} height={44} />
      ) : items.length === 0 ? (
        <p style={{ color: 'var(--faint)', fontSize: '0.85rem' }}>No receipt attached yet.</p>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {items.map((a) => (
            <div key={a.id} style={{
              display: 'flex', alignItems: 'center', gap: 10, padding: '9px 11px',
              border: '1px solid var(--line)', borderRadius: 8, fontSize: '0.84rem',
            }}>
              <span style={{
                width: 30, height: 30, borderRadius: 7, background: 'var(--red-bg)', color: 'var(--red)',
                display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.62rem', fontWeight: 700, flexShrink: 0,
              }}>
                {a.contentType.includes('pdf') ? 'PDF' : 'IMG'}
              </span>
              <span style={{ flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {a.filename}
                <span style={{ color: 'var(--faint)', marginLeft: 6 }}>{Math.round(a.sizeBytes / 1024) || 1} KB</span>
              </span>
              <button type="button" onClick={() => open(a.id)} style={ghostBtn}>View</button>
              {!a.frozen && !props.frozen && (
                <button type="button" onClick={() => remove(a.id)} style={dangerBtn} disabled={busy}>Delete</button>
              )}
            </div>
          ))}
        </div>
      )}

      {props.frozen ? (
        <p style={{ fontSize: '0.76rem', color: 'var(--faint)' }}>
          This document is approved or settled — its receipts are frozen.
        </p>
      ) : (
        <label style={{
          border: '1.5px dashed var(--line)', borderRadius: 8, padding: '9px 12px', fontSize: '0.82rem',
          color: 'var(--navy2)', fontWeight: 600, cursor: busy ? 'wait' : 'pointer', display: 'block',
        }}>
          📎 Attach receipt (PDF or image)
          <input
            type="file"
            accept=".pdf,image/*"
            disabled={busy}
            onChange={(e) => {
              const f = e.target.files?.[0]
              if (f) upload(f)
              e.target.value = ''
            }}
            style={{ display: 'none' }}
          />
        </label>
      )}
    </Modal>
  )
}

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}
