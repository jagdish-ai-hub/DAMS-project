import type { ExtractItem, InvoiceExtract } from '../api/invoiceExtract'

const COLUMNS: { label: string; get: (i: ExtractItem) => string | number | undefined }[] = [
  { label: 'Page', get: (i) => i.page },
  { label: 'No', get: (i) => i.sno },
  { label: 'Code', get: (i) => i.code },
  { label: 'HSN/SAC', get: (i) => i.hsnSac },
  { label: 'GST %', get: (i) => i.gstRate },
  { label: 'Description', get: (i) => i.description },
  { label: 'Qty', get: (i) => i.qty },
  { label: 'UoM', get: (i) => i.uom },
  { label: 'Man hours', get: (i) => i.manHr },
  { label: 'Rate per hour', get: (i) => i.ratePerHr },
  { label: 'Unit price / gross / rate', get: (i) => i.unitAmount },
  { label: 'Discount', get: (i) => i.discount },
  { label: 'Handling', get: (i) => i.handling },
  { label: 'Taxable', get: (i) => i.taxable },
  { label: 'CGST', get: (i) => i.cgst },
  { label: 'SGST', get: (i) => i.sgst },
  { label: 'IGST', get: (i) => i.igst },
  { label: 'KFC', get: (i) => i.kfc },
  { label: 'TDS', get: (i) => i.tds },
  { label: 'Total', get: (i) => i.total },
]

function cell(v: string | number | undefined | null): string {
  if (v === undefined || v === null) return ''
  const s = String(v)
  return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
}

function headerValue(r: InvoiceExtract, ...labels: string[]): string {
  for (const l of labels) {
    const f = r.header.find((h) => h.label === l)
    if (f) return f.value
  }
  return ''
}

/**
 * One CSV with every spares, lube and labour line (and the items replaced free under warranty), each row carrying the
 * document it came from. Opens straight in Excel; the BOM keeps non-English characters intact.
 */
export function buildInvoiceCsv(r: InvoiceExtract): string {
  const doc = [
    r.fileName,
    r.documentLabel,
    headerValue(r, 'Invoice No.', 'Claim No.'),
    headerValue(r, 'Job Card No. (Eicher)'),
    headerValue(r, 'Vehicle Reg No.', 'Vehicle Regn No.'),
  ]
  const lines = [['File', 'Document', 'Invoice / claim no', 'Job card no', 'Vehicle', 'Section', ...COLUMNS.map((c) => c.label)].map(cell).join(',')]
  const sections: [string, ExtractItem[]][] = [['Spares', r.spares], ['Lube', r.lube], ['Labour', r.labour]]
  for (const [name, items] of sections) {
    for (const item of items) {
      lines.push([...doc, name, ...COLUMNS.map((c) => c.get(item))].map(cell).join(','))
    }
  }
  for (const f of r.freeItems) {
    lines.push([...doc, 'Free under warranty/AMC/FOC',
      ...COLUMNS.map((c) => (c.label === 'No' ? f.sno : c.label === 'Code' ? f.itemCode : c.label === 'Description' ? f.description : c.label === 'Qty' ? f.quantity : undefined))]
      .map(cell).join(','))
  }
  return '﻿' + lines.join('\r\n') + '\r\n'
}
