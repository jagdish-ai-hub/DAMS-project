import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { vi } from 'vitest'
import { AuthProvider } from '../auth/AuthContext'
import type { MyEntry } from '../api/myEntries'
import MyEntriesPage, { badgeTone } from './MyEntriesPage'

vi.mock('../api/myEntries', () => ({
  myEntriesApi: { list: vi.fn() },
}))

import { myEntriesApi } from '../api/myEntries'

const base: MyEntry = {
  id: 1,
  kind: 'RECEIPT',
  documentNo: 'OOR-AUG26-R-005',
  workflowStatus: 'SUBMITTED',
  settled: false,
  claimOverridden: false,
  jobCardId: 11,
  jobCardReference: 'OOR-JC-11',
  partyName: 'Sharma Transport',
  total: 8000,
  pendingAmount: 3133,
  lineCount: 2,
  overLimit: false,
  today: true,
  queried: false,
  createdAt: '2026-08-20T05:00:00Z',
  submittedAt: '2026-08-20T06:00:00Z',
}

function renderPage(entries: MyEntry[]) {
  vi.mocked(myEntriesApi.list).mockResolvedValue({ data: entries } as never)
  return render(
    <MemoryRouter>
      <AuthProvider>
        <MyEntriesPage />
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('badgeTone', () => {
  it('marks queried entries amber so they stand out', () => {
    expect(badgeTone({ ...base, queried: true, workflowStatus: 'QUERIED' })).toBe('amber')
  })

  it('marks rejections red', () => {
    expect(badgeTone({ ...base, workflowStatus: 'REJECTED' })).toBe('red')
  })

  it('marks settled / approved / closed green', () => {
    expect(badgeTone({ ...base, settled: true })).toBe('green')
    expect(badgeTone({ ...base, workflowStatus: 'APPROVED' })).toBe('green')
    expect(badgeTone({ ...base, workflowStatus: 'CLOSED' })).toBe('green')
  })

  it('leaves in-flight entries gray', () => {
    expect(badgeTone({ ...base, workflowStatus: 'SUBMITTED' })).toBe('gray')
    expect(badgeTone({ ...base, workflowStatus: 'VERIFIED' })).toBe('gray')
  })
})

describe('My Entries fix-and-resubmit loop', () => {
  it('offers Fix & Resubmit on queried items, Open elsewhere', async () => {
    renderPage([
      { ...base, id: 1, queried: true, workflowStatus: 'QUERIED' },
      { ...base, id: 2, queried: false, workflowStatus: 'SUBMITTED' },
    ])

    await waitFor(() => expect(screen.getByText('Fix & Resubmit')).toBeInTheDocument())
    expect(screen.getByText('Open')).toBeInTheDocument()
  })

  it('shows Overridden · Final on FM-closed claims and SETTLED on paid receipts', async () => {
    renderPage([
      { ...base, id: 3, claimOverridden: true, workflowStatus: 'APPROVED' },
      { ...base, id: 4, settled: true, workflowStatus: 'APPROVED' },
    ])

    await waitFor(() => expect(screen.getByText('Overridden · Final')).toBeInTheDocument())
    expect(screen.getByText('SETTLED')).toBeInTheDocument()
  })

  it('flags over-limit expenses inline', async () => {
    renderPage([{ ...base, id: 5, kind: 'EXPENSE', overLimit: true }])

    await waitFor(() => expect(screen.getByText('⚠ over limit')).toBeInTheDocument())
  })
})
