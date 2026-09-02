import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import './styles/globals.css'
import { AuthProvider } from './auth/AuthContext'

// A focused <input type="number"> changes its value when the mouse wheel / trackpad scrolls
// over it — an amount silently drifts while the user scrolls the page. Blur it on wheel so the
// scroll just moves the page and never touches the number.
document.addEventListener(
  'wheel',
  (e) => {
    const el = document.activeElement
    if (el instanceof HTMLInputElement && el.type === 'number' && el === e.target) {
      el.blur()
    }
  },
  { passive: true },
)

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </React.StrictMode>,
)
