package com.flo.nem12.sql

/**
 * How generated `INSERT`s handle a clash on the `(nmi, timestamp)` unique key.
 * Meter reads are often re-transmitted, so this is a real operational choice.
 *
 * - [DO_NOTHING] (default): idempotent, and tolerant of duplicate keys within a
 *   single batch.
 * - [DO_UPDATE]: latest read wins; safe only when input is de-duplicated, since
 *   Postgres rejects two identical conflict targets in one statement.
 * - [ERROR]: no `ON CONFLICT` clause — let the database reject duplicates.
 */
enum class ConflictStrategy {
    DO_NOTHING,
    DO_UPDATE,
    ERROR,
}

