package com.pressureagent.mobile.data.local

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes events to the Android system calendar.
 *
 * Requires WRITE_CALENDAR permission (already declared in AndroidManifest).
 * On first use, the user will be prompted to grant the permission at runtime.
 */
@Singleton
class CalendarHelper @Inject constructor(
    private val context: Context,
) {

    /**
     * Create a calendar event and return the event ID.
     *
     * @param title Event title
     * @param location Optional location
     * @param startTime ISO-8601 datetime string
     * @param durationMinutes Duration in minutes (default 60)
     * @param description Optional description
     * @return The event ID if successful, null otherwise
     */
    fun createEvent(
        title: String,
        location: String? = null,
        startTime: String? = null,
        durationMinutes: Int = 60,
        description: String? = null,
    ): Long? {
        val cr: ContentResolver = context.contentResolver

        // Find the primary calendar
        val calendarId = getPrimaryCalendarId(cr) ?: return null

        val startMillis = if (startTime != null) {
            try {
                val zdt = ZonedDateTime.parse(startTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                zdt.toInstant().toEpochMilli()
            } catch (_: Exception) {
                System.currentTimeMillis() + 3600_000 // fallback: 1 hour from now
            }
        } else {
            System.currentTimeMillis() + 3600_000 // default: 1 hour from now
        }

        val endMillis = startMillis + (durationMinutes * 60_000L)

        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.TITLE, title)
            if (location != null) put(CalendarContract.Events.EVENT_LOCATION, location)
            if (description != null) put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
        }

        val uri = cr.insert(CalendarContract.Events.CONTENT_URI, values)
        return uri?.let { ContentUris.parseId(it) }
    }

    /**
     * Delete an event by ID. Returns true if successful.
     */
    fun deleteEvent(eventId: Long): Boolean {
        val cr: ContentResolver = context.contentResolver
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        return cr.delete(uri, null, null) > 0
    }

    private fun getPrimaryCalendarId(cr: ContentResolver): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
        )
        val cursor = cr.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.IS_PRIMARY} = 1",
            null,
            null,
        )
        return cursor?.use {
            if (it.moveToFirst()) it.getLong(0) else null
        }
    }
}
