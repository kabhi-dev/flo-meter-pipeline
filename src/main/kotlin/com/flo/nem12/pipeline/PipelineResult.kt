package com.flo.nem12.pipeline

import com.flo.nem12.parser.ParsingStats

/**
 * Outcome of a pipeline run. Returned rather than printed, so the CLI can log a
 * summary and tests can assert on the numbers.
 */
data class PipelineResult(
    val dataRecords: Long,
    val readingsWritten: Long,
    val statementsWritten: Long,
    val skippedRecords: Long,
    val durationMillis: Long,
) {
    fun summary(): String = buildString {
        append("Done in ${durationMillis} ms — ")
        append("300-records=$dataRecords, ")
        append("readings=$readingsWritten, ")
        append("insert-statements=$statementsWritten, ")
        append("skipped=$skippedRecords")
    }

    companion object {
        fun of(stats: ParsingStats, statementsWritten: Long, durationMillis: Long) =
            PipelineResult(
                dataRecords = stats.dataRecords,
                readingsWritten = stats.readingsEmitted,
                statementsWritten = statementsWritten,
                skippedRecords = stats.skippedRecords,
                durationMillis = durationMillis,
            )
    }
}

