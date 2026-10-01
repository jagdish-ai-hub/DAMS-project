# Cashier home message boxes (rev 57)

Left box "Queries for me": documents the Accountant / Finance Manager queried or rejected (click opens the doc).
Right box "Approvals": expenses sent to the FM for pre-approval and the replies (waiting / approved / queried).
Badges = items still needing the cashier; derived from current document state (no read tracking), so resubmitting lowers the count.

Decisions: Rejected shown for 7 days but not counted; unread-style badges; FM_QUERIED excluded (Accountant's).
Backend: CashierInboxService + GET /api/v1/my-entries/inbox (CASHIER). Frontend: cashier/InboxBoxes.tsx in CashierHomePage side gutters (xl+, stacked below).
Tests: CashierInboxServiceTest.
