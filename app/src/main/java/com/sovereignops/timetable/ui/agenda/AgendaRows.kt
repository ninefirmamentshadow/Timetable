package com.sovereignops.timetable.ui.agenda

import com.sovereignops.timetable.core.schedule.AgendaSections

/** Pure shaping of [AgendaSections] into the flat row list the adapter shows. */
object AgendaRows {

    fun build(sections: AgendaSections, title: (AgendaSection) -> String): List<AgendaRow> = buildList {
        if (sections.needsReview.isNotEmpty()) {
            add(AgendaRow.SectionHeader(AgendaSection.NEEDS_REVIEW, title(AgendaSection.NEEDS_REVIEW)))
            sections.needsReview.forEach { add(AgendaRow.Item(AgendaSection.NEEDS_REVIEW, it)) }
        }
        if (sections.upcoming.isNotEmpty()) {
            add(AgendaRow.SectionHeader(AgendaSection.UPCOMING, title(AgendaSection.UPCOMING)))
            sections.upcoming.forEach { day ->
                add(AgendaRow.DayHeader(AgendaSection.UPCOMING, day.date))
                day.bookings.forEach { add(AgendaRow.Item(AgendaSection.UPCOMING, it)) }
            }
        }
        if (sections.past.isNotEmpty()) {
            add(AgendaRow.SectionHeader(AgendaSection.PAST, title(AgendaSection.PAST)))
            sections.past.forEach { day ->
                add(AgendaRow.DayHeader(AgendaSection.PAST, day.date))
                day.bookings.forEach { add(AgendaRow.Item(AgendaSection.PAST, it)) }
            }
        }
    }
}
