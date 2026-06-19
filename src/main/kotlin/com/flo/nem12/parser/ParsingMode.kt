package com.flo.nem12.parser

/**
 * How the parser reacts to malformed input.
 *
 * - [STRICT] fail on the first bad record.
 * - [LENIENT] skip and count the bad record, then continue — useful for bulk
 *   ingestion where one bad row shouldn't abort the whole file.
 */
enum class ParsingMode { STRICT, LENIENT }

