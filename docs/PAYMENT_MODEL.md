# Timetable payment model

What the app counts as money, what it does not, and where its limits are. The code is in `core/earnings/Earnings.kt` and `core/model/Booking.kt`; the tests are `CashRecordedTest` and `EarningsTest`.

## Three figures, never added together

| Figure | What it is | Where it comes from |
| --- | --- | --- |
| **Completed** (booked value) | The rate of bookings marked COMPLETED | `rate` of COMPLETED rows. It is not cash. |
| **Cash recorded** | Money you entered as received | `amountReceived` of any booking, whatever its status (a no-show fee counts) |
| **Projected** | Open commitments that have not ended | `rate` of INQUIRY / CONFIRMED rows still in the future |

An open booking whose time has passed is in none of these. It is counted as **Needs review** until you mark it completed, no-show or cancelled.

## Received amount

- **Empty (null) means unknown.** Nothing is assumed paid. An old booking, a completed booking and a confirmed booking with no amount all read as "not recorded".
- **Zero is a recorded zero** ("nothing was received"), not the same as empty.
- **Never negative.** The editor rejects a negative amount, and a stored negative row is skipped and counted, not deleted.
- **A status never creates cash.** Marking a booking COMPLETED does not fill in the amount.
- **The count of completed bookings with no payment recorded** is shown next to the figures, so an unpaid booking is visible and not silently counted.

## What the received amount means

**Unrestricted cash actually received.** Enter what you received and kept:

- Platform credit, a balance you cannot withdraw, or a promised payment is **not** cash. Leave the field empty until it is real cash.
- If you refunded part of a payment, enter what you **kept** (edit the amount down). The app has no separate refund field.
- Expenses and hours are not recorded here.

## Limits of this release

- Timetable has **no separate field for refunds, chargebacks, restricted credit, expenses or labor hours.** A payment is one number per booking. The Weekly Scorecard (`tools/weekly-scorecard.html`) is where those are recorded: it computes net cash = unrestricted cash received − refunds/chargebacks − attributable cash expenses, divides by all labor hours, and shows restricted credit apart. Timetable's help text tells you not to enter credit, but the app cannot check it.
- No payment categories (deposit, tip, fee) exist. One amount per booking.
- Cash recorded is not a tax, income or accounting figure.

## The advance-payment note

`depositReceived` is a historical field name. It is shown as **Advance payment received (optional note)**, is off by default, and nothing reads it: not Earnings, not the conflict checks, not validation, not status changes (`theAdvancePaymentNoteChangesNoFigure`, `theAdvancePaymentNoteIsOptionalAndNeverBlocksOrChangesAValidation`). The app has no deposit policy, and no screen requires, suggests or enforces one. The field stays so stored rows and backups remain readable; it is not exported to `.ics`.

## Storage and backup

`rateCents` and `receivedCents` are integer cents (`received` is nullable). Both are in the encrypted backup (payload schema 2; schema 1 restores with "not recorded"). Neither is exported to the calendar file.
