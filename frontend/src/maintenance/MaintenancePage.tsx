import { useCallback, useEffect, useState } from 'react'
import { newBuildAvailable } from './maintenance'

/**
 * TEMPORARY maintenance screen (plan.md rev 44) — navy theme, a broken-server illustration and
 * a repair crew (wrench + gears). Self-contained: its own CSS, inline SVG, no other app screens.
 * It quietly re-checks for a new deploy and reloads itself the moment maintenance is over.
 */

const CHECK_EVERY_MS = 30_000

function nowIst(): string {
  return new Intl.DateTimeFormat('en-IN', {
    timeZone: 'Asia/Kolkata', hour: 'numeric', minute: '2-digit', hour12: true,
  }).format(new Date()).toLowerCase() + ' IST'
}

export default function MaintenancePage() {
  const [checkedAt, setCheckedAt] = useState(nowIst)
  const [checking, setChecking] = useState(false)

  useEffect(() => {
    const previous = document.title
    document.title = 'DAMS — Under maintenance'
    return () => { document.title = previous }
  }, [])

  const check = useCallback(async () => {
    setChecking(true)
    if (await newBuildAvailable()) {
      window.location.reload()
      return
    }
    setCheckedAt(nowIst())
    setChecking(false)
  }, [])

  useEffect(() => {
    const id = window.setInterval(check, CHECK_EVERY_MS)
    return () => window.clearInterval(id)
  }, [check])

  return (
    <div className="mt-page">
      <style>{CSS}</style>

      <header className="mt-top">
        <span className="mt-brand">DAMS</span>
        <span className="mt-brand-sub">Dealer Activity Management System</span>
      </header>

      <main className="mt-main">
        <section className="mt-card" aria-labelledby="mt-title">
          <div className="mt-art">
            <RepairIllustration />
          </div>

          <div className="mt-copy">
            <span className="mt-pill"><span className="mt-pill-dot mt-anim" />Maintenance in progress</span>
            <h1 id="mt-title">We&rsquo;re under maintenance</h1>
            <p className="mt-lead">
              DAMS is temporarily unavailable while we carry out scheduled maintenance.
              Please check back in a little while &mdash; we&rsquo;ll be back shortly.
            </p>

            <ul className="mt-points">
              <li><Tick />Your saved data is safe</li>
              <li><Tick />Nothing is needed from you right now</li>
              <li><Tick />This page refreshes by itself as soon as we&rsquo;re back</li>
            </ul>

            <div className="mt-bar mt-anim" aria-hidden="true" />

            <div className="mt-check" role="status" aria-live="polite">
              <span>{checking ? 'Checking…' : <>Last checked {checkedAt}</>}</span>
              <button type="button" className="mt-btn" onClick={check} disabled={checking}>
                Check again
              </button>
            </div>
          </div>
        </section>
      </main>
    </div>
  )
}

