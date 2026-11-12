import { useCallback, useEffect, useMemo, useState } from 'react'
import { apiMessage, sparePartsApi, type PermissionGrid } from '../api/spareParts'
import { ErrorBanner, Skeleton, Spinner, card, cardTitle, ghostBtn, primaryBtn, td, th } from '../shell/ui'
import { useSpareAccess } from './SpareAccessContext'
import { FlashBanner, Hint, useFlash } from './spareUi'

/**
 * Masters → Spare Parts: what an Inventory Manager and a Store Keeper may do. The Owner ticks or unticks; the server
 * checks the grid on every action, so unticking really blocks the action, it does not just hide a button. Shown only
 * to the Owner, and only while the module is switched on for the organization.
 */
export default function SparePartsPermissionsSection() {
  const { access, loading } = useSpareAccess()
  const [grid, setGrid] = useState<PermissionGrid | null>(null)
  const [draft, setDraft] = useState<Record<string, Record<string, boolean>>>({})
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const flash = useFlash()

  const active = access.enabled && access.owner

  const load = useCallback(async () => {
    try {
      const { data } = await sparePartsApi.permissions()
      setGrid(data)
      setDraft(Object.fromEntries(data.roles.map((r) => [r.role, { ...r.values }])))
      setError('')
    } catch (e) {
      setError(apiMessage(e, 'Could not load the permissions.'))
    }
  }, [])

  useEffect(() => { if (active) load() }, [active, load])

  const changed = useMemo(() => {
    const out: Record<string, Record<string, boolean>> = {}
    for (const r of grid?.roles ?? []) {
      for (const [perm, value] of Object.entries(draft[r.role] ?? {})) {
        if (r.values[perm] !== value) (out[r.role] ??= {})[perm] = value
      }
    }
    return out
  }, [grid, draft])
  const dirty = Object.keys(changed).length > 0

  async function save() {
    setSaving(true)
    setError('')
    try {
      const { data } = await sparePartsApi.updatePermissions(changed)
      setGrid(data)
      setDraft(Object.fromEntries(data.roles.map((r) => [r.role, { ...r.values }])))
      flash.show('Saved. It applies from the next click of everyone concerned.')
    } catch (e) {
      setError(apiMessage(e, 'Could not save the permissions.'))
    } finally {
      setSaving(false)
    }
  }

  if (loading || !active) return null

  return (
    <section style={card} aria-labelledby="spare-permissions-title">
      <h2 id="spare-permissions-title" style={cardTitle}>Spare Parts: what each role may do</h2>
      <p style={{ fontSize: '0.8rem', color: 'var(--muted)', margin: '-6px 0 12px', lineHeight: 1.5 }}>
        Tick what each role is allowed to do at <strong>its own branches</strong>. Both roles can always search every branch’s stock.
      </p>
      <FlashBanner message={flash.message} />
      <ErrorBanner message={error} />

      {!grid ? (
        <Skeleton height={160} radius={8} />
      ) : (
        <>
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.84rem', minWidth: 420 }}>
              <thead>
                <tr>
                  <th style={th}>Can…</th>
                  {grid.roles.map((r) => <th key={r.role} style={{ ...th, textAlign: 'center', width: 130 }}>{r.label}</th>)}
                </tr>
              </thead>
              <tbody>
                {grid.permissions.map((p) => (
                  <tr key={p.key}>
                    <td style={td}>{p.label}</td>
                    {grid.roles.map((r) => (
                      <td key={r.role} style={{ ...td, textAlign: 'center' }}>
                        <input
                          type="checkbox"
                          aria-label={`${r.label}: ${p.label}`}
                          checked={draft[r.role]?.[p.key] ?? false}
                          onChange={(e) => setDraft((d) => ({ ...d, [r.role]: { ...d[r.role], [p.key]: e.target.checked } }))}
                          style={{ width: 18, height: 18, cursor: 'pointer' }}
                        />
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 14, flexWrap: 'wrap' }}>
            <Hint>The Owner is view-only here and acts through Switch role. Assign the two roles in Team &amp; Branches.</Hint>
            <span style={{ flex: 1 }} />
            <button type="button" style={{ ...ghostBtn, minHeight: 38 }} disabled={!dirty || saving} onClick={() => setDraft(Object.fromEntries(grid.roles.map((r) => [r.role, { ...r.values }])))}>Undo</button>
            <button type="button" onClick={save} disabled={!dirty || saving}
              style={{ ...primaryBtn(!dirty || saving), minHeight: 38, display: 'inline-flex', alignItems: 'center', gap: 8 }}>
              {saving && <Spinner />} Save changes
            </button>
          </div>
        </>
      )}
    </section>
  )
}
