package com.flo.nem12.parser

import com.flo.nem12.domain.MeterReading
import com.flo.nem12.domain.NmiInterval
import com.flo.nem12.logging.Log
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Streaming NEM12 parser: maps a lazy `Sequence<String>` of lines to a lazy
 * `Sequence<MeterReading>`, holding only the current line and the current `200`
 * context, so memory stays flat regardless of file size.
 *
 * `300` rows inherit their NMI and interval length from the most recent valid
 * `200` row. All mutable state lives inside a [parse] call, so a single instance
 * is reusable.
 */
class Nem12Parser(
    private val mode: ParsingMode = ParsingMode.STRICT,
) : MeterReadingSource {
    /**
     * Transforms a lazy stream of NEM12 [lines] into a lazy stream of
     * [MeterReading]s. [stats] is updated as the returned sequence is consumed.
     */
    override fun parse(
        lines: Sequence<String>,
        stats: ParsingStats,
    ): Sequence<MeterReading> = sequence {
        var context: NmiInterval? = null
        var lineNumber = 0

        for (rawLine in lines) {
            lineNumber++
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            val fields = line.split(DELIMITER)
            when (RecordType.fromIndicator(fields[0])) {
                RecordType.NMI_DATA_DETAILS ->
                    context = parseContextOrHandle(fields, lineNumber)

                RecordType.INTERVAL_DATA -> {
                    val readings = expandOrHandle(fields, context, lineNumber, stats)
                    if (readings != null) {
                        stats.dataRecords++
                        stats.readingsEmitted += readings.size
                        yieldAll(readings)
                    }
                }

                // Recognised but unused here.
                RecordType.HEADER,
                RecordType.INTERVAL_EVENT,
                RecordType.B2B_DETAILS,
                RecordType.END_OF_DATA -> Unit

                // Unknown indicator: skip rather than fail.
                null -> Log.debug { "Line $lineNumber: ignoring unknown record type '${fields[0]}'" }
            }
        }
    }

    /**
     * Parses a `200` record into an [NmiInterval]. In LENIENT mode a bad record
     * returns `null` so later `300` rows aren't attributed to a stale NMI; STRICT
     * mode rethrows.
     */
    private fun parseContextOrHandle(fields: List<String>, lineNumber: Int): NmiInterval? =
        try {
            parseContext(fields, lineNumber)
        } catch (e: Nem12ParseException) {
            if (mode == ParsingMode.STRICT) throw e
            Log.warn { "${e.message} — dropping NMI context; subsequent 300 records will be skipped until the next valid 200" }
            null
        }

    private fun parseContext(fields: List<String>, lineNumber: Int): NmiInterval {
        if (fields.size <= INTERVAL_LENGTH_INDEX) {
            throw Nem12ParseException(
                lineNumber,
                "200 record has ${fields.size} fields; need at least ${INTERVAL_LENGTH_INDEX + 1}",
            )
        }
        val nmi = fields[NMI_INDEX].trim()
        val intervalLength = fields[INTERVAL_LENGTH_INDEX].trim().toIntOrNull()
            ?: throw Nem12ParseException(
                lineNumber,
                "200 record has non-numeric interval length '${fields[INTERVAL_LENGTH_INDEX]}'",
            )
        return try {
            NmiInterval(nmi, intervalLength)
        } catch (e: IllegalArgumentException) {
            throw Nem12ParseException(lineNumber, e.message ?: "invalid 200 record", e)
        }
    }

    /**
     * Expands a `300` record into per-interval readings, or `null` if the record
     * is skipped in LENIENT mode. All-or-nothing: a partially corrupt row never
     * yields a subset of its readings.
     */
    private fun expandOrHandle(
        fields: List<String>,
        context: NmiInterval?,
        lineNumber: Int,
        stats: ParsingStats,
    ): List<MeterReading>? =
        try {
            val ctx = context ?: throw Nem12ParseException(
                lineNumber,
                "300 record encountered before a valid 200 record",
            )
            expandIntervalRecord(fields, ctx, lineNumber)
        } catch (e: Nem12ParseException) {
            if (mode == ParsingMode.STRICT) throw e
            stats.skippedRecords++
            Log.warn { "${e.message} — skipping record" }
            null
        }

    private fun expandIntervalRecord(
        fields: List<String>,
        ctx: NmiInterval,
        lineNumber: Int,
    ): List<MeterReading> {
        val firstValueIndex = INTERVAL_DATE_INDEX + 1
        val requiredFields = firstValueIndex + ctx.intervalsPerDay
        if (fields.size < requiredFields) {
            throw Nem12ParseException(
                lineNumber,
                "300 record has ${fields.size} fields; need at least $requiredFields " +
                    "for ${ctx.intervalLengthMinutes}min data (${ctx.intervalsPerDay} intervals)",
            )
        }

        val date = parseIntervalDate(fields[INTERVAL_DATE_INDEX].trim(), lineNumber)

        val readings = ArrayList<MeterReading>(ctx.intervalsPerDay)
        for (i in 0 until ctx.intervalsPerDay) {
            val raw = fields[firstValueIndex + i].trim()
            val consumption = raw.toBigDecimalOrNull()
                ?: throw Nem12ParseException(
                    lineNumber,
                    "non-numeric consumption '$raw' at interval ${i + 1}",
                )
            readings += MeterReading(ctx.nmi, ctx.timestampAt(date, i), consumption)
        }
        return readings
    }

    private fun parseIntervalDate(raw: String, lineNumber: Int): LocalDate =
        try {
            LocalDate.parse(raw, DATE_FORMAT)
        } catch (e: Exception) {
            throw Nem12ParseException(lineNumber, "invalid interval date '$raw' (expected yyyyMMdd)", e)
        }

    companion object {
        private const val DELIMITER = ","

        // Field positions (0-based) within their record types.
        private const val NMI_INDEX = 1            // 200 record
        private const val INTERVAL_LENGTH_INDEX = 8 // 200 record (the "ninth value")
        private const val INTERVAL_DATE_INDEX = 1   // 300 record

        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    }
}

