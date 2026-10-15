import { useEffect, useId, useState } from 'react'

/**
 * Draws one Mermaid diagram inside a Help article (rev 80). The `mermaid` library is large, so it is
 * loaded on demand — only when a help page that contains a diagram is opened — and never at app start.
 * Diagrams are authored in the repo (never typed by users) and rendered in Mermaid's strict mode.
 * If drawing fails, the diagram's text is shown instead so the page is never blank.
 */

type MermaidApi = typeof import('mermaid').default
let loaded: Promise<MermaidApi> | null = null

function loadMermaid(): Promise<MermaidApi> {
  loaded ??= import('mermaid').then((m) => {
    m.default.initialize({
      startOnLoad: false,
      securityLevel: 'strict',
      theme: 'base',
      themeVariables: {
        fontFamily: 'inherit',
        fontSize: '14px',
        primaryColor: '#EAF0FB',
        primaryBorderColor: '#1F3864',
        primaryTextColor: '#1F3864',
        lineColor: '#5B6B86',
        secondaryColor: '#F1F4F9',
        tertiaryColor: '#FFFFFF',
      },
      flowchart: { htmlLabels: false, curve: 'basis' },
    })
    return m.default
  })
  return loaded
}

export default function MermaidDiagram({ chart, label }: { chart: string; label?: string }) {
  const id = `mmd-${useId().replace(/[^a-zA-Z0-9]/g, '')}`
  const [svg, setSvg] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let alive = true
    setSvg(null)
    setFailed(false)
    loadMermaid()
      .then((mermaid) => mermaid.render(id, chart))
      .then((out) => { if (alive) setSvg(out.svg) })
      .catch(() => { if (alive) setFailed(true) })
    return () => { alive = false }
  }, [chart, id])

  if (failed) {
    return <pre className="dams-help-diagram-fallback" aria-label={label}>{chart}</pre>
  }
  return (
    <figure className="dams-help-diagram" role="img" aria-label={label ?? 'Diagram'}>
      {svg == null
        ? <span style={{ color: 'var(--faint)', fontSize: '0.8rem' }}>Drawing the diagram…</span>
        : <div dangerouslySetInnerHTML={{ __html: svg }} />}
    </figure>
  )
}
