import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import InvoiceExtractPage from './InvoiceExtractPage'
import { buildInvoiceCsv } from './invoiceExtractCsv'
import { invoiceExtractApi, type InvoiceExtract } from '../api/invoiceExtract'

vi.mock('../api/invoiceExtract', () => ({ invoiceExtractApi: { extract: vi.fn() } }))
vi.mock('../help/HelpButton', () => ({ default: () => null }))

const base: InvoiceExtract = {
  fileName: 'inv.pdf',
  documentType: 'TAX_INVOICE',
  documentLabel: 'Tax invoice (IGST)',
  header: [
    { label: 'Invoice No.', value: '7731162600134' },
    { label: 'Job Card No. (Eicher)', value: '4010734608' },
    { label: 'Vehicle Reg No.', value: 'CG00AA0003' },
  ],
  spares: [
    { page: 1, sno: '1', code: 'ID334315', hsnSac: '84212300', gstRate: 18, description: 'MAIN FILTER KIT', qty: 1, uom: 'NOS', unitAmount: 1555.08, discount: 155.51, taxable: 1399.57, igst: 251.92, total: 1651.49 },
    { page: 1, sno: '2', code: 'IE451148', hsnSac: '85124000', gstRate: 18, description: 'WIPER BLADE (550MM)', qty: 2, uom: 'NOS', unitAmount: 470.34, discount: 0, taxable: 940.68, igst: 169.32, total: 1110 },
  ],
  lube: [{ page: 2, sno: '1', code: 'IM300905L', hsnSac: '27101972', description: 'EICHER Life Max Green 209 LTR, with "quotes"', qty: 10, uom: 'NOS', unitAmount: 381.36, discount: 381.36, taxable: 3432.24, igst: 617.8, total: 4050.04 }],
  labour: [],
  freeItems: [{ sno: 1, itemCode: '0116SC1', description: 'FREE SERVICE -1', quantity: 3 }],
  totals: [{ label: 'Invoice amount', value: 16644.11 }],
  hsnSummary: [{ hsn: '84212300', taxable: 1399.57, total: 1651.49 }],
  checks: [{ name: 'spares #1 taxable = amount - discount', ok: true }, { name: 'spares: sum of line totals = printed sub total', ok: true }],
  problems: [],
  checksPassed: 2,
  checksTotal: 2,
}

function renderPage() {
  return render(<MemoryRouter><InvoiceExtractPage /></MemoryRouter>)
}

