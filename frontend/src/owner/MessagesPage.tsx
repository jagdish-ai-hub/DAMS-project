import { useEffect, useState } from 'react'
import { messagesApi, type MessageLogEntry, type MessageTemplate } from '../api/messaging'
import { card, ErrorBanner, Badge, primaryBtn, inputStyle, th, td, fmtDateTime } from '../shell/ui'
import HelpButton from '../help/HelpButton'

/**
 * Messaging (FEAT-36): org templates ({{variable}} copy, no free-typed
 * blasts), manual send, and the full auditable send log. Until a real
 * WhatsApp/SMS provider is configured, sends are logged + recorded
 * (status LOGGED) — visibly, never silently dropped.
 */
export default function MessagesPage() {
  const [templates, setTemplates] = useState<MessageTemplate[] | null>(null)
  const [log, setLog] = useState<MessageLogEntry[] | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const [code, setCode] = useState('due_reminder')
  const [toPhone, setToPhone] = useState('')
  const [varsText, setVarsText] = useState('{"name":"","amount":"","docNo":"","dueDate":"","branch":""}')

  async function load() {
    setError('')
    try {
      const [t, l] = await Promise.all([messagesApi.templates(), messagesApi.log()])
      setTemplates(t.data)
      setLog(l.data)
      if (!t.data.some((x) => x.code === code) && t.data.length > 0) setCode(t.data[0].code)
    } catch (e) {
      setError(apiError(e, 'Could not load messaging.'))
    }
  }

  useEffect(() => { load() }, []) // eslint-disable-line react-hooks/exhaustive-deps

  async function send() {
    setBusy(true)
    setError('')
    try {
      const variables = varsText.trim() ? (JSON.parse(varsText) as Record<string, string>) : {}
      await messagesApi.send({ templateCode: code, toPhone, variables })
      setToPhone('')
      await load()
    } catch (e) {
      setError(apiError(e, 'Could not send. Check the phone and the variable JSON.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div style={{ maxWidth: 1000, margin: '0 auto' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
        <h1 style={{ fontSize: '1.3rem', color: 'var(--navy)', margin: 0 }}>Messages</h1>
        <HelpButton slug="sending-messages" />
      </div>
      <p style={{ fontSize: '0.82rem', color: 'var(--muted)', marginTop: 0 }}>
        Templated WhatsApp/SMS with a full send log. Missing variables render empty — never crash.
      </p>
      <ErrorBanner message={error} />

      <div style={{ ...card, marginBottom: 14, padding: 14 }}>
        <div style={{ fontWeight: 700, marginBottom: 8 }}>Send now</div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 8 }}>
          <select value={code} onChange={(e) => setCode(e.target.value)} style={inputStyle}>
            {(templates ?? []).map((t) => (
              <option key={t.code} value={t.code}>{t.code} ({t.channel})</option>
            ))}
          </select>
          <input value={toPhone} onChange={(e) => setToPhone(e.target.value)} placeholder="Recipient phone" style={{ ...inputStyle, width: 170 }} />
          <button type="button" onClick={send} disabled={busy || !toPhone} style={primaryBtn(busy)}>Send</button>
        </div>
        <input
          value={varsText}
          onChange={(e) => setVarsText(e.target.value)}
          placeholder='Variables as JSON, e.g. {"name":"Sharma"}'
          spellCheck={false}
          style={{ ...inputStyle, width: '100%', fontFamily: 'Consolas, monospace', fontSize: '0.78rem' }}
        />
        {(templates ?? []).filter((t) => t.code === code).map((t) => (
          <div key={t.id} style={{ fontSize: '0.78rem', color: 'var(--muted)', marginTop: 6 }}>
            Template: {t.body} {!t.active && <Badge tone="gray">deactivated</Badge>}
          </div>
        ))}
      </div>

      <div style={card}>
        <div style={{ fontWeight: 700, padding: '10px 10px 0' }}>Send log (newest first)</div>
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead><tr><th style={th}>When</th><th style={th}>To</th><th style={th}>Template</th><th style={th}>Status</th><th style={th}>Body</th></tr></thead>
          <tbody>
            {(log ?? []).map((m) => (
              <tr key={m.id}>
                <td style={{ ...td, whiteSpace: 'nowrap', fontSize: '0.76rem' }}>{fmtDateTime(m.createdAt)}</td>
                <td style={td}>{m.toPhone}</td>
                <td style={{ ...td, fontSize: '0.78rem' }}>{m.templateCode ?? '—'}</td>
                <td style={td}>
                  <Badge tone={m.status === 'FAILED' ? 'red' : m.status === 'SENT' ? 'green' : 'amber'}>{m.status}</Badge>
                  {m.error && <div style={{ fontSize: '0.72rem', color: 'var(--faint)' }}>{m.error}</div>}
                </td>
                <td style={{ ...td, fontSize: '0.78rem', maxWidth: 380 }}>{m.body}</td>
              </tr>
            ))}
            {log?.length === 0 && <tr><td colSpan={5} style={{ ...td, color: 'var(--faint)' }}>Nothing sent yet.</td></tr>}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}
