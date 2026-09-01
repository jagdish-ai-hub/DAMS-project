import { useEffect, useRef, useState, type ChangeEvent } from 'react'
import type { Attachment } from '../api/receipts'
import { ErrorBanner, ghostBtn, primaryBtn, Spinner } from '../shell/ui'

/** The six attachment calls — identical shape on `receiptsApi` and `expensesApi`. */
export type AttachmentApi = {
  documentAttachments: (id: number) => Promise<{ data: Attachment[] }>
  lineAttachments: (id: number, lineNo: number) => Promise<{ data: Attachment[] }>
  attachToDocument: (id: number, file: File) => Promise<{ data: Attachment }>
  attachToLine: (id: number, lineNo: number, file: File) => Promise<{ data: Attachment }>
  signedUrl: (attachmentId: number) => Promise<{ data: { url: string } }>
  deleteAttachment: (attachmentId: number) => Promise<unknown>
}

/** A settlement / expense line a file can be tagged to. `lineNo` is the server line number. */
export type LineTarget = { lineNo: number; label: string }

const MAX_MB = 10

type Loaded = Attachment & { where: string }
type Staged = { id: string; file: File; target: 'doc' | number; tooBig: boolean }

/**
 * "Documents" panel on the New Receipt / New Expense forms. Always visible. Lets the user
 * pick several PDFs / images at once and tag each one to the whole document ("top level")
 * or to one line ("sub-transaction"). If the form has not been saved yet, the first upload
 * saves it as a draft ({@code ensureDraft}) so the server has an id to attach to.
 *
 * Backend rules mirrored here: {@value MAX_MB} MB per file, PDF or image only, and no
 * changes once the document is frozen (settled / approved / closed / rejected).
 */
