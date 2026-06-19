package com.flo.nem12.pipeline

import com.flo.nem12.parser.ParsingMode
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.GZIPInputStream
import kotlin.io.path.createTempDirectory
import kotlin.io.path.inputStream
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Nem12PipelineTest {

    private val samplePath: Path = Paths.get(javaClass.getResource("/nem12/sample.csv")!!.toURI())
    private val tmp = createTempDirectory("nem12-pipeline-test")

    @Test
    fun `converts the provided NEM12 sample end-to-end`() {
        val output = tmp.resolve("out.sql")
        val result = Nem12Pipeline(PipelineConfig(input = samplePath, output = output)).run()

        // 2 NMIs x 4 days = 8 interval (300) records; each is 48 half-hours.
        assertEquals(8, result.dataRecords)
        assertEquals(8 * 48, result.readingsWritten)
        assertEquals(0, result.skippedRecords)
        assertEquals(1, result.statementsWritten) // 384 rows fit in one default 1000-batch

        val sql = output.readText()
        assertEquals(192, Regex("\\('NEM1201009', ").findAll(sql).count())
        assertEquals(192, Regex("\\('NEM1201010', ").findAll(sql).count())

        // First 12 half-hours are zero; value 13 (index 12 -> 06:00) is 0.461.
        assertTrue(sql.contains("('NEM1201009', '2005-03-01 00:00:00', 0)"), "missing midnight zero reading")
        assertTrue(sql.contains("('NEM1201009', '2005-03-01 06:00:00', 0.461)"), "missing 06:00 reading")

        // Structural sanity.
        assertTrue(sql.startsWith("INSERT INTO meter_readings (\"nmi\", \"timestamp\", \"consumption\") VALUES\n"))
        assertTrue(sql.trimEnd().endsWith("DO NOTHING;"))
    }

    @Test
    fun `gzip output round-trips`() {
        val output = tmp.resolve("out.sql.gz")
        Nem12Pipeline(PipelineConfig(input = samplePath, output = output)).run()

        val sql = GZIPInputStream(output.inputStream()).bufferedReader().use { it.readText() }
        assertEquals(384, Regex("\\('NEM1201").findAll(sql).count())
    }

    @Test
    fun `small batch size splits output into multiple statements`() {
        val output = tmp.resolve("batched.sql")
        val result = Nem12Pipeline(
            PipelineConfig(input = samplePath, output = output, batchSize = 50),
        ).run()

        // ceil(384 / 50) = 8 statements.
        assertEquals(8, result.statementsWritten)
        assertEquals(8, Regex("INSERT INTO").findAll(output.readText()).count())
    }

    @Test
    fun `lenient mode tolerates a corrupt record mid-file`() {
        val corrupt = tmp.resolve("corrupt.csv")
        corrupt.toFile().writeText(
            buildString {
                appendLine("200,NEM1201009,E1E2,1,E1,N1,01009,kWh,30,20050610")
                appendLine("300,20050301," + (1..48).joinToString(",") { "1" } + ",A") // valid
                appendLine("300,BADDATE," + (1..48).joinToString(",") { "1" } + ",A")   // invalid date
                appendLine("300,20050303," + (1..48).joinToString(",") { "1" } + ",A") // valid
            },
        )
        val output = tmp.resolve("lenient.sql")
        val result = Nem12Pipeline(
            PipelineConfig(input = corrupt, output = output, mode = ParsingMode.LENIENT),
        ).run()

        assertEquals(2, result.dataRecords)
        assertEquals(96, result.readingsWritten)
        assertEquals(1, result.skippedRecords)
    }
}




