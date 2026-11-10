import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MyEntriesPage from './MyEntriesPage'
import { myEntriesApi, type MyEntry } from '../api/myEntries'

vi.mock('../api/myEntries', () => ({ myEntriesApi: { list: vi.fn(), inbox: vi.fn() } }))

const entry = (id: number): MyEntry => ({
  id, kind: 'EXPENSE', documentNo: `E-${id}`, workflowStatus: 'CLOSED', settled: false, jobCardId: null,
  jobCardReference: null, partyName: `Vendor ${id}`, total: 10, pendingAmount: null, lineCount: 1, overLimit: false,
  today: false, queried: false, createdAt: '2026-11-01T00:00:00Z', submittedAt: null, preApprovalStatus: null, preApprovalCovers: false,
})
const rows = (n: number) => Array.from({ length: n }, (_, i) => entry(i + 1))

describe('My Entries paging and dates (rev 92)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(myEntriesApi.list).mockImplementation((async (o: { limit?: number } = {}) =>
      ({ data: rows(Math.min(o.limit ?? 50, 120)) })) as never)
  })

  it('"Load more" asks for 50 more rows; the button goes away when everything is shown', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter><MyEntriesPage /></MemoryRouter>)
    await screen.findByText('Showing 50 entries — more available')
    expect(myEntriesApi.list).toHaveBeenLastCalledWith({ limit: 50, from: '', to: '' })

    await user.click(screen.getByRole('button', { name: 'Load more' }))
    await screen.findByText('Showing 100 entries — more available')
    await user.click(screen.getByRole('button', { name: 'Load more' }))
    await screen.findByText('Showing 120 entries')
    expect(screen.queryByRole('button', { name: 'Load more' })).not.toBeInTheDocument()
  })

  it('a date range is sent to the server and starts again from the first page', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter><MyEntriesPage /></MemoryRouter>)
    await screen.findByText(/Showing 50/)
    await user.click(screen.getByRole('button', { name: 'Load more' }))
    await screen.findByText(/Showing 100/)

    await user.type(screen.getByLabelText('From date'), '2026-10-02')
    await waitFor(() => expect(myEntriesApi.list).toHaveBeenLastCalledWith({ limit: 50, from: '2026-10-02', to: '' }))
    expect(screen.getByRole('button', { name: 'Clear dates' })).toBeInTheDocument()
  })
})
