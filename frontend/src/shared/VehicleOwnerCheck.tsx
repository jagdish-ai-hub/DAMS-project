import { useCallback, useRef, useState, type ReactNode } from 'react'
import { customersApi } from '../api/customers'
import { vehiclesApi, type Vehicle } from '../api/vehicles'
import { Modal, ghostBtn, primaryBtn } from '../shell/ui'
import { normaliseVehicleNo } from './PartyPickers'

/**
 * rev 70 — at save/submit, a typed vehicle number that is already on record under a customer
 * other than the one entered is never switched silently: the cashier is asked which is right.
 * (The server enforces the same rule; this is the friendly way to resolve it.)
 */

export interface OwnerMismatch {
  vehicle: Vehicle
  /** What the cashier entered: a typed new name, or the name of a different picked customer. */
  enteredName: string
  /** True when the entered customer is an existing record — it cannot be renamed into the owner. */
  enteredIsExisting: boolean
}

/** What the form should save under after the dialog: the customer on record, linked to the vehicle. */
export interface OwnerChoice {
  customerId: number
  customerName: string
  vehicleId: number
}

type Outcome = OwnerChoice | 'cancel' | null  // null = nothing to ask, save as entered

const squash = (s: string) => s.trim().replace(/\s+/g, ' ').toLowerCase()

/**
 * Looks the typed vehicle number up and reports a mismatch, or null when there is none (or the
 * lookup failed — the server's own check is the backstop, so a flaky lookup never blocks saving).
 */
export async function findOwnerMismatch(input: {
  customerId: number | null
  customerName: string
  vehicleId: number | null
  vehicleNo: string
}): Promise<OwnerMismatch | null> {
  if (input.vehicleId != null) return null // picked from the list: consistent by construction
  const no = normaliseVehicleNo(input.vehicleNo)
  if (!no) return null
  let vehicle: Vehicle
  try {
    vehicle = (await vehiclesApi.byNumber(no)).data
  } catch {
    return null // 404 = a new number
  }
  if (input.customerId != null) {
    if (vehicle.customerId === input.customerId) return null
    return { vehicle, enteredName: input.customerName.trim() || 'the selected customer', enteredIsExisting: true }
  }
  const typed = input.customerName.trim()
  if (!typed || squash(typed) === squash(vehicle.customerName ?? '')) return null
  return { vehicle, enteredName: typed, enteredIsExisting: false }
}

type Pick = 'owner' | 'rename'

const optionBox = (selected: boolean) => ({
  display: 'flex', alignItems: 'flex-start', gap: 10, textAlign: 'left' as const, width: '100%',
  padding: '12px 14px', borderRadius: 10, cursor: 'pointer',
  border: `2px solid ${selected ? 'var(--navy)' : 'var(--line)'}`,
  background: selected ? 'var(--bg)' : 'var(--surface)',
})

function Option(props: { selected: boolean; onSelect: () => void; title: string; children: ReactNode }) {
  return (
    <button type="button" role="radio" aria-checked={props.selected} onClick={props.onSelect} style={optionBox(props.selected)}>
      <span aria-hidden style={{
        width: 16, height: 16, borderRadius: 999, flexShrink: 0, marginTop: 2,
        border: `2px solid ${props.selected ? 'var(--navy)' : 'var(--muted)'}`,
        background: props.selected ? 'var(--navy)' : 'transparent', boxShadow: props.selected ? 'inset 0 0 0 3px var(--surface)' : 'none',
      }} />
      <span>
        <span style={{ display: 'block', fontWeight: 700, fontSize: '0.88rem', color: 'var(--navy)' }}>{props.title}</span>
        <span style={{ display: 'block', fontSize: '0.78rem', color: 'var(--muted)', marginTop: 2 }}>{props.children}</span>
      </span>
    </button>
  )
}

export function VehicleOwnerDialog(props: {
  mismatch: OwnerMismatch
  onProceed: (choice: OwnerChoice) => void
  onCancel: () => void
}) {
  const { vehicle, enteredName, enteredIsExisting } = props.mismatch
  const owner = vehicle.customerName ?? 'the customer on record'
  const [pick, setPick] = useState<Pick | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function proceed() {
    if (!pick) return
    setBusy(true)
    setError('')
    try {
      if (pick === 'rename') {
        // The update replaces name AND phone, so carry the saved phone over.
        const { data: current } = await customersApi.get(vehicle.customerId)
        await customersApi.update(vehicle.customerId, { name: enteredName, phone: current.phone ?? undefined })
      }
      props.onProceed({
        customerId: vehicle.customerId,
        customerName: pick === 'rename' ? enteredName : owner,
        vehicleId: vehicle.id,
      })
    } catch {
      setError('Could not update the name. Try again, or choose the other option.')
      setBusy(false)
    }
  }

  return (
    <Modal
      title="Vehicle is on record under a different name"
      subtitle={`${vehicle.vehicleNo} is registered to ${owner}, but you entered ${enteredName}.`}
      onClose={props.onCancel}
      footer={
        <>
          <button type="button" style={ghostBtn} onClick={props.onCancel} disabled={busy}>Cancel</button>
          <button type="button" style={primaryBtn(!pick || busy)} onClick={proceed} disabled={!pick || busy}>
            {busy ? 'Working…' : 'Proceed'}
          </button>
        </>
      }
    >
      <div role="radiogroup" aria-label="Which name is correct" style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        <Option selected={pick === 'owner'} onSelect={() => setPick('owner')} title={`${owner} is correct`}>
          Save this under {owner}, the customer on record for {vehicle.vehicleNo}. “{enteredName}” is not used.
        </Option>
        {!enteredIsExisting && (
          <Option selected={pick === 'rename'} onSelect={() => setPick('rename')} title={`Update the name to ${enteredName}`}>
            Rename {owner} to “{enteredName}” on all of their records, then save under that customer.
          </Option>
        )}
      </div>
      {error && <div role="alert" style={{ color: 'var(--red, #b42318)', fontSize: '0.8rem' }}>{error}</div>}
    </Modal>
  )
}

/**
 * `check()` resolves to null (nothing to ask — save as entered), an OwnerChoice (save with the
 * form's customer/vehicle replaced by it) or 'cancel'. Render `dialog` once in the page.
 */
export function useVehicleOwnerCheck() {
  const [mismatch, setMismatch] = useState<OwnerMismatch | null>(null)
  const settle = useRef<((o: Outcome) => void) | null>(null)

  const check = useCallback(async (input: Parameters<typeof findOwnerMismatch>[0]): Promise<Outcome> => {
    const found = await findOwnerMismatch(input)
    if (!found) return null
    return new Promise<Outcome>((resolve) => {
      settle.current = resolve
      setMismatch(found)
    })
  }, [])

  function done(o: Outcome) {
    settle.current?.(o)
    settle.current = null
    setMismatch(null)
  }

  const dialog = mismatch
    ? <VehicleOwnerDialog mismatch={mismatch} onProceed={done} onCancel={() => done('cancel')} />
    : null
  return { check, dialog }
}
