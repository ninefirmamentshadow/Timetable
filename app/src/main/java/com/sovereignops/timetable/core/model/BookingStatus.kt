package com.sovereignops.timetable.core.model

/**
 * Lifecycle of a booking. Only [occupiesCalendar] statuses hold a slot and can
 * conflict with another booking; a cancelled or no-show slot is free again.
 * Only [COMPLETED] counts toward earned money — a confirmed future booking is
 * projected, not earned.
 */
enum class BookingStatus(val label: String) {
    INQUIRY("Inquiry"),
    CONFIRMED("Confirmed"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    NO_SHOW("No-show");

    /** Does this booking still hold its place on the calendar? */
    val occupiesCalendar: Boolean
        get() = this == INQUIRY || this == CONFIRMED || this == COMPLETED

    /** Has this booking's money actually been earned? */
    val isEarned: Boolean
        get() = this == COMPLETED

    /** Is this a still-open future commitment (drives projected earnings)? */
    val isProjected: Boolean
        get() = this == INQUIRY || this == CONFIRMED
}
