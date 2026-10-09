import { inr } from './ui'

describe('inr', () => {
  it('groups the Indian way and rounds to the rupee', () => {
    expect(inr(886)).toBe('₹886')
    expect(inr(925813)).toBe('₹9,25,813')
    expect(inr(1234.6)).toBe('₹1,235')
  })

  it('treats missing as zero', () => {
    expect(inr(null)).toBe('₹0')
    expect(inr(undefined)).toBe('₹0')
  })

  it('puts a negative sign in front of the rupee mark', () => {
    expect(inr(-1025)).toBe('−₹1,025')
    expect(inr(-0.2)).toBe('₹0')
  })
})
