/*
 * Platform UI font — chosen by Super Admin (Settings → Appearance), applied to every role and
 * to the login screen. See AGENT.md "Appearance — platform font".
 *
 * A fixed, vetted list on purpose: every font here is self-hosted and verified to carry the ₹
 * glyph (U+20B9, in each package's latin-ext subset) and equal-width digits so amount columns
 * line up. To add a font: one entry here + its @fontsource package + the backend allowlist
 * (AppearanceService.ALLOWED_FONTS).
 */

export type FontKey = 'plex' | 'inter'

export interface FontDef {
  key: FontKey
  label: string
  /** Primary family name as declared by the @fontsource CSS. */
  family: string
  /** OpenType features for this font (Inter needs `tnum` for tabular figures; Plex's digits already are). */
  features: string
  blurb: string
  load: () => Promise<unknown>
}

export const FONTS: FontDef[] = [
  {
    key: 'plex',
    label: 'IBM Plex Sans',
    family: "'IBM Plex Sans'",
    features: "'kern'",
    blurb: 'Engineered and precise — a bank-grade feel with distinctive figures.',
    load: () => Promise.all([
      import('@fontsource/ibm-plex-sans/400.css'),
      import('@fontsource/ibm-plex-sans/500.css'),
      import('@fontsource/ibm-plex-sans/600.css'),
      import('@fontsource/ibm-plex-sans/700.css'),
    ]),
  },
  {
    key: 'inter',
    label: 'Inter',
    family: "'Inter Variable'",
    features: "'kern', 'tnum'",
    blurb: 'Neutral and highly legible — the modern software standard.',
    load: () => import('@fontsource-variable/inter/index.css'),
  },
]

export const DEFAULT_FONT: FontKey = 'plex'
const FALLBACK = "'Segoe UI', system-ui, -apple-system, 'Helvetica Neue', Arial, sans-serif"
const CACHE_KEY = 'dams.ui.font'

export function fontDef(key: string | null | undefined): FontDef {
  return FONTS.find((f) => f.key === key) ?? FONTS.find((f) => f.key === DEFAULT_FONT)!
}

/** Loads the font's files (once — dynamic imports are cached) and points `--font` at it. */
export function applyFont(key: string | null | undefined): FontKey {
  const f = fontDef(key)
  f.load().catch(() => { /* the system fallback stays in place */ })
  const root = document.documentElement
  root.style.setProperty('--font', `${f.family}, ${FALLBACK}`)
  root.style.setProperty('--font-features', f.features)
  root.dataset.font = f.key
  return f.key
}

export function cachedFont(): FontKey | null {
  try {
    const v = localStorage.getItem(CACHE_KEY)
    return FONTS.some((f) => f.key === v) ? (v as FontKey) : null
  } catch {
    return null
  }
}

export function cacheFont(key: FontKey) {
  try {
    localStorage.setItem(CACHE_KEY, key)
  } catch {
    /* private mode / blocked storage — the server value is re-fetched on every load anyway */
  }
}
