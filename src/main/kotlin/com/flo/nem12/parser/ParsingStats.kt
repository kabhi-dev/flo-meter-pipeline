package com.flo.nem12.parser

/**
 * Counters for one parse run. Parsing is lazy, so these are final only once the
 * sequence has been fully consumed. Single-threaded by design, so plain `var`s
 * are enough.
 */
class ParsingStats {
    var dataRecords: Long = 0L
        internal set
    var readingsEmitted: Long = 0L
        internal set
    var skippedRecords: Long = 0L
        internal set

    override fun toString(): String =
        "ParsingStats(dataRecords=$dataRecords, readingsEmitted=$readingsEmitted, skippedRecords=$skippedRecords)"
}

