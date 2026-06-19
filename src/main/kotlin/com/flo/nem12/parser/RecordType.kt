package com.flo.nem12.parser

/**
 * NEM12 record types, keyed by the leading record-indicator field. Only `200`
 * and `300` carry data we need; the rest are listed so they can be skipped
 * explicitly instead of being treated as errors.
 */
enum class RecordType(val indicator: String) {
    /** File header (one per file). */
    HEADER("100"),

    /** NMI Data Details — carries the NMI and interval length. */
    NMI_DATA_DETAILS("200"),

    /** Interval Data — the interval date and the consumption values. */
    INTERVAL_DATA("300"),

    /** Interval Event — quality/method annotations. Not needed here. */
    INTERVAL_EVENT("400"),

    /** B2B Details. Not needed here. */
    B2B_DETAILS("500"),

    /** End of data (one per file). */
    END_OF_DATA("900"),
    ;

    companion object {
        private val BY_INDICATOR = entries.associateBy(RecordType::indicator)

        /** @return the [RecordType] for [indicator], or `null` if unrecognised. */
        fun fromIndicator(indicator: String): RecordType? = BY_INDICATOR[indicator]
    }
}

