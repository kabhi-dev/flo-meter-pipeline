package com.flo.nem12.sql

import com.flo.nem12.domain.MeterReading

/**
 * Consumer of [MeterReading]s. [BatchedSqlInsertWriter] emits SQL today; a JDBC
 * or `COPY` sink could be added without touching the parser. Extends
 * [AutoCloseable] so `use { }` flushes any buffered batch.
 */
interface MeterReadingSink : AutoCloseable {
    /** Accept a single reading. Implementations may buffer before emitting. */
    fun write(reading: MeterReading)
}

