import api from './axios'

export interface Appearance {
  font: string
  available: string[]
}

export const appearanceApi = {
  /** Public — the login screen needs the font before anyone signs in. */
  get() {
    return api.get<Appearance>('/api/v1/public/appearance')
  },
  /** Super Admin only. */
  setFont(font: string) {
    return api.put<Appearance>('/api/v1/admin/appearance', { font })
  },
}
