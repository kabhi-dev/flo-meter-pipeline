package com.flo.nem12.sql

import com.flo.nem12.domain.MeterReading
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BatchedSqlInsertWriterTest {

    private fun reading(nmi: String = "NEM1201009", ts: String = "2005-03-01T00:00:00", consumption: String = "0.461") =
        MeterReading(nmi, LocalDateTime.parse(ts), BigDecimal(consumption))

    @Test
    fun `single reading produces a complete insert with default conflict clause`() {
        val sb = StringBuilder()
        BatchedSqlInsertWriter(sb).use { it.write(reading()) }

        val expected = """
            INSERT INTO meter_readings ("nmi", "timestamp", "consumption") VALUES
              ('NEM1201009', '2005-03-01 00:00:00', 0.461)
            ON CONFLICT ("nmi", "timestamp") DO NOTHING;

        """.trimIndent()
        assertEquals(expected, sb.toString())
    }

    @Test
    fun `rows are folded into batches of batchSize`() {
        val sb = StringBuilder()
        val writer = BatchedSqlInsertWriter(sb, batchSize = 2)
        writer.use {
            it.write(reading(ts = "2005-03-01T00:00:00"))
            it.write(reading(ts = "2005-03-01T00:30:00"))
            it.write(reading(ts = "2005-03-01T01:00:00"))
        }

        // 3 rows, batch size 2 -> two INSERT statements (2 rows + 1 row).
        assertEquals(3, writer.rowsWritten)
        assertEquals(2, writer.statementsWritten)
        assertEquals(2, Regex("INSERT INTO").findAll(sb).count())
    }

    @Test
    fun `partial final batch is flushed on close`() {
        val sb = StringBuilder()
        val writer = BatchedSqlInsertWriter(sb, batchSize = 1000)
        writer.use { it.write(reading()) }

        assertEquals(1, writer.statementsWritten)
        assertTrue(sb.endsWith(";\n"))
    }

    @Test
    fun `single quotes in nmi are escaped`() {
        val sb = StringBuilder()
        BatchedSqlInsertWriter(sb).use { it.write(reading(nmi = "O'BRIEN")) }
        assertTrue(sb.contains("('O''BRIEN',"), "expected doubled single quote, got: $sb")
    }

    @Test
    fun `do-update strategy emits an upsert clause`() {
        val sb = StringBuilder()
        BatchedSqlInsertWriter(sb, conflictStrategy = ConflictStrategy.DO_UPDATE).use { it.write(reading()) }
        assertTrue(
            sb.contains("""ON CONFLICT ("nmi", "timestamp") DO UPDATE SET "consumption" = EXCLUDED."consumption";"""),
            sb.toString(),
        )
    }

    @Test
    fun `error strategy emits no on-conflict clause`() {
        val sb = StringBuilder()
        BatchedSqlInsertWriter(sb, conflictStrategy = ConflictStrategy.ERROR).use { it.write(reading()) }
        assertTrue(!sb.contains("ON CONFLICT"), sb.toString())
        assertTrue(sb.trimEnd().endsWith(");"), sb.toString())
    }

    @Test
    fun `consumption is rendered in plain decimal, never scientific notation`() {
        val sb = StringBuilder()
        // 1E+1 would be the default toString of this BigDecimal.
        BatchedSqlInsertWriter(sb).use { it.write(reading(consumption = "1E+1")) }
        assertTrue(sb.contains(", 10)"), sb.toString())
    }
}

