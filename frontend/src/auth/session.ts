/**
 * Session storage keys and helpers.
 *
 * v1 keeps the session deliberately thin: a single access token in `sessionStorage`,
 * so it does NOT survive a browser restart and testers can switch between the seeded
 * role accounts freely. No refresh token, no "remember me". See plan.md rev 3.
 */
export const ACCESS_TOKEN_KEY = 'dams_access_token'
export const USER_NAME_KEY = 'dams_user_name'

export function clearSession(): void {
  sessionStorage.removeItem(ACCESS_TOKEN_KEY)
  sessionStorage.removeItem(USER_NAME_KEY)
}