function Tick() {
  return (
    <svg className="mt-tick" viewBox="0 0 20 20" aria-hidden="true">
      <circle cx="10" cy="10" r="10" fill="#E4F3EB" />
      <path d="M5.5 10.4l3 3 6-6.4" fill="none" stroke="#1E7F4F" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

/** A rack with one unit ajar and smoking, and a wrench + gears on the repair. */
function RepairIllustration() {
  return (
    <svg
      viewBox="34 22 412 326"
      role="img"
      aria-label="A server rack with one unit ajar and smoking, while a wrench and gears carry out maintenance"
      className="mt-svg"
    >
      <defs>
        <pattern id="mt-hazard" width="22" height="22" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
          <rect width="22" height="22" fill="#F59E0B" />
          <rect width="11" height="22" fill="#1F3864" />
        </pattern>
        <radialGradient id="mt-glow" cx="50%" cy="50%" r="50%">
          <stop offset="0" stopColor="#EF4444" stopOpacity=".55" />
          <stop offset="1" stopColor="#EF4444" stopOpacity="0" />
        </radialGradient>
      </defs>

      {/* floor + shadow */}
      <ellipse cx="240" cy="334" rx="200" ry="13" fill="#101828" opacity=".08" />
      <rect x="40" y="330" width="400" height="12" rx="6" fill="url(#mt-hazard)" />

      {/* smoke */}
      <g fill="#98A1AF">
        <circle className="mt-smoke mt-anim" cx="268" cy="62" r="12" style={{ animationDelay: '0s' }} />
        <circle className="mt-smoke mt-anim" cx="252" cy="62" r="9" style={{ animationDelay: '.9s' }} />
        <circle className="mt-smoke mt-anim" cx="282" cy="64" r="8" style={{ animationDelay: '1.7s' }} />
      </g>

      {/* rack frame */}
      <rect x="120" y="76" width="240" height="248" rx="16" fill="#1F3864" />
      <rect x="120" y="76" width="240" height="248" rx="16" fill="none" stroke="#162a4d" strokeWidth="3" />
      <rect x="132" y="304" width="216" height="12" rx="4" fill="#162a4d" />

      {/* healthy units */}
      {[148, 206, 264].map((y, i) => (
        <g key={y}>
          <rect x="136" y={y} width="208" height="48" rx="9" fill="#2E5395" />
          <rect x="146" y={y + 12} width="92" height="5" rx="2.5" fill="#EAF0FB" opacity=".35" />
          <rect x="146" y={y + 22} width="70" height="5" rx="2.5" fill="#EAF0FB" opacity=".35" />
          <rect x="146" y={y + 32} width="82" height="5" rx="2.5" fill="#EAF0FB" opacity=".35" />
          <circle cx="316" cy={y + 24} r="5" fill="#34D399" className={i === 1 ? 'mt-flicker mt-anim' : undefined} />
          <circle cx="330" cy={y + 24} r="5" fill="#34D399" opacity=".55" />
        </g>
      ))}

      {/* the broken unit: pulled out, tilted, smoking, red light */}
      <g transform="rotate(-6 250 114) translate(12 -2)">
        <ellipse cx="318" cy="112" rx="34" ry="34" fill="url(#mt-glow)" className="mt-glow mt-anim" />
        <rect x="128" y="90" width="208" height="48" rx="9" fill="#3B6BC4" stroke="#162a4d" strokeWidth="2" />
        <rect x="140" y="102" width="70" height="5" rx="2.5" fill="#EAF0FB" opacity=".35" />
        <path d="M150 124 l12 -9 l9 8 l12 -10 l10 9" fill="none" stroke="#FCA5A5" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
        <circle cx="318" cy="114" r="6" fill="#EF4444" className="mt-led mt-anim" />
        <circle cx="332" cy="114" r="5" fill="#475467" />
      </g>

      {/* loose wires from the open unit */}
      <path d="M352 132 q 14 22 4 44" fill="none" stroke="#EF4444" strokeWidth="3" strokeLinecap="round" />
      <path d="M356 130 q 22 16 14 40" fill="none" stroke="#F59E0B" strokeWidth="3" strokeLinecap="round" />
      <circle cx="356" cy="178" r="3.5" fill="#F59E0B" />
      <circle cx="370" cy="172" r="3.5" fill="#F59E0B" />

      {/* sparks */}
      <g stroke="#F59E0B" strokeWidth="2.5" strokeLinecap="round">
        <line className="mt-spark mt-anim" x1="350" y1="104" x2="364" y2="96" />
        <line className="mt-spark mt-anim" x1="354" y1="114" x2="370" y2="114" style={{ animationDelay: '.35s' }} />
        <line className="mt-spark mt-anim" x1="350" y1="124" x2="362" y2="132" style={{ animationDelay: '.7s' }} />
      </g>

      {/* repair crew: two meshing gears + a wrench */}
      <g>
        <g transform="translate(72 258)">
          <g className="mt-gear mt-gear-a mt-anim">
            <circle r="30" fill="none" stroke="#2E5395" strokeWidth="12" strokeDasharray="11.8 11.8" />
            <circle r="25" fill="#2E5395" />
            <circle r="9" fill="#FFFFFF" />
          </g>
        </g>
        <g transform="translate(104 218)">
          <g className="mt-gear mt-gear-b mt-anim">
            <circle r="19" fill="none" stroke="#B45309" strokeWidth="9" strokeDasharray="7.4 7.4" />
            <circle r="15" fill="#B45309" />
            <circle r="6" fill="#FFFFFF" />
          </g>
        </g>
      </g>

      <g transform="translate(396 232) rotate(32)">
        <g className="mt-wrench mt-anim">
          <rect x="-8" y="6" width="16" height="104" rx="8" fill="#B45309" />
          <circle cx="0" cy="0" r="22" fill="#B45309" />
          <rect x="-8.5" y="-30" width="17" height="26" fill="#FFFFFF" />
          <circle cx="0" cy="92" r="4" fill="#FFFFFF" opacity=".7" />
        </g>
      </g>
    </svg>
  )
}

const CSS = `
.mt-page { min-height: 100vh; display: flex; flex-direction: column; background: #F1F4F9; color: #1A2233;
  font-family: 'Segoe UI', system-ui, -apple-system, 'Helvetica Neue', Arial, sans-serif; }
.mt-top { background: #1F3864; color: #fff; display: flex; align-items: baseline; gap: 12px;
  padding: 14px clamp(16px, 4vw, 32px); }
.mt-brand { font-weight: 800; font-size: 1.05rem; letter-spacing: .02em; }
.mt-brand-sub { font-size: .78rem; opacity: .75; }
.mt-main { flex: 1; display: flex; align-items: center; justify-content: center; padding: clamp(16px, 4vw, 40px); }
.mt-card { width: 100%; max-width: 940px; background: #fff; border: 1px solid #E3E7EE; border-radius: 14px;
  box-shadow: 0 4px 14px rgba(16,24,40,.10), 0 2px 6px rgba(16,24,40,.06);
  display: grid; grid-template-columns: minmax(0, 1fr); gap: 8px; padding: clamp(20px, 4vw, 40px); }
@media (min-width: 780px) { .mt-card { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); align-items: center; gap: 28px; } }
.mt-art { background: linear-gradient(180deg, #EAF0FB 0%, #F6F8FD 100%); border-radius: 12px; padding: 12px; }
.mt-svg { display: block; width: 100%; max-width: 100%; height: auto; }
.mt-pill { display: inline-flex; align-items: center; gap: 8px; background: #FCF0DE; color: #B45309; font-weight: 700;
  font-size: .76rem; border-radius: 999px; padding: 5px 12px; }
.mt-pill-dot { width: 8px; height: 8px; border-radius: 50%; background: #B45309; }
.mt-copy h1 { color: #1F3864; font-size: clamp(1.5rem, 3.4vw, 2rem); line-height: 1.2; margin: 14px 0 10px; }
.mt-lead { color: #66707F; font-size: .98rem; line-height: 1.6; }
.mt-points { list-style: none; margin: 18px 0 0; padding: 0; display: grid; gap: 10px; }
.mt-points li { display: flex; align-items: flex-start; gap: 10px; font-size: .9rem; line-height: 1.4; }
.mt-tick { width: 20px; height: 20px; flex: none; margin-top: 1px; }
.mt-bar { height: 8px; border-radius: 999px; margin: 22px 0 14px;
  background: repeating-linear-gradient(45deg, #F59E0B 0 10px, #1F3864 10px 20px); background-size: 28px 28px; }
.mt-check { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap;
  color: #66707F; font-size: .82rem; }
.mt-btn { background: #fff; color: #1F3864; border: 1.5px solid #1F3864; border-radius: 8px; padding: 7px 14px;
  font-weight: 700; font-size: .82rem; cursor: pointer; }
.mt-btn:hover:not(:disabled) { background: #EAF0FB; }
.mt-btn:disabled { opacity: .55; cursor: default; }

.mt-anim { animation-duration: 1.1s; animation-iteration-count: infinite; }
.mt-pill-dot.mt-anim { animation-name: mt-pulse; animation-duration: 1.6s; }
.mt-bar.mt-anim { animation-name: mt-stripes; animation-duration: 1s; animation-timing-function: linear; }
.mt-led.mt-anim { animation-name: mt-blink; }
.mt-glow.mt-anim { animation-name: mt-blink; animation-duration: 1.1s; }
.mt-flicker.mt-anim { animation-name: mt-flick; animation-duration: 3.2s; }
.mt-spark.mt-anim { animation-name: mt-spark; animation-duration: 1.05s; }
.mt-smoke.mt-anim { animation-name: mt-rise; animation-duration: 2.8s; animation-timing-function: ease-out;
  transform-box: fill-box; transform-origin: center; opacity: 0; }
.mt-gear { transform-box: fill-box; transform-origin: center; }
.mt-gear-a.mt-anim { animation-name: mt-spin; animation-duration: 7s; animation-timing-function: linear; }
.mt-gear-b.mt-anim { animation-name: mt-spin-rev; animation-duration: 4.6s; animation-timing-function: linear; }
.mt-wrench { transform-box: fill-box; transform-origin: 50% 14%; }
.mt-wrench.mt-anim { animation-name: mt-turn; animation-duration: 2.2s; animation-timing-function: ease-in-out; }

@keyframes mt-blink  { 0%, 55% { opacity: 1 } 56%, 100% { opacity: .2 } }
@keyframes mt-flick  { 0%, 88%, 100% { opacity: 1 } 90% { opacity: .25 } 94% { opacity: 1 } 96% { opacity: .3 } }
@keyframes mt-spark  { 0%, 100% { opacity: 0 } 15%, 45% { opacity: 1 } 70% { opacity: 0 } }
@keyframes mt-pulse  { 0%, 100% { transform: scale(1); opacity: 1 } 50% { transform: scale(1.5); opacity: .45 } }
@keyframes mt-spin     { to { transform: rotate(360deg) } }
@keyframes mt-spin-rev { to { transform: rotate(-360deg) } }
@keyframes mt-turn   { 0%, 100% { transform: rotate(-14deg) } 50% { transform: rotate(14deg) } }
@keyframes mt-stripes { to { background-position: 28px 0 } }
@keyframes mt-rise {
  0%   { transform: translateY(0) scale(.6); opacity: 0 }
  20%  { opacity: .6 }
  100% { transform: translateY(-44px) scale(1.6); opacity: 0 }
}
@media (prefers-reduced-motion: reduce) {
  .mt-anim { animation: none !important; }
  .mt-smoke { opacity: .45; }
  .mt-spark { opacity: 1; }
}
`
