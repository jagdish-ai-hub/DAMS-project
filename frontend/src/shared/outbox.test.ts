import { isOfflineError, outboxEnqueue, outboxList, outboxMarkError, outboxRemove } from './outbox'

/**
 * Offline outbox (FEAT-49): network failures queue, server rejections don't.
 * Nothing queued is real until it syncs as a draft.
 */
describe('isOfflineError', () => {
  it('treats no-response failures as offline', () => {
    expect(isOfflineError({ request: {}, code: 'ERR_NETWORK' })).toBe(true)
    expect(isOfflineError({ request: {} })).toBe(true)
  })

  it('treats server answers — even errors — as online', () => {
    expect(isOfflineError({ response: { status: 400 } })).toBe(false)
    expect(isOfflineError({ response: { status: 500 } })).toBe(false)
    expect(isOfflineError(null)).toBe(false)
    expect(isOfflineError(undefined)).toBe(false)
  })
})

describe('outbox queue', () => {
  beforeEach(() => localStorage.clear())

  it('enqueues, lists and removes entries', () => {
    const e = outboxEnqueue('receipt', 'Receipt (2 lines)', { submit: false })
    expect(e.id).toBeTruthy()
    expect(outboxList()).toHaveLength(1)
    expect(outboxList()[0].kind).toBe('receipt')
    outboxRemove(e.id)
    expect(outboxList()).toHaveLength(0)
  })

  it('marks sync errors without dropping the entry', () => {
    const e = outboxEnqueue('cash', 'Cash In', {})
    outboxMarkError(e.id, 'Server refused it')
    expect(outboxList()[0].error).toBe('Server refused it')
    expect(outboxList()).toHaveLength(1)
  })

  it('survives a reload via localStorage', () => {
    outboxEnqueue('expense', 'Expense (1 line)', { submit: false })
    const raw = localStorage.getItem('dams_outbox_v1')
    expect(raw).toContain('expense')
  })
})
