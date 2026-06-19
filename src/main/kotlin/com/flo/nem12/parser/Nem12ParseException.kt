package com.flo.nem12.parser

/**
 * Raised when a NEM12 line cannot be interpreted. Carries the 1-based
 * [lineNumber] so the offending row is easy to locate in a large file.
 */
class Nem12ParseException(
    val lineNumber: Int,
    message: String,
    cause: Throwable? = null,
) : RuntimeException("Line $lineNumber: $message", cause)

