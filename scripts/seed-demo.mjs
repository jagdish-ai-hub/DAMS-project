#!/usr/bin/env node
/**
 * scripts/seed-demo.mjs — additive demo data for the existing org (Option A).
 *
 *   node scripts/seed-demo.mjs
 *
 * Adds ~8 customers per branch with current-month receipts / expenses / cash, each walked
 * through the real submit -> verify -> approve flow so every derived value (document numbers,
 * pending amounts, drawer positions, the audit trail, the dashboard) is computed by the app.
 *
 * Everything it creates is name-prefixed "DEMO-". Re-running aborts if DEMO- customers
 * already exist (pass SEED_FORCE=1 to add another batch anyway).
 *
 * Env (all optional):
 *   SEED_API                                    default http://localhost:8080
 *   SEED_OWNER_EMAIL / SEED_OWNER_PASSWORD      default owner@jjmotors.demo / owner123
 *   SEED_ACCOUNTANT_EMAIL / SEED_ACCOUNTANT_PASSWORD  accountant@jjmotors.demo / accountant123
 *   SEED_FM_EMAIL / SEED_FM_PASSWORD            default finance@jjmotors.demo / finance123
 *   SEED_CASHIER_EMAIL / SEED_CASHIER_PASSWORD  default cashier@jjmotors.demo / cashier123  (existing Rayagada cashier)
 *   SEED_STAFF_PASSWORD                         password for cashiers this script creates (default Demo@12345)
 *   SEED_FORCE=1                                seed again even if DEMO- data exists
 */

const API = (process.env.SEED_API || 'http://localhost:8080').replace(/\/+$/, '')
const STAFF_PW = process.env.SEED_STAFF_PASSWORD || 'Demo@12345'
const FORCE = process.env.SEED_FORCE === '1'

const CREDS = {
  owner:      [process.env.SEED_OWNER_EMAIL || 'owner@jjmotors.demo',           process.env.SEED_OWNER_PASSWORD || 'owner123'],
  accountant: [process.env.SEED_ACCOUNTANT_EMAIL || 'accountant@jjmotors.demo', process.env.SEED_ACCOUNTANT_PASSWORD || 'accountant123'],
  fm:         [process.env.SEED_FM_EMAIL || 'finance@jjmotors.demo',            process.env.SEED_FM_PASSWORD || 'finance123'],
  cashierOOR: [process.env.SEED_CASHIER_EMAIL || 'cashier@jjmotors.demo',       process.env.SEED_CASHIER_PASSWORD || 'cashier123'],
}

