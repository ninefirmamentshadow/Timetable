package com.sovereignops.timetable.ui

/** One-shot messages from the view model to the activity. */
sealed interface UiEvent {

    enum class Kind { BOOKING, AVAILABILITY_WINDOW }

    /**
     * Something was deleted and can be put back until [undoToken] is used.
     * [label] is what to name it in the message (an alias or a weekday).
     */
    data class Deleted(val kind: Kind, val label: String, val undoToken: Long) : UiEvent

    /** A storage operation failed; [reason] is shown to the operator. Nothing is retried silently. */
    data class Failed(val reason: String) : UiEvent
}
