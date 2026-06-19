package com.flo.nem12.logging

/**
 * Tiny logging facade that writes to stderr, keeping stdout clean for generated
 * SQL. Hiding it behind one object makes swapping in a real logger easy later.
 */
object Log {
    @Volatile
    var level: Level = Level.INFO

    enum class Level { DEBUG, INFO, WARN, ERROR, OFF }

    fun debug(message: () -> String) = log(Level.DEBUG, message)
    fun info(message: () -> String) = log(Level.INFO, message)
    fun warn(message: () -> String) = log(Level.WARN, message)
    fun error(message: () -> String) = log(Level.ERROR, message)

    private fun log(at: Level, message: () -> String) {
        if (level == Level.OFF || at.ordinal < level.ordinal) return
        System.err.println("[$at] ${message()}")
    }
}

