package com.flo.nem12.cli

import com.flo.nem12.parser.ParsingMode
import com.flo.nem12.pipeline.PipelineConfig
import com.flo.nem12.sql.ConflictStrategy
import com.flo.nem12.logging.Log
import java.nio.file.Path
import kotlin.io.path.Path

/** Result of interpreting the command line. */
data class CliInvocation(
    val config: PipelineConfig?,
    val logLevel: Log.Level,
    val showHelp: Boolean,
)

/**
 * Minimal argument parser for the handful of flags this tool needs. [parse] does
 * no I/O, so the CLI can be unit-tested by asserting on the returned
 * [CliInvocation].
 */
object Cli {

    class UsageException(message: String) : RuntimeException(message)

    fun parse(args: Array<String>): CliInvocation {
        var input: Path? = null
        var output: Path? = null
        var batchSize = PipelineConfig().batchSize
        var conflict = ConflictStrategy.DO_NOTHING
        var mode = ParsingMode.STRICT
        var logLevel = Log.Level.INFO

        val it = args.iterator()
        while (it.hasNext()) {
            when (val arg = it.next()) {
                "-h", "--help" -> return CliInvocation(null, logLevel, showHelp = true)
                "-i", "--input" -> input = Path(requireValue(it, arg))
                "-o", "--output" -> output = Path(requireValue(it, arg))
                "-b", "--batch-size" -> batchSize = parseBatchSize(requireValue(it, arg))
                "-c", "--on-conflict" -> conflict = parseConflict(requireValue(it, arg))
                "-m", "--mode" -> mode = parseMode(requireValue(it, arg))
                "-v", "--verbose" -> logLevel = Log.Level.DEBUG
                "-q", "--quiet" -> logLevel = Log.Level.ERROR
                else -> throw UsageException("unknown option '$arg'")
            }
        }

        val config = PipelineConfig(
            input = input,
            output = output,
            batchSize = batchSize,
            conflictStrategy = conflict,
            mode = mode,
        )
        return CliInvocation(config, logLevel, showHelp = false)
    }

    private fun requireValue(it: Iterator<String>, option: String): String {
        if (!it.hasNext()) throw UsageException("option '$option' requires a value")
        return it.next()
    }

    private fun parseBatchSize(raw: String): Int {
        val n = raw.toIntOrNull() ?: throw UsageException("batch-size must be an integer, got '$raw'")
        if (n < 1) throw UsageException("batch-size must be >= 1, got $n")
        return n
    }

    private fun parseConflict(raw: String): ConflictStrategy = when (raw.lowercase()) {
        "do-nothing" -> ConflictStrategy.DO_NOTHING
        "do-update" -> ConflictStrategy.DO_UPDATE
        "error" -> ConflictStrategy.ERROR
        else -> throw UsageException("on-conflict must be one of do-nothing|do-update|error, got '$raw'")
    }

    private fun parseMode(raw: String): ParsingMode = when (raw.lowercase()) {
        "strict" -> ParsingMode.STRICT
        "lenient" -> ParsingMode.LENIENT
        else -> throw UsageException("mode must be one of strict|lenient, got '$raw'")
    }

    val help: String = """
        flo-meter-pipeline — convert NEM12 interval data into SQL INSERTs for the meter_readings table.

        USAGE:
          flo-meter-pipeline [options]

        OPTIONS:
          -i, --input <path>       NEM12 input file (default: stdin). '.gz' is auto-decompressed.
          -o, --output <path>      SQL output file (default: stdout). '.gz' is auto-compressed.
          -b, --batch-size <n>     Rows per INSERT statement (default: 1000).
          -c, --on-conflict <s>    do-nothing | do-update | error (default: do-nothing).
          -m, --mode <s>           strict | lenient (default: strict).
          -v, --verbose            Verbose (debug) logging on stderr.
          -q, --quiet              Errors only on stderr.
          -h, --help               Show this help and exit.

        EXAMPLES:
          flo-meter-pipeline -i nem12.csv -o inserts.sql
          cat nem12.csv | flo-meter-pipeline | psql "${'$'}DATABASE_URL"
          flo-meter-pipeline -i big.csv.gz -o out.sql.gz -b 5000 -m lenient
    """.trimIndent()
}


