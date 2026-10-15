import { render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MermaidDiagram from './MermaidDiagram'
import HelpDrawer from './HelpDrawer'
import { HELP_MANIFEST, articleBody, type HelpRole } from './manifest'

const render_ = vi.fn()
vi.mock('mermaid', () => ({
  default: { initialize: vi.fn(), render: (...a: unknown[]) => render_(...a) },
}))

describe('MermaidDiagram (rev 80)', () => {
  beforeEach(() => {
    render_.mockReset()
  })

  it('draws the diagram once the library has loaded', async () => {
    render_.mockResolvedValue({ svg: '<svg data-testid="drawn"><text>Draft</text></svg>' })
    render(<MermaidDiagram chart={'flowchart TD\n  A-->B'} label="Receipt flow" />)

    expect(await screen.findByTestId('drawn')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Receipt flow' })).toBeInTheDocument()
    expect(render_).toHaveBeenCalledWith(expect.stringMatching(/^mmd-/), 'flowchart TD\n  A-->B')
  })

  it('shows the diagram text instead of a blank page when drawing fails', async () => {
    render_.mockRejectedValue(new Error('Parse error'))
    render(<MermaidDiagram chart={'flowchart TD\n  broken'} />)

    await waitFor(() => expect(screen.getByText(/flowchart TD/)).toBeInTheDocument())
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })
})

describe('Help Center pages that follow a document (rev 80)', () => {
  const pages: [HelpRole, string][] = [
    ['cashier', 'where-is-my-entry'],
    ['accountant', 'where-is-it'],
    ['finance-manager', 'where-is-it'],
    ['owner', 'where-is-it'],
  ]

  it.each(pages)('%s has the page registered and it contains diagrams for receipts, expenses and cash', (role, slug) => {
    expect(HELP_MANIFEST[role].some((e) => e.slug === slug)).toBe(true)
    const body = articleBody(role, slug)
    expect(body.match(/```mermaid/g)?.length ?? 0).toBeGreaterThanOrEqual(4)
    expect(body).toMatch(/## Receipts/)
    expect(body).toMatch(/## Expenses/)
    expect(body).toMatch(/## Cash in and out/)
    expect(body).toMatch(/What every status means/)
  })

  it('every role highlights its own step, and the owner highlights none', () => {
    expect(articleBody('cashier', 'where-is-my-entry')).toMatch(/class A,Q here/)
    expect(articleBody('accountant', 'where-is-it')).toMatch(/class B,F here/)
    expect(articleBody('finance-manager', 'where-is-it')).toMatch(/class C here/)
    expect(articleBody('owner', 'where-is-it')).not.toMatch(/ here\n/)
  })

  it('opens in the Help drawer and draws every diagram on the page', async () => {
    render_.mockResolvedValue({ svg: '<svg data-testid="drawn"></svg>' })
    render(<HelpDrawer role="finance-manager" open onClose={() => {}} initialSlug="where-is-it" />)

    expect(await screen.findByText('Where is it — and what’s next?', { selector: 'h1' })).toBeInTheDocument()
    const expected = articleBody('finance-manager', 'where-is-it').match(/```mermaid/g)!.length
    await waitFor(() => expect(screen.getAllByTestId('drawn')).toHaveLength(expected))
  })
})