describe('Extract invoice (test tool, rev 98)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.HTMLElement.prototype.scrollIntoView = vi.fn()      // the error banner scrolls itself into view; jsdom has no such method
    vi.mocked(invoiceExtractApi.extract).mockResolvedValue({ data: base } as never)
  })

  it('reads nothing until a PDF is chosen', () => {
    renderPage()
    expect(screen.getByRole('button', { name: 'Read the PDF' })).toBeDisabled()
    expect(screen.getByText(/nothing is saved/i)).toBeInTheDocument()
  })

  it('uploads the PDF and shows the details, the spares / lube / labour tables and a green verdict', async () => {
    const user = userEvent.setup()
    renderPage()
    const pdf = new File(['%PDF-1.4'], 'inv.pdf', { type: 'application/pdf' })
    await user.upload(screen.getByLabelText('PDF file'), pdf)
    await user.click(screen.getByRole('button', { name: 'Read the PDF' }))

    await waitFor(() => expect(invoiceExtractApi.extract).toHaveBeenCalledWith(pdf))
    expect(await screen.findByRole('status')).toHaveTextContent('All 2 checks passed')
    expect(screen.getByText('Tax invoice (IGST)')).toBeInTheDocument()
    expect(screen.getByText('4010734608')).toBeInTheDocument()          // job card in the details

    const spares = screen.getByRole('region', { name: 'Spares' })
    expect(within(spares).getByText('MAIN FILTER KIT')).toBeInTheDocument()
    expect(within(spares).getByText('1,651.49')).toBeInTheDocument()
    expect(within(spares).getByText('2,761.49')).toBeInTheDocument()     // sum of the totals row: 1,651.49 + 1,110.00
    expect(within(spares).queryByText('Man hrs')).not.toBeInTheDocument() // a tax invoice has no man-hours column
    expect(within(screen.getByRole('region', { name: 'Lube' })).getByText('IM300905L')).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Labour' })).toHaveTextContent('none on this document')
    expect(screen.getByRole('region', { name: 'Replaced under warranty' })).toHaveTextContent('FREE SERVICE -1')
  })

  it('says plainly when the figures do not add up, and lists the failed checks first', async () => {
    const bad: InvoiceExtract = {
      ...base,
      checks: [{ name: 'spares #1 taxable = amount - discount', ok: true }, { name: 'spares: sum of line totals = printed sub total', ok: false, detail: '8248.48 vs 8356.48' }],
      checksPassed: 1,
    }
    vi.mocked(invoiceExtractApi.extract).mockResolvedValue({ data: bad } as never)
    const user = userEvent.setup()
    renderPage()
    await user.upload(screen.getByLabelText('PDF file'), new File(['%PDF-1.4'], 'inv.pdf', { type: 'application/pdf' }))
    await user.click(screen.getByRole('button', { name: 'Read the PDF' }))

    expect(await screen.findByRole('status')).toHaveTextContent('1 of 2 checks failed')
    expect(screen.getByText(/8248\.48 vs 8356\.48/)).toBeInTheDocument()
  })

  it('shows the server message when the file cannot be read', async () => {
    vi.mocked(invoiceExtractApi.extract).mockRejectedValue({ response: { data: { message: 'This is not a PDF file.' } } })
    const user = userEvent.setup()
    renderPage()
    await user.upload(screen.getByLabelText('PDF file'), new File(['hello'], 'a.pdf', { type: 'application/pdf' }))
    await user.click(screen.getByRole('button', { name: 'Read the PDF' }))

    expect(await screen.findByText('This is not a PDF file.')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Spares' })).not.toBeInTheDocument()
  })
})

describe('buildInvoiceCsv', () => {
  it('has one row per line with the document it came from, quotes awkward text, and starts with a BOM', () => {
    const csv = buildInvoiceCsv(base)
    const lines = csv.replace('﻿', '').trim().split('\r\n')

    expect(csv.startsWith('﻿')).toBe(true)
    expect(lines[0]).toContain('File,Document,Invoice / claim no,Job card no,Vehicle,Section,Page,No,Code')
    expect(lines).toHaveLength(1 + 2 + 1 + 1)                                  // header + 2 spares + 1 lube + 1 free item
    expect(lines[1]).toContain('inv.pdf,Tax invoice (IGST),7731162600134,4010734608,CG00AA0003,Spares')
    expect(lines[1]).toContain('ID334315')
    expect(lines[3]).toContain('"EICHER Life Max Green 209 LTR, with ""quotes"""')
    expect(lines[4]).toContain('Free under warranty/AMC/FOC')
    expect(lines[4]).toContain('FREE SERVICE -1')
  })

  it('labels the claim number and vehicle of a warranty claim', () => {
    const claim: InvoiceExtract = {
      ...base, documentType: 'WARRANTY_CLAIM', documentLabel: 'Warranty claim invoice',
      header: [{ label: 'Claim No.', value: '7731N261100138' }, { label: 'Job Card No. (Eicher)', value: '4010756021' }, { label: 'Vehicle Regn No.', value: 'OD00AA0001' }],
      spares: [], lube: [], freeItems: [],
      labour: [{ code: '0117999991', description: 'Service Van Branded', manHr: 267, ratePerHr: 22, taxable: 5874, tds: -117.48, total: 6813.84 }],
    }
    const row = buildInvoiceCsv(claim).replace('﻿', '').trim().split('\r\n')[1]

    expect(row).toContain('7731N261100138,4010756021,OD00AA0001,Labour')
    expect(row).toContain('267,22')
    expect(row).toContain('-117.48')
  })
})
