import type { CSSProperties } from 'react'
import type { MasterRow } from '../api/masters'
import { inputStyle } from '../shell/ui'

/**
 * Business status picker, shared by the Cashier's receipt form and the review queues. The
 * options are whatever the server said this role may set — the list is never widened here, so
 * the dropdown and the API can't disagree.
 *
 * rev 65: a deprecated or inactive status is never offered for a new choice (the server leaves
 * them out of the list). A record that already carries one still has to show it, so pass
 * {@code currentName}: when {@code value} is not among the options, that one value is added,
 * marked "(retired)", and nothing else retired is.
 */
export function BusinessStatusSelect(props: {
  statuses: MasterRow[]
  value: number | ''
  onChange: (id: number) => void
  disabled?: boolean
  style?: CSSProperties
  /** The record's own current status name — shown even if that status has since been retired. */
  currentName?: string | null
}) {
  // Defensive: if a caller hands over a deprecated row, only the record's own value may stay.
  const options = props.statuses.filter((s) => !s.deprecated || s.id === props.value)
  const inList = props.value === '' || options.some((s) => s.id === props.value)

  return (
    <select
      value={props.value}
      disabled={props.disabled}
      onChange={(e) => props.onChange(Number(e.target.value))}
      style={props.style ?? inputStyle}
    >
      {!inList && props.currentName && (
        <option value={props.value}>{props.currentName} (retired)</option>
      )}
      {options.map((s) => <option key={s.id} value={s.id}>{s.name}{s.deprecated ? ' (retired)' : ''}</option>)}
    </select>
  )
}
