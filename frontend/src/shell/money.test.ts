import { fmtDate, fmtDateShort, fmtDateTime, inr, initials, istToday } from './ui'

/**
 * Money + date display rules (the read-only half of the money logic): every rupee
 * figure and every date the app shows goes through these helpers, so one test here
 * pins the format for all five roles at once.
 */
describe('inr', () => {
  it('formats whole rupees with Indian digit grouping', () => {
    expect(inr(15431)).toBe('₹15,431')
    expect(inr(100000)).toBe('₹1,00,000')
    expect(inr(39200)).toBe('₹39,200')
  })

  it('rounds to whole rupees and tolerates null/undefined', () => {
    expect(inr(99.6)).toBe('₹100')
    expect(inr(null)).toBe('₹0')
    expect(inr(undefined)).toBe('₹0')
    expect(inr(0)).toBe('₹0')
  })
})

describe('calendar dates are parsed from parts, never new Date(str)', () => {
  it('formats yyyy-mm-dd without timezone slip', () => {
    expect(fmtDate('2026-08-07')).toBe('7 August 2026')
    expect(fmtDateShort('2026-08-07')).toBe('7 Aug 2026')
  })

  it('returns an em dash for missing dates and echoes garbage', () => {
    expect(fmtDate(null)).toBe('—')
    expect(fmtDate('')).toBe('—')
    expect(fmtDateShort(undefined)).toBe('—')
    expect(fmtDate('not-a-date')).toBe('not-a-date')
  })
})

describe('instants render in India time for every viewer', () => {
  it('converts a UTC instant to IST and labels it', () => {
    // 09:42 UTC == 15:12 IST on 7 Aug 2026.
    expect(fmtDateTime('2026-08-07T09:42:00Z')).toBe('7 August 2026, 3:12 pm IST')
  })

  it('returns an em dash for missing instants', () => {
    expect(fmtDateTime(null)).toBe('—')
    expect(fmtDateTime(undefined)).toBe('—')
  })
})

describe('istToday / initials', () => {
  it('returns today in India as yyyy-mm-dd', () => {
    expect(istToday()).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })

  it('takes up to two uppercase initials', () => {
    expect(initials('Bikram Nayak')).toBe('BN')
    expect(initials('  single  ')).toBe('S')
    expect(initials('')).toBe('')
  })
})
