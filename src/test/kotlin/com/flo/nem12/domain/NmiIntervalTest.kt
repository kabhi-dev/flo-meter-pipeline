package com.flo.nem12.domain

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NmiIntervalTest {

    private val date = LocalDate.parse("2005-03-01")

    @Test
    fun `intervals per day derives from interval length`() {
        assertEquals(48, NmiInterval("NMI", 30).intervalsPerDay)
        assertEquals(96, NmiInterval("NMI", 15).intervalsPerDay)
        assertEquals(288, NmiInterval("NMI", 5).intervalsPerDay)
        assertEquals(1440, NmiInterval("NMI", 1).intervalsPerDay)
    }

    @Test
    fun `timestampAt maps interval index to the interval start`() {
        val nmi = NmiInterval("NMI", 30)
        assertEquals(LocalDateTime.parse("2005-03-01T00:00:00"), nmi.timestampAt(date, 0))
        assertEquals(LocalDateTime.parse("2005-03-01T00:30:00"), nmi.timestampAt(date, 1))
        assertEquals(LocalDateTime.parse("2005-03-01T23:30:00"), nmi.timestampAt(date, 47))
    }

    @Test
    fun `timestampAt rejects an out-of-range index`() {
        val nmi = NmiInterval("NMI", 30)
        assertFailsWith<IllegalArgumentException> { nmi.timestampAt(date, 48) }
        assertFailsWith<IllegalArgumentException> { nmi.timestampAt(date, -1) }
    }

    @Test
    fun `blank nmi is rejected`() {
        assertFailsWith<IllegalArgumentException> { NmiInterval("  ", 30) }
    }

    @Test
    fun `nmi longer than the column width is rejected`() {
        assertFailsWith<IllegalArgumentException> { NmiInterval("01234567890", 30) } // 11 chars
    }

    @Test
    fun `interval length must divide a day evenly`() {
        assertFailsWith<IllegalArgumentException> { NmiInterval("NMI", 7) }
        assertFailsWith<IllegalArgumentException> { NmiInterval("NMI", 0) }
        assertFailsWith<IllegalArgumentException> { NmiInterval("NMI", 1441) }
    }
}

