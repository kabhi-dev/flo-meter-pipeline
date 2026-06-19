package com.flo.nem12.pipeline

import com.flo.nem12.parser.MeterReadingSource
import com.flo.nem12.parser.Nem12Parser
import com.flo.nem12.parser.ParsingStats
import com.flo.nem12.sql.BatchedSqlInsertWriter
import com.flo.nem12.logging.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.io.path.extension

/**
 * Wires one ingestion run together: open input, stream-parse, stream-write SQL,
 * report. The only place that touches files, gzip and the console. The `use { }`
 * blocks close streams and flush the final batch even on a mid-file failure.
 */
class Nem12Pipeline(private val config: PipelineConfig) {

    fun run(): PipelineResult {
        val source: MeterReadingSource = Nem12Parser(config.mode)
        val stats = ParsingStats()
        val startNanos = System.nanoTime()

        openReader().use { reader ->
            openWriter().use { writer ->
                val sink = BatchedSqlInsertWriter(
                    out = writer,
                    batchSize = config.batchSize,
                    conflictStrategy = config.conflictStrategy,
                )
                // sink.use flushes the trailing partial batch.
                sink.use { s ->
                    source.parse(reader.lineSequence(), stats).forEach(s::write)
                }
                writer.flush()

                val durationMillis = (System.nanoTime() - startNanos) / 1_000_000
                val result = PipelineResult.of(stats, sink.statementsWritten, durationMillis)
                Log.info { result.summary() }
                return result
            }
        }
    }

    // --- I/O wiring -----------------------------------------------------------

    private fun openReader(): BufferedReader {
        val rawIn: InputStream = config.input
            ?.let { Files.newInputStream(it) }
            ?: System.`in`
        val decoded = if (isGzip(config.input)) GZIPInputStream(rawIn, config.bufferSizeBytes) else rawIn
        return BufferedReader(
            InputStreamReader(decoded, StandardCharsets.UTF_8),
            config.bufferSizeBytes,
        )
    }

    private fun openWriter(): BufferedWriter {
        val rawOut: OutputStream = config.output
            ?.let {
                it.parent?.let { dir -> Files.createDirectories(dir) }
                Files.newOutputStream(it)
            }
            ?: System.out
        val encoded = if (isGzip(config.output)) GZIPOutputStream(rawOut, config.bufferSizeBytes) else rawOut
        return BufferedWriter(
            OutputStreamWriter(encoded, StandardCharsets.UTF_8),
            config.bufferSizeBytes,
        )
    }

    private fun isGzip(path: Path?): Boolean =
        path?.extension?.equals("gz", ignoreCase = true) == true
}

