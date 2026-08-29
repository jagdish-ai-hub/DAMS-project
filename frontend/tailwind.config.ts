import type { Config } from 'tailwindcss'

const config: Config = {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Design system from mockups — match these exactly
        navy:    'var(--navy)',
        navy2:   'var(--navy2)',
        navy3:   'var(--navy3)',
        bg:      'var(--bg)',
        surface: 'var(--surface)',
        line:    'var(--line)',
        ink:     'var(--ink)',
        muted:   'var(--muted)',
        faint:   'var(--faint)',
        green:   'var(--green)',
        'green-bg': 'var(--green-bg)',
        amber:   'var(--amber)',
        'amber-bg': 'var(--amber-bg)',
        red:     'var(--red)',
        'red-bg':   'var(--red-bg)',
        blue:    'var(--blue)',
        'blue-bg':  'var(--blue-bg)',
        gray:    'var(--gray)',
        'gray-bg':  'var(--gray-bg)',
      },
      borderRadius: {
        DEFAULT: 'var(--radius)',
      },
    },
  },
  plugins: [],
}

export default config