// ─── http ────────────────────────────────────────────────────────────
let TOKEN = null
async function rq(method, path, body) {
  const res = await fetch(API + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(TOKEN ? { Authorization: 'Bearer ' + TOKEN } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const txt = await res.text()
  let data = null
  try { data = txt ? JSON.parse(txt) : null } catch { data = txt }
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status} ${(data && data.message) || txt}`)
  return data
}
const GET = (p) => rq('GET', p)
const POST = (p, b) => rq('POST', p, b)
const PATCH = (p, b) => rq('PATCH', p, b)

async function login(key) {
  const [email, password] = CREDS[key] || key
  const r = await POST('/api/v1/auth/login', { email, password })
  TOKEN = r.accessToken
  return r
}
async function tryStep(label, fn) {
  try { return await fn() } catch (e) { console.warn(`   ! skipped ${label}: ${e.message}`); return null }
}

// ─── dates ───────────────────────────────────────────────────────────
const pad = (n) => String(n).padStart(2, '0')
function daysAgo(n) {
  const d = new Date(); d.setDate(d.getDate() - n)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

// ─── pools ───────────────────────────────────────────────────────────
const FIRST = [
  'Sumati', 'Rajendra', 'Laxmipriya', 'Debendra', 'Sasmita', 'Prafulla',
  'Gopal', 'Sanjukta', 'Trilochan', 'Basanti', 'Dillip', 'Namrata',
  'Ashok', 'Puspanjali', 'Harihar', 'Snehalata', 'Bijay', 'Kuntala',
]
const LAST = ['Sahoo', 'Nayak', 'Patra', 'Behera', 'Mohanty', 'Das']
const FIRMS = ['Kalinga Logistics', 'Maa Tarini Carriers', 'Konark Roadlines', 'Utkal Freight', 'Bharat Cargo Lines', 'Eastern Roadways']
const PHONE = (i) => '9437' + String(100000 + i * 1373).slice(0, 6)
const RTO = { OOJ: 'OD10', OOB: 'OD07', OOR: 'OD33' }
const vno = (code, i) => `${RTO[code] || 'OD00'}${'ABCDEFGH'[i % 8]}${String(1000 + i * 417).slice(0, 4)}`
const gst = (i) => `21ABCDE${String(1000 + i)}F1Z${i % 9}`

// 12 job cards / branch across 8 customers. [category, status, invoice, mode, payFraction, disposition]
function jobCardPlan(branchIndex) {
  const b2bOrReject = branchIndex === 0 ? 'reject' : 'approve'
  return [
    { c: 0, cat: 'Workshop',        st: 'WIP',      inv: 18500, mode: 'Cash',     pay: 1.0,  d: 'approve' },
    { c: 1, cat: 'Breakdown',       st: 'WIP',      inv: 9200,  mode: 'QR / UPI', pay: 1.0,  d: 'approve' },
    { c: 2, cat: 'Workshop',        st: 'WIP',      inv: 26000, mode: 'Bank',     pay: 1.0,  d: 'verify' },
    { c: 3, cat: 'Spare / Counter', st: 'Close',    inv: 4300,  mode: 'Cash',     pay: 1.0,  d: 'approve' },
    { c: 4, cat: 'Advance',         st: 'Hold',     inv: 7000,  mode: 'Adv-Cash', pay: 0.55, d: 'submitted' },
    { c: 5, cat: 'Workshop',        st: 'WIP',      inv: 14800, mode: 'Cash',     pay: 0.6,  d: 'query' },
    { c: 0, cat: 'AMC',             st: 'AMC',      inv: 21000, mode: 'Cash',     pay: 1.0,  d: 'claimExact' },
    { c: 1, cat: 'Warranty',        st: 'Warranty', inv: 16500, mode: 'Cash',     pay: 1.0,  d: 'claimShort' },
    { c: 6, cat: 'Workshop',        st: 'WIP',      inv: 12000, mode: 'Cash',     pay: 1.0,  d: 'approve' },
    { c: 7, cat: 'B2B Credit',      st: 'Credit',   inv: 33000, mode: 'Bank',     pay: 0.4,  d: b2bOrReject },
    { c: 2, cat: 'Workshop',        st: 'WIP',      inv: 6800,  mode: 'Cash',     pay: 1.0,  d: 'approve' },
    { c: 3, cat: 'Breakdown',       st: 'WIP',      inv: 5200,  mode: 'Cash',     pay: 0.0,  d: 'draft' },
  ]
}

// 5 expenses / branch. jc = index into that branch's job cards (for jobCardId), or null.
const EXPENSE_PLAN = [
  { cat: 'Service',  sub: 'Fuel (JC)',           amt: 900,  mode: 'Cash',     st: 'In Progress',      jc: 0,  d: 'approveClose' },
  { cat: 'Service',  sub: 'Taxi (JC)',           amt: 1800, mode: 'Cash',     st: 'In Progress',      jc: 2,  d: 'approveClose' },
  { cat: 'Showroom', sub: 'Stationary',          amt: 1200, mode: 'QR / UPI', st: 'Open',             jc: null, d: 'approve' },
  { cat: 'Service',  sub: 'Local Purchase (JC)', amt: 4200, mode: 'Cash',     st: 'Awaiting Receipt', jc: 8,  d: 'overLimit' },
  { cat: 'Service',  sub: 'Fuel (JC)',           amt: 1500, mode: 'Cash',     st: 'Transfer to Claim', jc: 6, d: 'transferClaim' },
]

const CASH_PLAN = [
  { dir: 'IN',  amt: 20000, day: 9, d: 'approve' },
  { dir: 'OUT', amt: 8000,  day: 7, d: 'approve' },
  { dir: 'IN',  amt: 12000, day: 5, d: 'approve' },
  { dir: 'IN',  amt: 5000,  day: 1, d: 'submitted' },
]

// ─── masters ─────────────────────────────────────────────────────────
async function loadMasters() {
  const idx = {}
  for (const slug of ['receive-categories', 'receive-statuses', 'settlement-modes', 'expense-categories', 'expense-modes', 'expense-statuses', 'banks']) {
    const rows = await GET(`/api/v1/masters/${slug}`)
    idx[slug] = Object.fromEntries(rows.map((r) => [r.name, r]))
  }
  const ecats = await GET('/api/v1/masters/expense-categories')
  idx['expense-sub-categories'] = {}
  for (const c of ecats) {
    const rows = await GET(`/api/v1/masters/expense-sub-categories?expenseCategoryId=${c.id}`)
    for (const r of rows) idx['expense-sub-categories'][r.name] = r
  }
  return idx
}
const must = (M, slug, name) => {
  const r = M[slug] && M[slug][name]
  if (!r) throw new Error(`master "${name}" not found in ${slug} — have: ${Object.keys(M[slug] || {}).join(', ')}`)
  return r
}

// ─── main ────────────────────────────────────────────────────────────
const stats = { customers: 0, vehicles: 0, jobCards: 0, receipts: {}, expenses: {}, cash: {}, claimsClosed: 0 }
const bump = (obj, k) => { obj[k] = (obj[k] || 0) + 1 }

// ─── top-up: a small CURRENT-MONTH batch on existing DEMO customers ───
// The main seed dates transactions 2–10 days back; if "today" is early in the month those
// all fall in the previous month and the dashboard's month-to-date KPIs read 0. This adds a
// handful of approved, current-month receipts / expenses / cash per branch so MTD populates.
async function topup() {
  console.log(`DAMS demo seed — TOP-UP (current-month)  ->  ${API}`)
  await login('owner')
  const branches = await GET('/api/v1/branches')
  const byCode = Object.fromEntries(branches.map((b) => [b.code, b]))
  const order = ['OOJ', 'OOB', 'OOR'].filter((c) => byCode[c] && byCode[c].active)
  const cashierCreds = {
    OOR: CREDS.cashierOOR,
    OOJ: ['cashier.ooj@jjmotors.demo', STAFF_PW],
    OOB: ['cashier.oob@jjmotors.demo', STAFF_PW],
  }
  const M = await loadMasters()
  const cat = must(M, 'receive-categories', 'Workshop')
  const st = must(M, 'receive-statuses', 'WIP')
  const cash = must(M, 'settlement-modes', 'Cash')
  const ec = must(M, 'expense-categories', 'Service')
  const es = must(M, 'expense-statuses', 'In Progress')
  const em = must(M, 'expense-modes', 'Cash')
  const sub = must(M, 'expense-sub-categories', 'Fuel (JC)')
  const sbi = must(M, 'banks', 'State Bank of India')

  const made = { receipts: [], expenses: [], cash: [] }
  for (const code of order) {
    await login(cashierCreds[code])
    const hits = (await GET('/api/v1/search?q=DEMO-')).hits.slice(0, 4)
    console.log(`\n[${code}] top-up on ${hits.length} existing DEMO customers`)
    let n = 0
    for (const h of hits) {
      const amt = 8000 + n * 2600
      const r = await POST('/api/v1/receipts', {
        customerId: h.customerId,
        categoryId: cat.id,
        businessStatusId: st.id,
        invoiceNo: String(7739000000000 + n),
        invoiceAmount: amt,
        lines: [{ transactionDate: daysAgo(n % 2), settlementModeId: cash.id, amount: amt }],
        submit: true,
      })
      made.receipts.push(r.id)
      n++
    }
    const x = await POST('/api/v1/expenses', {
      receiverName: 'DEMO-City Fuel Station',
      expenseCategoryId: ec.id,
      businessStatusId: es.id,
      lines: [{ transactionDate: daysAgo(1), subCategoryId: sub.id, expenseModeId: em.id, amount: 850 }],
      submit: true,
    })
    made.expenses.push(x.id)
    const c = await POST('/api/v1/cash-documents', {
      direction: 'IN', transactionDate: daysAgo(1), amount: 10000, bankId: sbi.id,
      transactionRef: `NEFT${Date.now() % 1000000}`, submit: true,
    })
    made.cash.push(c.id)
  }

  await login('accountant')
  console.log('\n[accountant] verify')
  for (const id of made.receipts) await POST(`/api/v1/receipts/${id}/verify`)
  for (const id of made.expenses) await POST(`/api/v1/expenses/${id}/verify`)
  for (const id of made.cash) await POST(`/api/v1/cash-documents/${id}/verify`)

  await login('fm')
  console.log('[finance] approve')
  for (const id of made.receipts) await POST(`/api/v1/receipts/${id}/approve`)
  for (const id of made.expenses) await tryStep(`approve E${id}`, () => POST(`/api/v1/expenses/${id}/approve`))
  for (const id of made.cash) await POST(`/api/v1/cash-documents/${id}/approve`)

  await login('accountant')
  for (const id of made.expenses) await tryStep(`close E${id}`, () => POST(`/api/v1/expenses/${id}/close`))

  console.log(`\ntop-up done: ${made.receipts.length} receipts, ${made.expenses.length} expenses, ${made.cash.length} cash — all approved, dated this month.`)
}

async function run() {
  if (process.env.SEED_TOPUP === '1') return topup()
  console.log(`DAMS demo seed  ->  ${API}`)
  await login('owner')

  const branches = await GET('/api/v1/branches')
  const byCode = Object.fromEntries(branches.map((b) => [b.code, b]))
  const order = ['OOJ', 'OOB', 'OOR'].filter((c) => byCode[c] && byCode[c].active)
  if (order.length === 0) throw new Error('no active branches found')
  console.log(`branches: ${order.join(', ')}`)

  // guard
  await login('accountant')
  const probe = await GET('/api/v1/search?q=DEMO-')
  if ((probe.hits || []).length && !FORCE) {
    console.error(`\nAborting: ${probe.hits.length} DEMO- customers already exist. Set SEED_FORCE=1 to add another batch.`)
    process.exit(1)
  }

  // ensure a cashier per branch + accountant covers all branches
  await login('owner')
  const users = await GET('/api/v1/users')
  const cashierCreds = { OOR: CREDS.cashierOOR }
  for (const code of order) {
    if (cashierCreds[code]) continue
    const email = `cashier.${code.toLowerCase()}@jjmotors.demo`
    let u = users.find((x) => x.email === email)
    if (!u) {
      console.log(`creating cashier ${email} (${code})`)
      const created = await POST('/api/v1/users', { name: `DEMO Cashier ${byCode[code].name}`, email, role: 'CASHIER', homeBranchId: byCode[code].id })
      const token = new URL(created.inviteLink).searchParams.get('token')
      await POST('/api/v1/auth/accept-invite', { token, password: STAFF_PW })
    }
    cashierCreds[code] = [email, STAFF_PW]
  }
  const acc = users.find((x) => x.role === 'ACCOUNTANT')
  if (acc) {
    const allIds = order.map((c) => byCode[c].id)
    await tryStep('widen accountant branch access', () =>
      PATCH(`/api/v1/users/${acc.id}`, { name: acc.name, email: acc.email, role: 'ACCOUNTANT', branchIds: allIds }))
  }

  const M = await loadMasters()

  // ── phase 1: cashiers create everything ───────────────────────────
  const world = []
  for (let bi = 0; bi < order.length; bi++) {
    const code = order[bi]
    const branch = byCode[code]
    await login(cashierCreds[code])
    console.log(`\n[${code}] ${branch.name} — creating`)
    const B = { code, id: branch.id, jobCards: [], expenses: [], cash: [] }

    // customers 0..7 (6 individuals + 2 firms), each 1 vehicle (c1,c4 get 2)
    const custId = []
    const custVeh = []
    for (let i = 0; i < 8; i++) {
      const isFirm = i >= 6
      const name = isFirm
        ? `DEMO-${FIRMS[bi * 2 + (i - 6)]}`
        : `DEMO-${FIRST[bi * 6 + i]} ${LAST[(bi + i) % LAST.length]}`
      const c = await POST('/api/v1/customers', { name, phone: PHONE(bi * 8 + i) })
      stats.customers++
      const vs = []
      const nv = (i === 1 || i === 4) ? 2 : 1
      for (let k = 0; k < nv; k++) {
        const v = await POST('/api/v1/vehicles', { customerId: c.id, vehicleNo: vno(code, i * 3 + k) })
        stats.vehicles++; vs.push(v.id)
      }
      custId.push(c.id); custVeh.push(vs)
    }

    // job cards + receipts
    const plan = jobCardPlan(bi)
    let overrideUsed = false
    for (const p of plan) {
      const isFirm = p.c >= 6
      const rc = must(M, 'receive-categories', p.cat)
      const rs = must(M, 'receive-statuses', p.st)
      const sm = must(M, 'settlement-modes', p.mode)
      const draft = p.d === 'draft'
      const lineAmt = Math.round(p.inv * p.pay)
      const line = {
        transactionDate: daysAgo(2 + (stats.jobCards % 9)),
        settlementModeId: sm.id,
        amount: lineAmt,
        ...(sm.requiresBank ? { bankId: must(M, 'banks', 'HDFC Bank').id } : {}),
        ...(sm.requiresRef ? { transactionRef: `TXN${Date.now() % 100000}${stats.jobCards}` } : {}),
      }
      const body = {
        customerId: custId[p.c],
        vehicleId: custVeh[p.c][0],
        categoryId: rc.id,
        businessStatusId: rs.id,
        dbmId: String(4000000000 + bi * 10000 + stats.jobCards),
        invoiceNo: String(7731122600000 + bi * 1000 + stats.jobCards),
        invoiceAmount: p.inv,
        ...(isFirm ? { b2b: true, gstNo: gst(bi * 8 + p.c) } : {}),
        lines: draft ? [] : [line],
        submit: !draft,
      }
      const r = await POST('/api/v1/receipts', body)
      stats.jobCards++; bump(stats.receipts, p.d)
      const lineNo = (r.lines && r.lines[0] && r.lines[0].lineNo) || 1
      const wantsOverride = !overrideUsed && (p.d === 'verify' || p.d === 'approve')
      if (wantsOverride) overrideUsed = true
      B.jobCards.push({ recId: r.id, jcId: r.jobCardId, lineNo, d: p.d, cat: p.cat, override: wantsOverride })
    }

    // expenses
    for (const e of EXPENSE_PLAN) {
      const ec = must(M, 'expense-categories', e.cat)
      const es = must(M, 'expense-statuses', e.st)
      const em = must(M, 'expense-modes', e.mode)
      const sub = must(M, 'expense-sub-categories', e.sub)
      const jobCardId = e.jc == null ? undefined : B.jobCards[e.jc] && B.jobCards[e.jc].jcId
      const body = {
        jobCardId,
        receiverName: `DEMO-${['Sharma Auto Spares', 'City Fuel Station', 'Rayagada Taxi Union', 'Quick Courier', 'Balaji Traders'][stats.jobCards % 5]}`,
        expenseCategoryId: ec.id,
        businessStatusId: es.id,
        lines: [{
          transactionDate: daysAgo(3 + (Object.keys(stats.expenses).length % 7)),
          subCategoryId: sub.id,
          expenseModeId: em.id,
          amount: e.amt,
          ...(em.requiresRef ? { transactionRef: `UPI${Date.now() % 100000}` } : {}),
        }],
        submit: true,
      }
      const x = await POST('/api/v1/expenses', body)
      bump(stats.expenses, e.d)
      const lineNo = (x.lines && x.lines[0] && x.lines[0].lineNo) || 1
      if (e.d === 'transferClaim') await tryStep(`transfer-to-claim E${x.id}`, () => POST(`/api/v1/expenses/${x.id}/transfer-to-claim`))
      B.expenses.push({ id: x.id, d: e.d, lineNo })
    }

    // cash
    await tryStep(`opening ${code}`, () =>
      POST('/api/v1/cash/opening', { branchId: branch.id, openingDate: daysAgo(12), amount: 15000 }))
    for (const cc of CASH_PLAN) {
      const x = await POST('/api/v1/cash-documents', {
        direction: cc.dir,
        transactionDate: daysAgo(cc.day),
        amount: cc.amt,
        bankId: must(M, 'banks', 'State Bank of India').id,
        transactionRef: `NEFT${Date.now() % 1000000}`,
        submit: true,
      })
      bump(stats.cash, cc.d)
      B.cash.push({ id: x.id, d: cc.d })
    }

    world.push(B)
  }

  // ── phase 2: accountant review ───────────────────────────────────
  await login('accountant')
  console.log('\n[accountant] reviewing')
  for (const B of world) {
    for (const jc of B.jobCards) {
      if (jc.d === 'draft' || jc.d === 'submitted') continue
      if (jc.d === 'query') { await POST(`/api/v1/receipts/${jc.recId}/query`, { note: 'Please attach the customer-signed copy and confirm the vehicle number.' }); continue }
      if (jc.d === 'reject') { await POST(`/api/v1/receipts/${jc.recId}/reject`, { reason: 'Duplicate of an earlier receipt for the same job card.' }); continue }
      if (jc.override) {
        await tryStep(`override R${jc.recId}`, () =>
          POST(`/api/v1/receipts/${jc.recId}/lines/${jc.lineNo}/override`, { amount: 9500, reason: 'Corrected to match the signed workshop invoice.' }))
      }
      await POST(`/api/v1/receipts/${jc.recId}/verify`)
    }
    for (const e of B.expenses) {
      if (e.d === 'overLimit') {
        await tryStep(`override E${e.id}`, () =>
          POST(`/api/v1/expenses/${e.id}/lines/${e.lineNo}/override`, { amount: 4100, reason: 'Adjusted to the vendor bill total.' }))
      }
      await POST(`/api/v1/expenses/${e.id}/verify`)
    }
    for (const c of B.cash) {
      if (c.d === 'submitted') continue
      await POST(`/api/v1/cash-documents/${c.id}/verify`)
    }
  }

  // ── phase 3: finance manager approve + close claims ──────────────
  await login('fm')
  console.log('[finance] approving + closing claims')
  for (const B of world) {
    for (const jc of B.jobCards) {
      if (!['approve', 'claimExact', 'claimShort'].includes(jc.d)) continue
      await POST(`/api/v1/receipts/${jc.recId}/approve`)
      if (jc.d === 'claimExact' || jc.d === 'claimShort') {
        const doc = await GET(`/api/v1/receipts/${jc.recId}`)
        const recd = Number(doc.totalReceived || 0)
        const finalAmount = jc.d === 'claimExact' ? recd : Math.max(0, recd - 1500)
        await tryStep(`close-claim JC${jc.jcId}`, () =>
          POST(`/api/v1/job-cards/${jc.jcId}/close-claim`, {
            finalAmount,
            ...(jc.d === 'claimShort' ? { reason: 'Eicher settled short of the claimed amount; accepted as final.' } : {}),
          }))
        stats.claimsClosed++
      }
    }
    for (const e of B.expenses) {
      if (e.d === 'overLimit') continue        // left awaiting finance approval on purpose
      await tryStep(`approve E${e.id}`, () => POST(`/api/v1/expenses/${e.id}/approve`))
    }
    for (const c of B.cash) {
      if (c.d === 'submitted') continue
      await POST(`/api/v1/cash-documents/${c.id}/approve`)
    }
  }

  // ── phase 4: accountant closes some expenses ─────────────────────
  await login('accountant')
  console.log('[accountant] closing expenses')
  for (const B of world) {
    for (const e of B.expenses) {
      if (e.d !== 'approveClose') continue
      await tryStep(`close E${e.id}`, () => POST(`/api/v1/expenses/${e.id}/close`))
    }
  }

  // ── phase 5: cashiers close a recent day ─────────────────────────
  console.log('[cashiers] day-close')
  for (let bi = 0; bi < world.length; bi++) {
    const B = world[bi]
    await login(cashierCreds[B.code])
    await tryStep(`close-day ${B.code}`, async () => {
      const drawer = await GET(`/api/v1/cash/drawer?date=${daysAgo(3)}&branchId=${B.id}`)
      const computed = Number(drawer.computedPosition || 0)
      const short = bi === world.length - 1
      await POST('/api/v1/cash/close-day', {
        branchId: B.id,
        closeDate: daysAgo(3),
        countedAmount: short ? computed - 300 : computed,
        ...(short ? { varianceRemark: 'Short by 300 — pending petty-cash slip from the workshop.' } : {}),
      })
    })
  }

  // ── summary ─────────────────────────────────────────────────────
  console.log('\n──────────── seeded ────────────')
  console.log(`customers  ${stats.customers}`)
  console.log(`vehicles   ${stats.vehicles}`)
  console.log(`job cards  ${stats.jobCards}`)
  console.log(`receipts   ${JSON.stringify(stats.receipts)}`)
  console.log(`expenses   ${JSON.stringify(stats.expenses)}`)
  console.log(`cash docs  ${JSON.stringify(stats.cash)}`)
  console.log(`claims closed ${stats.claimsClosed}`)
  console.log('\nlogins:')
  console.log(`  owner       ${CREDS.owner[0]} / ${CREDS.owner[1]}`)
  console.log(`  accountant  ${CREDS.accountant[0]} / ${CREDS.accountant[1]}`)
  console.log(`  finance     ${CREDS.fm[0]} / ${CREDS.fm[1]}`)
  for (const code of Object.keys(cashierCreds)) {
    console.log(`  cashier ${code}  ${cashierCreds[code][0]} / ${cashierCreds[code][1]}`)
  }
  console.log('\nall seeded rows are name-prefixed "DEMO-".')
}

run().catch((e) => { console.error('\nSEED FAILED:', e.message); process.exit(1) })
