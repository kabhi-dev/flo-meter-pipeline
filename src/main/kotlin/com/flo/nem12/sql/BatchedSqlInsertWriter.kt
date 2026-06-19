package com.flo.nem12.sql

import com.flo.nem12.domain.MeterReading
import java.time.format.DateTimeFormatter

/**
 * Writes [MeterReading]s as batched, multi-row SQL `INSERT`s to an [Appendable]
 * (a file [java.io.Writer], `System.out`, or a `StringBuilder` in tests).
 *
 * Folding [batchSize] rows into one `INSERT ... VALUES (...),(...),...` avoids the
 * per-statement overhead of millions of single-row inserts. Rows are appended as
 * they arrive — the writer never holds more than the current statement — so with
 * the streaming parser the pipeline runs in constant memory. String literals are
 * single-quote escaped. Not thread-safe.
 *
 * @param out destination for generated SQL.
 * @param batchSize maximum rows per `INSERT` statement (must be >= 1).
 * @param conflictStrategy how to resolve unique-key clashes.
 */
class BatchedSqlInsertWriter(
    private val out: Appendable,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val conflictStrategy: ConflictStrategy = ConflictStrategy.DO_NOTHING,
) : MeterReadingSink {

    init {
        require(batchSize >= 1) { "batchSize must be >= 1, was $batchSize" }
    }

    private var rowsInBatch = 0

    /** Total rows written across all statements. */
    var rowsWritten: Long = 0L
        private set

    /** Total `INSERT` statements emitted. */
    var statementsWritten: Long = 0L
        private set

    override fun write(reading: MeterReading) {
        if (rowsInBatch == 0) {
            out.append(INSERT_HEADER)
        } else {
            out.append(",\n")
        }
        appendValuesTuple(reading)
        rowsInBatch++
        rowsWritten++

        if (rowsInBatch == batchSize) {
            finishBatch()
        }
    }

    override fun close() {
        if (rowsInBatch > 0) {
            finishBatch()
        }
    }

    private fun finishBatch() {
        out.append(conflictClause)
        statementsWritten++
        rowsInBatch = 0
    }

    private fun appendValuesTuple(reading: MeterReading) {
        out.append("  ('")
        appendEscaped(reading.nmi)
        out.append("', '")
        out.append(reading.timestamp.format(TIMESTAMP_FORMAT))
        out.append("', ")
        // Plain string keeps the value exact and out of scientific notation (1E+1).
        out.append(reading.consumption.toPlainString())
        out.append(')')
    }

    private fun appendEscaped(value: String) {
        // Most values have no quote, so avoid allocating a replaced copy.
        if (value.indexOf('\'') < 0) {
            out.append(value)
        } else {
            out.append(value.replace("'", "''"))
        }
    }

    private val conflictClause: String = when (conflictStrategy) {
        ConflictStrategy.DO_NOTHING ->
            "\nON CONFLICT ($CONFLICT_TARGET) DO NOTHING;\n"
        ConflictStrategy.DO_UPDATE ->
            "\nON CONFLICT ($CONFLICT_TARGET) DO UPDATE SET \"consumption\" = EXCLUDED.\"consumption\";\n"
        ConflictStrategy.ERROR ->
            ";\n"
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 1_000
        private const val CONFLICT_TARGET = "\"nmi\", \"timestamp\""

        private const val INSERT_HEADER =
            "INSERT INTO meter_readings (\"nmi\", \"timestamp\", \"consumption\") VALUES\n"

        private val TIMESTAMP_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}