export default function AttachmentsPanel(props: {
  docId: number | null
  noun: 'receipt' | 'expense'
  frozen: boolean
  lineTargets: LineTarget[]
  api: AttachmentApi
  /** Save the form as a draft, return the new id (or null if it could not be saved). */
  ensureDraft: () => Promise<number | null>
}) {
  const { docId, api, lineTargets, noun, frozen } = props
  const [loaded, setLoaded] = useState<Loaded[] | null>(docId == null ? [] : null)
  const [staged, setStaged] = useState<Staged[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const fileRef = useRef<HTMLInputElement>(null)

  const lineKey = lineTargets.map((t) => t.lineNo).join(',')

  async function fetchAll(id: number): Promise<Loaded[]> {
    const out: Loaded[] = []
    const doc = await api.documentAttachments(id)
    doc.data.forEach((a) => out.push({ ...a, where: `Whole ${noun}` }))
    for (const lt of lineTargets) {
      const r = await api.lineAttachments(id, lt.lineNo)
      r.data.forEach((a) => out.push({ ...a, where: lt.label }))
    }
    return out
  }

  useEffect(() => {
    let live = true
    if (docId == null) {
      setLoaded([])
      return
    }
    fetchAll(docId)
      .then((out) => live && setLoaded(out))
      .catch(() => live && setError('Could not load documents.'))
    return () => {
      live = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [docId, lineKey])

  function onPick(e: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? [])
    e.target.value = ''
    if (files.length === 0) return
    setError('')
    setStaged((prev) => [
      ...prev,
      ...files.map((file) => ({
        id: `${file.name}-${file.size}-${Date.now()}-${Math.random()}`,
        file,
        target: 'doc' as 'doc' | number,
        tooBig: file.size > MAX_MB * 1024 * 1024,
      })),
    ])
  }

  function setTarget(id: string, target: 'doc' | number) {
    setStaged((prev) => prev.map((s) => (s.id === id ? { ...s, target } : s)))
  }

  function removeStaged(id: string) {
    setStaged((prev) => prev.filter((s) => s.id !== id))
  }

  async function uploadAll() {
    const ready = staged.filter((s) => !s.tooBig)
    if (ready.length === 0) return
    setBusy(true)
    setError('')
    try {
      let id = docId
      if (id == null) {
        id = await props.ensureDraft()
        if (id == null) {
          setBusy(false)
          return // ensureDraft surfaced its own error (e.g. "customer name is required")
        }
      }
      for (const s of ready) {
        if (s.target === 'doc') await api.attachToDocument(id, s.file)
        else await api.attachToLine(id, s.target, s.file)
      }
      setStaged((prev) => prev.filter((s) => s.tooBig))
      setLoaded(await fetchAll(id))
    } catch (e) {
      setError(errMsg(e))
    } finally {
      setBusy(false)
    }
  }

  async function view(attId: number) {
    try {
      const { data } = await api.signedUrl(attId)
      window.open(data.url, '_blank', 'noopener')
    } catch {
      setError('Could not open that document.')
    }
  }

  async function remove(attId: number) {
    if (docId == null) return
    setBusy(true)
    setError('')
    try {
      await api.deleteAttachment(attId)
      setLoaded(await fetchAll(docId))
    } catch (e) {
      setError(errMsg(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section style={{ marginTop: 10 }}>
      <div style={{ marginBottom: 8 }}>
        <h4 style={{ fontSize: '0.84rem', fontWeight: 700, color: 'var(--navy)' }}>Documents</h4>
        <span style={{ fontSize: '0.74rem', color: 'var(--muted)' }}>
          scanned voucher / bill / proof — PDF or image, up to {MAX_MB} MB each
        </span>
      </div>

      <ErrorBanner message={error} />

      {frozen ? (
        <div style={{ fontSize: '0.8rem', color: 'var(--faint)' }}>
          This {noun} is closed — its documents are frozen and can’t be changed.
        </div>
      ) : (
        <>
          <input
            ref={fileRef}
            type="file"
            accept=".pdf,image/*"
            multiple
            onChange={onPick}
            style={{ display: 'none' }}
          />
          <button type="button" onClick={() => fileRef.current?.click()} style={ghostBtn} disabled={busy}>
            📎 Add documents
          </button>

          {staged.length > 0 && (
            <div style={{
              marginTop: 12, border: '1px solid var(--line)', borderRadius: 8, padding: 12,
              display: 'flex', flexDirection: 'column', gap: 8,
            }}>
              {staged.map((s) => (
                <div key={s.id} style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
                  <span style={{ fontSize: '0.82rem', fontWeight: 600, minWidth: 160, wordBreak: 'break-all' }}>
                    {s.file.name}
                  </span>
                  {s.tooBig ? (
                    <span style={{ fontSize: '0.76rem', color: 'var(--red)', fontWeight: 700 }}>
                      over {MAX_MB} MB — remove it
                    </span>
                  ) : (
                    <>
                      <span style={{ fontSize: '0.74rem', color: 'var(--faint)' }}>attach to</span>
                      <select
                        value={s.target === 'doc' ? 'doc' : String(s.target)}
                        onChange={(e) => setTarget(s.id, e.target.value === 'doc' ? 'doc' : Number(e.target.value))}
                        style={{
                          border: '1.5px solid var(--line)', borderRadius: 7, padding: '5px 8px', fontSize: '0.78rem',
                        }}
                      >
                        <option value="doc">Whole {noun} (top level)</option>
                        {lineTargets.map((lt) => (
                          <option key={lt.lineNo} value={lt.lineNo}>{lt.label}</option>
                        ))}
                      </select>
                    </>
                  )}
                  <button
                    type="button"
                    onClick={() => removeStaged(s.id)}
                    style={{ border: 'none', background: 'none', color: 'var(--red)', fontWeight: 700, cursor: 'pointer' }}
                  >
                    ×
                  </button>
                </div>
              ))}
              <div>
                <button
                  type="button"
                  onClick={uploadAll}
                  disabled={busy || staged.every((s) => s.tooBig)}
                  style={primaryBtn(busy || staged.every((s) => s.tooBig))}
                >
                  {busy ? <><Spinner /> Uploading…</> : `Upload ${staged.filter((s) => !s.tooBig).length || ''}`.trim()}
                </button>
              </div>
            </div>
          )}
        </>
      )}

      <div style={{ marginTop: 12, display: 'flex', flexDirection: 'column', gap: 6 }}>
        {loaded == null && <span style={{ fontSize: '0.8rem', color: 'var(--faint)' }}>Loading documents…</span>}
        {loaded != null && loaded.length === 0 && (
          <span style={{ fontSize: '0.8rem', color: 'var(--faint)' }}>No documents attached yet.</span>
        )}
        {(loaded ?? []).map((a) => (
          <div
            key={a.id}
            style={{
              display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap',
              border: '1px solid var(--line)', borderRadius: 8, padding: '8px 10px',
            }}
          >
            <span style={{ fontSize: '0.82rem', fontWeight: 600, wordBreak: 'break-all' }}>{a.filename}</span>
            <span style={{
              fontSize: '0.68rem', fontWeight: 700, color: 'var(--navy)', background: 'var(--gray-bg)',
              borderRadius: 999, padding: '2px 8px',
            }}>
              {a.where}
            </span>
            <span style={{ fontSize: '0.72rem', color: 'var(--faint)' }}>{fmtSize(a.sizeBytes)}</span>
            <span style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
              <button type="button" onClick={() => view(a.id)} style={ghostBtn}>View</button>
              {!frozen && !a.frozen && (
                <button
                  type="button"
                  onClick={() => remove(a.id)}
                  disabled={busy}
                  style={{ ...ghostBtn, color: 'var(--red)', borderColor: '#EBC2C2' }}
                >
                  Remove
                </button>
              )}
            </span>
          </div>
        ))}
      </div>
    </section>
  )
}

function fmtSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function errMsg(e: unknown): string {
  return (
    (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ?? 'Something went wrong with that document.'
  )
}
