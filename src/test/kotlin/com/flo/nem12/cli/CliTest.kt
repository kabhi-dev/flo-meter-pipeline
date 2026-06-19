package com.flo.nem12.cli

import com.flo.nem12.parser.ParsingMode
import com.flo.nem12.sql.ConflictStrategy
import com.flo.nem12.logging.Log
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CliTest {

    @Test
    fun `defaults are applied when no options are given`() {
        val invocation = Cli.parse(emptyArray())
        val config = assertNotNull(invocation.config)

        assertNull(config.input)   // stdin
        assertNull(config.output)  // stdout
        assertEquals(1000, config.batchSize)
        assertEquals(ConflictStrategy.DO_NOTHING, config.conflictStrategy)
        assertEquals(ParsingMode.STRICT, config.mode)
        assertEquals(Log.Level.INFO, invocation.logLevel)
    }

    @Test
    fun `long options are parsed`() {
        val invocation = Cli.parse(
            arrayOf(
                "--input", "in.csv",
                "--output", "out.sql",
                "--batch-size", "5000",
                "--on-conflict", "do-update",
                "--mode", "lenient",
                "--verbose",
            ),
        )
        val config = assertNotNull(invocation.config)

        assertEquals(Path("in.csv"), config.input)
        assertEquals(Path("out.sql"), config.output)
        assertEquals(5000, config.batchSize)
        assertEquals(ConflictStrategy.DO_UPDATE, config.conflictStrategy)
        assertEquals(ParsingMode.LENIENT, config.mode)
        assertEquals(Log.Level.DEBUG, invocation.logLevel)
    }

    @Test
    fun `short options are parsed`() {
        val invocation = Cli.parse(arrayOf("-i", "in.csv", "-o", "out.sql", "-b", "10", "-c", "error", "-m", "strict", "-q"))
        val config = assertNotNull(invocation.config)

        assertEquals(10, config.batchSize)
        assertEquals(ConflictStrategy.ERROR, config.conflictStrategy)
        assertEquals(Log.Level.ERROR, invocation.logLevel)
    }

    @Test
    fun `help short-circuits parsing`() {
        val invocation = Cli.parse(arrayOf("--help"))
        assertTrue(invocation.showHelp)
        assertNull(invocation.config)
    }

    @Test
    fun `unknown option is a usage error`() {
        assertFailsWith<Cli.UsageException> { Cli.parse(arrayOf("--nope")) }
    }

    @Test
    fun `option missing its value is a usage error`() {
        assertFailsWith<Cli.UsageException> { Cli.parse(arrayOf("--input")) }
    }

    @Test
    fun `non-numeric batch size is a usage error`() {
        assertFailsWith<Cli.UsageException> { Cli.parse(arrayOf("--batch-size", "lots")) }
    }

    @Test
    fun `zero batch size is a usage error`() {
        assertFailsWith<Cli.UsageException> { Cli.parse(arrayOf("--batch-size", "0")) }
    }

    @Test
    fun `invalid enum value is a usage error`() {
        assertFailsWith<Cli.UsageException> { Cli.parse(arrayOf("--mode", "sloppy")) }
        assertFailsWith<Cli.UsageException> { Cli.parse(arrayOf("--on-conflict", "merge")) }
    }
}

