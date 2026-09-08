import { apiError, isExpense, QUERY_TEMPLATES, wfTone } from './reviewShared'

/**
 * Review-queue display rules shared by the Accountant and FM queues: one tone
 * mapping, one expense detector, one error extractor — so both queues always
 * agree on what a document is and what state it is in.
 */
describe('wfTone', () => {
  it('warns amber on queried, red on rejected', () => {
    expect(wfTone('QUERIED')).toBe('amber')
    expect(wfTone('REJECTED')).toBe('red')
  })

  it('confirms green once a human has approved the state', () => {
    expect(wfTone('VERIFIED')).toBe('green')
    expect(wfTone('APPROVED')).toBe('green')
    expect(wfTone('CLOSED')).toBe('green')
  })

  it('stays neutral gray while work remains', () => {
    expect(wfTone('SUBMITTED')).toBe('gray')
    expect(wfTone('DRAFT')).toBe('gray')
  })
})

describe('isExpense', () => {
  it('tells expense documents from receipts by shape', () => {
    expect(isExpense({ expenseCategoryName: 'Fuel' } as never)).toBe(true)
    expect(isExpense({ settlementLines: [] } as never)).toBe(false)
  })
})

describe('apiError', () => {
  it('prefers the backend message, falls back cleanly', () => {
    expect(apiError({ response: { data: { message: 'Day is closed' } } }, 'fallback')).toBe('Day is closed')
    expect(apiError(new Error('boom'), 'fallback')).toBe('fallback')
    expect(apiError(null, 'fallback')).toBe('fallback')
  })
})

describe('QUERY_TEMPLATES', () => {
  it('offers the cashier actionable canned reasons', () => {
    expect(QUERY_TEMPLATES.length).toBeGreaterThan(0)
    for (const t of QUERY_TEMPLATES) {
      expect(typeof t).toBe('string')
      expect(t.length).toBeGreaterThan(0)
    }
  })
})
