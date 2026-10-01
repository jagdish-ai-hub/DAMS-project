import { useEffect, useState } from 'react'
import { authApi, type SwitchBranchOption, type SwitchOptions } from '../api/auth'
import type { Role } from '../auth/AuthContext'
import { ROLE_LABEL } from '../auth/roleLabels'
import { useAuth } from '../auth/useAuth'
import { Badge, ErrorBanner, Modal, Spinner, ghostBtn, primaryBtn } from './ui'
import { useRoleSwitch } from './useRoleSwitch'

const ROLE_BLURB: Partial<Record<Role, string>> = {
  FINANCE_MANAGER: 'Give final approval and close claims — across every branch.',
  ACCOUNTANT: 'Review, verify and close entries at this branch.',
  CASHIER: 'Record receipts, expenses and cash at this branch.',
}

/**
 * "Switch role" picker (plan.md rev 55): branch first, then a role available there. The server
 * only offers what the Owner granted (an Owner is offered every role at every branch), and it
 * re-checks on the way through — this dialog is a convenience, not the gate.
 */
export default function RoleSwitchModal({ onClose }: { onClose: () => void }) {
  const { user } = useAuth()
  const { switchTo, switchBack, busy, error } = useRoleSwitch()
  const [options, setOptions] = useState<SwitchOptions | null>(null)
  const [loadError, setLoadError] = useState('')
  const [branch, setBranch] = useState<SwitchBranchOption | null>(null)

  useEffect(() => {
    let cancelled = false
    authApi.switchOptions()
      .then(({ data }) => {
        if (cancelled) return
        setOptions(data)
        // Only one place to work → skip straight to picking the role.
        if (data.branches.length === 1) setBranch(data.branches[0])
      })
      .catch((err: unknown) => {
        if (cancelled) return
        setLoadError((err as { response?: { data?: { message?: string } } })?.response?.data?.message
          ?? 'Could not load your role options')
      })
    return () => { cancelled = true }
  }, [])

  if (!user) return null

  async function pick(role: Role) {
    if (!branch) return
    if (await switchTo(role, { id: branch.branchId, code: branch.code })) onClose()
  }

  async function back() {
    if (await switchBack()) onClose()
  }

  const onlyOneBranch = options?.branches.length === 1

  return (
    <Modal
      title="Switch role"
      subtitle={branch ? `Working at ${branch.code} — ${branch.name}` : 'Pick a branch, then the role to work in'}
      onClose={onClose}
      maxWidth={460}
      footer={<button type="button" onClick={onClose} style={ghostBtn}>Cancel</button>}
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {(error || loadError) && <ErrorBanner message={error || loadError} />}

        {user.isActing && (
          <div style={{
            background: 'var(--amber-bg)', border: '1px solid #EAD3AE', borderRadius: 10,
            padding: '12px 14px', display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap',
          }}>
            <div style={{ flex: 1, minWidth: 180, fontSize: '0.82rem' }}>
              You are working as <strong>{ROLE_LABEL[user.role]}</strong>
              {user.actingBranchLabel ? ` · ${user.actingBranchLabel}` : ''}.
            </div>
            <button type="button" onClick={back} disabled={busy} style={primaryBtn(busy)}>
              Return to {ROLE_LABEL[user.primaryRole]}
            </button>
          </div>
        )}

        {!options && !loadError && (
          <div style={{ display: 'flex', justifyContent: 'center', padding: 24 }}><Spinner /></div>
        )}

        {options && options.branches.length === 0 && (
          <div style={{ fontSize: '0.84rem', color: 'var(--muted)' }}>
            No other roles are available to you right now. Ask your Owner to add one in Team &amp; Branches.
          </div>
        )}

        {/* Step 1 — branch */}
        {options && !branch && options.branches.map((b) => (
          <button
            key={b.branchId}
            type="button"
            onClick={() => setBranch(b)}
            style={choiceStyle}
          >
            <span style={{ display: 'flex', flexDirection: 'column', gap: 2, textAlign: 'left', minWidth: 0 }}>
              <span style={{ fontWeight: 700, fontSize: '0.9rem' }}>{b.code}</span>
              <span style={{ fontSize: '0.78rem', color: 'var(--muted)' }}>{b.name}</span>
            </span>
            <span style={{ display: 'flex', gap: 6, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
              {b.roles.map((r) => <Badge key={r} tone="blue">{ROLE_LABEL[r]}</Badge>)}
            </span>
          </button>
        ))}

        {/* Step 2 — role at that branch */}
        {options && branch && (
          <>
            {!onlyOneBranch && (
              <button
                type="button"
                onClick={() => setBranch(null)}
                style={{ ...ghostBtn, alignSelf: 'flex-start' }}
              >
                ← Change branch
              </button>
            )}
            {branch.roles.map((r) => {
              const current = user.isActing && user.role === r && user.actingBranchId === branch.branchId
              return (
                <button
                  key={r}
                  type="button"
                  disabled={busy || current}
                  onClick={() => pick(r)}
                  style={{ ...choiceStyle, opacity: busy || current ? 0.6 : 1, cursor: busy || current ? 'default' : 'pointer' }}
                >
                  <span style={{ display: 'flex', flexDirection: 'column', gap: 2, textAlign: 'left' }}>
                    <span style={{ fontWeight: 700, fontSize: '0.9rem' }}>{ROLE_LABEL[r]}</span>
                    <span style={{ fontSize: '0.78rem', color: 'var(--muted)' }}>{ROLE_BLURB[r]}</span>
                  </span>
                  {current && <Badge tone="green">Current</Badge>}
                </button>
              )
            })}
            <div style={{ fontSize: '0.74rem', color: 'var(--faint)' }}>
              Anything you record is still saved under your own name, {user.name}.
            </div>
          </>
        )}
      </div>
    </Modal>
  )
}

const choiceStyle = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  gap: 12,
  width: '100%',
  background: 'var(--surface)',
  border: '1.5px solid var(--line)',
  borderRadius: 10,
  padding: '12px 14px',
  color: 'var(--ink)',
  cursor: 'pointer',
  minHeight: 48,
} as const
