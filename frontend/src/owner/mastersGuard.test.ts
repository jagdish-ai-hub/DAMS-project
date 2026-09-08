import { mastersApi } from '../api/masters'
import { toUsageMap } from './MastersPage'

/**
 * Masters rules (AGENT.md decision #3): every dropdown comes from these tables,
 * rows are deactivated — never deleted — and the 90-day usage guard warns first.
 */
describe('deactivate, never delete', () => {
  it('exposes no delete/remove/destroy path on the API client', () => {
    const keys = Object.keys(mastersApi)
    expect(keys).toEqual(expect.arrayContaining(['list', 'create', 'update', 'usage']))
    expect(keys.join(' ')).not.toMatch(/delete|remove|destroy/i)
  })

  it('deactivation flows through update with {active:false}', async () => {
    const put = vi.spyOn((await import('../api/axios')).default, 'patch').mockResolvedValue({ data: {} } as never)
    await mastersApi.update('banks', 3, { name: 'SBI', active: false })
    expect(put).toHaveBeenCalledWith('/api/v1/masters/banks/3', { name: 'SBI', active: false })
    put.mockRestore()
  })
})

describe('toUsageMap (90-day deactivate guard)', () => {
  it('parses the current backend shape', () => {
    expect(toUsageMap([{ id: 1, name: 'Cash', useCount: 12 }])).toEqual({ 1: 12 })
  })

  it('accepts older count shapes', () => {
    expect(toUsageMap([{ id: 2, usedCount: 4 }])).toEqual({ 2: 4 })
    expect(toUsageMap([{ id: 3, count: 7 }])).toEqual({ 3: 7 })
    expect(toUsageMap({ 5: 9 })).toEqual({ 5: 9 })
  })

  it('yields an empty map for unknown shapes instead of crashing the form', () => {
    expect(toUsageMap(null)).toEqual({})
    expect(toUsageMap(undefined)).toEqual({})
    expect(toUsageMap('garbage')).toEqual({})
  })
})
