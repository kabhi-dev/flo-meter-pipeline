package com.flo.nem12.domain

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * One row destined for the `meter_readings` table.
 *
 * [consumption] is a [BigDecimal] to match the exact `numeric` column — a
 * [Double] would corrupt billing values. [timestamp] is a [LocalDateTime]
 * because NEM12 interval data is zoneless. `id` is omitted: the database
 * generates it via `gen_random_uuid()`. The business key is ([nmi], [timestamp]).
 */
data class MeterReading(
    val nmi: String,
    val timestamp: LocalDateTime,
    val consumption: BigDecimal,
)

