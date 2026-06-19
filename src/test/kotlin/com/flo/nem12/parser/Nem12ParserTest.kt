package com.flo.nem12.parser

import com.flo.nem12.domain.MeterReading
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Nem12ParserTest {

    // --- helpers --------------------------------------------------------------

    private fun line200(nmi: String, intervalMinutes: Int) =
        "200,$nmi,E1E2,1,E1,N1,01009,kWh,$intervalMinutes,20050610"

    /** Builds a 300 record with [values] consumption fields plus a trailing quality flag. */
    private fun line300(date: String, values: List<String>) =
        (listOf("300", date) + values + listOf("A")).joinToString(",")

    private fun reading(nmi: String, ts: String, consumption: String) =
        MeterReading(nmi, LocalDateTime.parse(ts), BigDecimal(consumption))

    private fun parse(vararg lines: String, mode: ParsingMode = ParsingMode.STRICT): List<MeterReading> =
        Nem12Parser(mode).parse(lines.asSequence()).toList()

    // --- happy path -----------------------------------------------------------

    @Test
    fun `expands a 30-minute 300 record into 48 readings`() {
        val values = (1..48).map { "0.${it.toString().padStart(3, '0')}" }
        val readings = parse(line200("NEM1201009", 30), line300("20050301", values))

        assertEquals(48, readings.size)
        // First interval starts at midnight; second at 00:30; last at 23:30.
        assertEquals(reading("NEM1201009", "2005-03-01T00:00:00", "0.001"), readings.first())
        assertEquals(reading("NEM1201009", "2005-03-01T00:30:00", "0.002"), readings[1])
        assertEquals(reading("NEM1201009", "2005-03-01T23:30:00", "0.048"), readings.last())
    }

    @Test
    fun `15-minute data yields 96 readings`() {
        val values = (1..96).map { "0.5" }
        val readings = parse(line200("NEM1201009", 15), line300("20050301", values))

        assertEquals(96, readings.size)
        assertEquals(LocalDateTime.parse("2005-03-01T00:15:00"), readings[1].timestamp)
        assertEquals(LocalDateTime.parse("2005-03-01T23:45:00"), readings.last().timestamp)
    }

    @Test
    fun `5-minute data yields 288 readings`() {
        val readings = parse(line200("NEM1201009", 5), line300("20050301", List(288) { "1" }))
        assertEquals(288, readings.size)
    }

    @Test
    fun `consumption is preserved exactly as BigDecimal`() {
        val readings = parse(line200("NEM1201009", 30), line300("20050301", List(48) { "0.461" }))
        val consumption = readings.first().consumption
        assertEquals(BigDecimal("0.461"), consumption)
        assertEquals(3, consumption.scale()) // exact decimal, no float drift
    }

    @Test
    fun `a 200 record applies to all following 300 records until the next 200`() {
        val readings = parse(
            line200("NMI_A", 30),
            line300("20050301", List(48) { "1" }),
            line300("20050302", List(48) { "1" }),
            line200("NMI_B", 30),
            line300("20050301", List(48) { "1" }),
        )
        assertEquals(48 * 3, readings.size)
        assertEquals("NMI_A", readings[0].nmi)
        assertEquals("NMI_A", readings[48].nmi)   // second day, still NMI_A
        assertEquals("NMI_B", readings[96].nmi)   // after the second 200
    }

    @Test
    fun `header, event, b2b and end records are ignored`() {
        val readings = parse(
            "100,NEM12,200506081149,UNITEDDP,NEMMCO",
            line200("NEM1201009", 30),
            line300("20050301", List(48) { "1" }),
            "400,1,48,A,,",
            "500,O,S01009,20050310121004,",
            "900",
        )
        assertEquals(48, readings.size)
    }

    @Test
    fun `blank lines and surrounding whitespace are tolerated`() {
        val readings = parse(
            "",
            "   ",
            "  " + line200("NEM1201009", 30) + "  ",
            line300("20050301", List(48) { "1" }),
            "",
        )
        assertEquals(48, readings.size)
    }

    @Test
    fun `stats reflect data records, readings and skips`() {
        val stats = ParsingStats()
        Nem12Parser(ParsingMode.STRICT)
            .parse(sequenceOf(line200("NMI_A", 30), line300("20050301", List(48) { "1" })), stats)
            .toList()

        assertEquals(1, stats.dataRecords)
        assertEquals(48, stats.readingsEmitted)
        assertEquals(0, stats.skippedRecords)
    }

    // --- strict-mode failures -------------------------------------------------

    @Test
    fun `300 before any 200 fails in strict mode`() {
        val ex = assertFailsWith<Nem12ParseException> {
            parse(line300("20050301", List(48) { "1" }))
        }
        assertEquals(1, ex.lineNumber)
    }

    @Test
    fun `non-numeric consumption fails in strict mode`() {
        val bad = List(48) { if (it == 10) "oops" else "1" }
        assertFailsWith<Nem12ParseException> {
            parse(line200("NEM1201009", 30), line300("20050301", bad))
        }
    }

    @Test
    fun `too few interval values fails in strict mode`() {
        assertFailsWith<Nem12ParseException> {
            parse(line200("NEM1201009", 30), line300("20050301", List(10) { "1" }))
        }
    }

    @Test
    fun `invalid interval date fails in strict mode`() {
        assertFailsWith<Nem12ParseException> {
            parse(line200("NEM1201009", 30), line300("2005-03-01", List(48) { "1" }))
        }
    }

    @Test
    fun `non-numeric interval length fails in strict mode`() {
        assertFailsWith<Nem12ParseException> {
            parse("200,NEM1201009,E1E2,1,E1,N1,01009,kWh,XX,20050610")
        }
    }

    // --- lenient-mode recovery ------------------------------------------------

    @Test
    fun `lenient mode skips a bad 300 record and counts it`() {
        val stats = ParsingStats()
        val bad = List(48) { if (it == 0) "n/a" else "1" }
        val readings = Nem12Parser(ParsingMode.LENIENT).parse(
            sequenceOf(
                line200("NEM1201009", 30),
                line300("20050301", bad),         // skipped
                line300("20050302", List(48) { "1" }), // kept
            ),
            stats,
        ).toList()

        assertEquals(48, readings.size)
        assertEquals(1, stats.skippedRecords)
        assertEquals(1, stats.dataRecords)
        assertTrue(readings.all { it.timestamp.toLocalDate().toString() == "2005-03-02" })
    }

    @Test
    fun `lenient mode drops NMI context when a 200 record is invalid`() {
        val stats = ParsingStats()
        val readings = Nem12Parser(ParsingMode.LENIENT).parse(
            sequenceOf(
                "200,NEM1201009,E1E2,1,E1,N1,01009,kWh,XX,20050610", // invalid interval length
                line300("20050301", List(48) { "1" }),               // no valid context -> skipped
            ),
            stats,
        ).toList()

        assertTrue(readings.isEmpty())
        assertEquals(1, stats.skippedRecords)
    }
}

