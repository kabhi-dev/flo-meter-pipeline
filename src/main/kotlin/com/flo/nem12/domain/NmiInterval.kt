package com.flo.nem12.domain

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * NMI and interval length carried by a `200` record. The following `300` records
 * inherit this context to expand their interval values into [MeterReading]s.
 *
 * @param nmi National Metering Identifier (max 10 chars per the schema).
 * @param intervalLengthMinutes minutes per interval value; must divide a day exactly.
 */
data class NmiInterval(
    val nmi: String,
    val intervalLengthMinutes: Int,
) {
    init {
        require(nmi.isNotBlank()) { "NMI must not be blank" }
        require(nmi.length <= MAX_NMI_LENGTH) {
            "NMI '$nmi' exceeds max length $MAX_NMI_LENGTH"
        }
        require(intervalLengthMinutes in 1..MINUTES_PER_DAY) {
            "Interval length must be between 1 and $MINUTES_PER_DAY minutes, was $intervalLengthMinutes"
        }
        require(MINUTES_PER_DAY % intervalLengthMinutes == 0) {
            "Interval length $intervalLengthMinutes does not divide a 24h day evenly"
        }
    }

    /** Number of interval values expected on each `300` record for this NMI. */
    val intervalsPerDay: Int = MINUTES_PER_DAY / intervalLengthMinutes

    /**
     * Timestamp for the interval at [index] (0-based) on [date], taken as the
     * interval start: 30-minute data runs 00:00, 00:30 … 23:30. NEM12 also allows
     * interval-ending; this method is the one place to change that convention.
     */
    fun timestampAt(date: LocalDate, index: Int): LocalDateTime {
        require(index in 0 until intervalsPerDay) {
            "Interval index $index out of range [0, $intervalsPerDay) for ${intervalLengthMinutes}min data"
        }
        return date.atStartOfDay().plusMinutes(index.toLong() * intervalLengthMinutes)
    }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
        const val MAX_NMI_LENGTH = 10
    }
}

