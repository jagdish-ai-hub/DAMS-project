/** Shared "Overridden · Final" marking (AGENT.md closing rule #3): the Finance Manager
 *  closed the claim at a final amount different from what was received. It is permanent and
 *  must be visibly marked everywhere the record is shown — FM queue, My Entries, customer
 *  history, search results. One component, one wording, everywhere. */
export default function ClaimFinalBadge({ finalAmount }: { finalAmount?: number | null }) {
  return (
    <span
      title={
        finalAmount != null
          ? `Closed by the Finance Manager at a final ₹${finalAmount} — different from the amount received`
          : 'Closed by the Finance Manager at a final amount different from what was received'
      }
      style={{
        fontSize: '0.66rem', fontWeight: 800, whiteSpace: 'nowrap',
        color: 'var(--purple, #6B3FA0)', background: 'var(--purple-bg, #EFE7FB)',
        borderRadius: 999, padding: '2px 8px',
      }}
    >
      Overridden · Final
    </span>
  )
}
