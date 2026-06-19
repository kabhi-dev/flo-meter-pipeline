package com.flo.nem12.pipeline

import com.flo.nem12.parser.ParsingMode
import com.flo.nem12.sql.BatchedSqlInsertWriter
import com.flo.nem12.sql.ConflictStrategy
import java.nio.file.Path

/**
 * Configuration for one pipeline run. `null` paths mean stdin/stdout, so the tool
 * can act as a Unix filter: `cat file | flo-meter-pipeline | psql`.
 */
data class PipelineConfig(
    /** Input NEM12 file; `null` reads from stdin. `.gz` inputs are decompressed. */
    val input: Path? = null,
    /** Output SQL file; `null` writes to stdout. `.gz` outputs are compressed. */
    val output: Path? = null,
    val batchSize: Int = BatchedSqlInsertWriter.DEFAULT_BATCH_SIZE,
    val conflictStrategy: ConflictStrategy = ConflictStrategy.DO_NOTHING,
    val mode: ParsingMode = ParsingMode.STRICT,
    /** I/O buffer size in bytes; 64 KiB is a good default for sequential bulk I/O. */
    val bufferSizeBytes: Int = 1 shl 16,
)

