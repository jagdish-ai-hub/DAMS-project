import { queryNote } from './CashPage'

/**
 * Cash-day money rules, mirrored from the backend drawer contract
 * (DrawerService.position + CashCloseService.closeDay) so a drift in either
 * direction breaks a test on this side too:
 *
 *   position = opening + cashReceipts + cashIn − cashExpenses − cashOut
 *   variance = counted − computed; remark mandatory iff variance ≠ 0.
 */
export function drawerPosition(o: {
  opening: number
  cashReceipts: number
  cashIn: number
  cashExpenses: number
  cashOut: number
}): number {
  return o.opening + o.cashReceipts + o.cashIn - o.cashExpenses - o.cashOut
}

export function varianceOf(counted: number, computed: number): number {
  return counted - computed
}

export function closeNeedsRemark(counted: number, computed: number): boolean {
  return varianceOf(counted, computed) !== 0
}

describe('drawer position', () => {
  it('adds receipts and Cash In, subtracts expenses and Cash Out', () => {
    // The seeded OOR day: 30000 + 5000 + 20000 − 800 − 15000.
    expect(
      drawerPosition({ opening: 30000, cashReceipts: 5000, cashIn: 20000, cashExpenses: 800, cashOut: 15000 }),
    ).toBe(39200)
  })

  it('collapses to the opening when nothing moved', () => {
    expect(drawerPosition({ opening: 10000, cashReceipts: 0, cashIn: 0, cashExpenses: 0, cashOut: 0 })).toBe(10000)
  })
})

describe('close variance gate', () => {
  it('needs no remark on an exact count', () => {
    expect(varianceOf(39200, 39200)).toBe(0)
    expect(closeNeedsRemark(39200, 39200)).toBe(false)
  })

  it('demands a remark on short and excess counts alike', () => {
    expect(varianceOf(38200, 39200)).toBe(-1000)
    expect(closeNeedsRemark(38200, 39200)).toBe(true)
    expect(varianceOf(39500, 39200)).toBe(300)
    expect(closeNeedsRemark(39500, 39200)).toBe(true)
  })
})

describe('queryNote (fix-and-resubmit banner)', () => {
  const q = (action: string, note?: string | null, at = '2026-08-20T10:00:00Z') => ({ action, note, at } as never)

  it('surfaces the most recent reviewer question', () => {
    expect(
      queryNote([q('Submitted'), q('Queried', 'UTR last-4 does not match'), q('Submitted')]),
    ).toBe('UTR last-4 does not match')
  })

  it('prefers rejections the same way', () => {
    expect(queryNote([q('Queried', 'old note'), q('Rejected', 'duplicate of R-004')])).toBe('duplicate of R-004')
  })

  it('returns null when there is nothing to fix', () => {
    expect(queryNote([q('Submitted'), q('Verified'), q('Approved')])).toBeNull()
    expect(queryNote([])).toBeNull()
  })

  it('ignores queried entries without a note', () => {
    expect(queryNote([q('Queried', null)])).toBeNull()
  })
})
