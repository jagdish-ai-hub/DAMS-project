import type { CSSProperties } from 'react'
import type { MasterRow } from '../api/masters'
import { inputStyle } from '../shell/ui'

/**
 * Job-card business status picker, shared by the Cashier's receipt form and the two
 * review queues. The options are whatever the server said this role may set — the list is
 * never filtered here, so the dropdown and the API can't disagree.
 *
 * Statuses that predate role mapping sit under a "Deprecated" group at the bottom: still
 * pickable for anyone mid-process on one, but out of the way of the live list.
 */
export function BusinessStatusSelect(props: {
  statuses: MasterRow[]
  value: number | ''
  onChange: (id: number) => void
  disabled?: boolean
  style?: CSSProperties
}) {
  const live = props.statuses.filter((s) => !s.deprecated)
  const retired = props.statuses.filter((s) => s.deprecated)

  return (
    <select
      value={props.value}
      disabled={props.disabled}
      onChange={(e) => props.onChange(Number(e.target.value))}
      style={props.style ?? inputStyle}
    >
      {live.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
      {retired.length > 0 && (
        <optgroup label="Deprecated">
          {retired.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
        </optgroup>
      )}
    </select>
  )
}
