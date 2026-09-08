import { useEffect, useRef, useState, type ChangeEvent } from 'react'
import { UploadCloud } from 'lucide-react'
import type { Attachment } from '../api/receipts'
import { ErrorBanner, ghostBtn, primaryBtn, Spinner } from '../shell/ui'
import AttachmentLightbox from '../shared/AttachmentLightbox'

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
  const cameraRef = useRef<HTMLInputElement>(null)

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

  const [isDragging, setIsDragging] = useState(false)
  const [lightbox, setLightbox] = useState<{ url: string; filename: string; contentType?: string } | null>(null)
  // FEAT-37: exact-duplicate warning from the last upload batch (same bytes
  // elsewhere in the org). A warning, never a block.
  const [dupNotice, setDupNotice] = useState<string>('')

  /** Collect duplicate warnings from an upload response. */
  function collectDup(res: { data: Attachment }) {
    const dups = res.data.duplicateOf ?? []
    if (dups.length > 0) {
      setDupNotice(
        `Same photo already attached to ${dups.map((d) => `${d.parentType} #${d.parentId} (${d.filename})`).join(', ')} — attached anyway, flagged for the reviewer.`,
      )
    }
  }

  function addFiles(files: File[]) {
    if (files.length === 0) return
    setError('')
    setDupNotice('')
    // Compress asynchronously so phone photos shrink before staging; stage
    // immediately with originals only if compression fails (it resolves back).
    void Promise.all(files.map((f) => compressImage(f))).then((out) => {
      setStaged((prev) => [
        ...prev,
        ...out.map((file) => ({
          id: `${file.name}-${file.size}-${Date.now()}-${Math.random()}`,
          file,
          target: 'doc' as 'doc' | number,
          tooBig: file.size > MAX_MB * 1024 * 1024,
        })),
      ])
    })
  }

  function onPick(e: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? [])
    e.target.value = ''
    addFiles(files)
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
        if (s.target === 'doc') await collectDup(await api.attachToDocument(id, s.file))
        else await collectDup(await api.attachToLine(id, s.target, s.file))
      }
      setStaged((prev) => prev.filter((s) => s.tooBig))
      setLoaded(await fetchAll(id))
    } catch (e) {
      setError(errMsg(e))
    } finally {
      setBusy(false)
    }
  }

  async function view(att: Loaded) {
    try {
      const { data } = await api.signedUrl(att.id)
      setLightbox({ url: data.url, filename: att.filename, contentType: att.contentType })
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
      {dupNotice && (
        <div style={{
          background: 'var(--amber-bg)', border: '1px solid #EAD3AE', color: 'var(--amber)',
          borderRadius: 8, padding: '9px 12px', fontSize: '0.78rem', marginBottom: 10,
        }}>
          <strong>Possible duplicate bill:</strong> {dupNotice}
        </div>
      )}

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
          <input
            ref={cameraRef}
            type="file"
            accept="image/*"
            capture="environment"
            onChange={onPick}
            style={{ display: 'none' }}
          />

          <div
            onDragOver={(e) => { e.preventDefault(); setIsDragging(true) }}
            onDragLeave={() => setIsDragging(false)}
            onDrop={(e) => {
              e.preventDefault()
              setIsDragging(false)
              if (e.dataTransfer.files?.length) {
                addFiles(Array.from(e.dataTransfer.files))
              }
            }}
            onClick={() => fileRef.current?.click()}
            style={{
              border: isDragging ? '2px dashed var(--navy)' : '1.5px dashed var(--line)',
              background: isDragging ? 'var(--navy3)' : 'var(--bg)',
              borderRadius: 8,
              padding: '16px 14px',
              textAlign: 'center',
              cursor: busy ? 'wait' : 'pointer',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 4,
              transition: 'all 0.15s ease',
            }}
          >
            <UploadCloud size={22} style={{ color: isDragging ? 'var(--navy)' : 'var(--muted)' }} />
            <div style={{ fontSize: '0.82rem', fontWeight: 600, color: 'var(--navy)' }}>
              Drag &amp; drop receipts here, or <span style={{ textDecoration: 'underline' }}>browse</span>
            </div>
            <div style={{ fontSize: '0.7rem', color: 'var(--faint)' }}>
              PDF or images up to {MAX_MB} MB each
            </div>
          </div>
          <button
            type="button"
            onClick={() => cameraRef.current?.click()}
            style={{ ...ghostBtn, minHeight: 38, marginTop: 8 }}
          >
            📷 Take photo
          </button>

          {staged.length > 0 && (
            <div style={{
              marginTop: 12, border: '1px solid var(--line)', borderRadius: 8, padding: 12,
              display: 'flex', flexDirection: 'column', gap: 8,
            }}>
              {staged.map((s) => (
                <div key={s.id} style={{
                  display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap',
                  padding: '6px 0', borderBottom: '1px dashed var(--line)',
                }}>
                  <span style={{ fontSize: '0.82rem', fontWeight: 600, flex: '1 1 140px', minWidth: 0, wordBreak: 'break-all' }}>
                    {s.file.name}
                  </span>
                  {s.tooBig ? (
                    <span style={{ fontSize: '0.76rem', color: 'var(--red)', fontWeight: 700 }}>
                      over {MAX_MB} MB — remove it
                    </span>
                  ) : (
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                      <span style={{ fontSize: '0.74rem', color: 'var(--faint)' }}>attach to</span>
                      <select
                        value={s.target === 'doc' ? 'doc' : String(s.target)}
                        onChange={(e) => setTarget(s.id, e.target.value === 'doc' ? 'doc' : Number(e.target.value))}
                        style={{
                          border: '1.5px solid var(--line)', borderRadius: 7, padding: '5px 8px', fontSize: '0.78rem',
                          minHeight: 36, maxWidth: '100%',
                        }}
                      >
                        <option value="doc">Whole {noun} (top level)</option>
                        {lineTargets.map((lt) => (
                          <option key={lt.lineNo} value={lt.lineNo}>{lt.label}</option>
                        ))}
                      </select>
                    </div>
                  )}
                  <button
                    type="button"
                    onClick={() => removeStaged(s.id)}
                    style={{
                      border: 'none', background: 'none', color: 'var(--red)', fontWeight: 700, cursor: 'pointer',
                      width: 36, height: 36, display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                      fontSize: '1.2rem', padding: 0, marginLeft: 'auto',
                    }}
                    aria-label={`Remove staged file ${s.file.name}`}
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
                  style={{ ...primaryBtn(busy || staged.every((s) => s.tooBig)), minHeight: 36 }}
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
            <span style={{ fontSize: '0.82rem', fontWeight: 600, wordBreak: 'break-all', flex: '1 1 140px', minWidth: 0 }}>
              {a.filename}
            </span>
            <span style={{
              fontSize: '0.68rem', fontWeight: 700, color: 'var(--navy)', background: 'var(--gray-bg)',
              borderRadius: 999, padding: '2px 8px', whiteSpace: 'nowrap',
            }}>
              {a.where}
            </span>
            <span style={{ fontSize: '0.72rem', color: 'var(--faint)', whiteSpace: 'nowrap' }}>{fmtSize(a.sizeBytes)}</span>
            <span style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
              <button type="button" onClick={() => view(a)} style={{ ...ghostBtn, minHeight: 36 }} aria-label={`View attachment ${a.filename}`}>View</button>
              {!frozen && !a.frozen && (
                <button
                  type="button"
                  onClick={() => remove(a.id)}
                  disabled={busy}
                  style={{ ...ghostBtn, color: 'var(--red)', borderColor: '#EBC2C2', minHeight: 36 }}
                  aria-label={`Remove attachment ${a.filename}`}
                >
                  Remove
                </button>
              )}
            </span>
          </div>
        ))}
      </div>

      {lightbox && (
        <AttachmentLightbox
          url={lightbox.url}
          filename={lightbox.filename}
          contentType={lightbox.contentType}
          onClose={() => setLightbox(null)}
        />
      )}
    </section>
  )
}

function fmtSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

// Counter phones shoot multi-MB photos that stall on slow uploads — shrink
// images to max 1600px JPEG 0.8 client-side. PDFs pass through untouched.
function compressImage(file: File): Promise<File> {
  if (!file.type.startsWith('image/')) return Promise.resolve(file)
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      try {
        const max = 1600
        const scale = Math.min(1, max / Math.max(img.naturalWidth, img.naturalHeight))
        const w = Math.max(1, Math.round(img.naturalWidth * scale))
        const h = Math.max(1, Math.round(img.naturalHeight * scale))
        if (scale >= 1) {
          URL.revokeObjectURL(url)
          resolve(file)
          return
        }
        const canvasEl = document.createElement('canvas')
        canvasEl.width = w
        canvasEl.height = h
        const ctx = canvasEl.getContext('2d')
        if (!ctx) {
          URL.revokeObjectURL(url)
          resolve(file)
          return
        }
        ctx.drawImage(img, 0, 0, w, h)
        canvasEl.toBlob(
          (blob) => {
            URL.revokeObjectURL(url)
            if (!blob) {
              resolve(file)
              return
            }
            const name = file.name.replace(/\.\w+$/, '') + '.jpg'
            resolve(new File([blob], name, { type: 'image/jpeg' }))
          },
          'image/jpeg',
          0.8,
        )
      } catch {
        URL.revokeObjectURL(url)
        resolve(file)
      }
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      resolve(file)
    }
    img.src = url
  })
}

function errMsg(e: unknown): string {
  return (
    (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ?? 'Something went wrong with that document.'
  )
}
