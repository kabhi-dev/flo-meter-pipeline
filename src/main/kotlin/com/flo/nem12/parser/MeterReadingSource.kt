package com.flo.nem12.parser

import com.flo.nem12.domain.MeterReading

/**
 * Produces a lazy stream of [MeterReading]s from raw input lines, updating the
 * supplied [ParsingStats] as the stream is consumed. The counterpart of
 * `MeterReadingSink`: a pipeline pulls from a source and pushes into a sink, so
 * either side can be swapped (e.g. a different input format) without changing the
 * other.
 */
interface MeterReadingSource {
    fun parse(
        lines: Sequence<String>,
        stats: ParsingStats = ParsingStats(),
    ): Sequence<MeterReading>
}


