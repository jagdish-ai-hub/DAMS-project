import { ACCESS_TOKEN_KEY, USER_NAME_KEY, clearSession } from './session'

/**
 * Test-phase session rules (AGENT.md): one short-lived access JWT in
 * sessionStorage, nothing persistent — testers switch seeded role accounts
 * constantly, so logout must leave nothing behind.
 */
describe('session', () => {
  beforeEach(() => sessionStorage.clear())

  it('exposes stable storage keys', () => {
    expect(ACCESS_TOKEN_KEY).toBe('dams_access_token')
    expect(USER_NAME_KEY).toBe('dams_user_name')
  })

  it('clearSession removes the token and the cached name, nothing else', () => {
    sessionStorage.setItem(ACCESS_TOKEN_KEY, 'jwt.here')
    sessionStorage.setItem(USER_NAME_KEY, 'Cashier')
    sessionStorage.setItem('unrelated', 'keep-me')

    clearSession()

    expect(sessionStorage.getItem(ACCESS_TOKEN_KEY)).toBeNull()
    expect(sessionStorage.getItem(USER_NAME_KEY)).toBeNull()
    expect(sessionStorage.getItem('unrelated')).toBe('keep-me')
  })
})
