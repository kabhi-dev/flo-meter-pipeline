package com.flo.nem12

import com.flo.nem12.cli.Cli
import com.flo.nem12.parser.Nem12ParseException
import com.flo.nem12.pipeline.Nem12Pipeline
import com.flo.nem12.logging.Log
import java.io.IOException
import kotlin.system.exitProcess

/**
 * CLI entry point: parse args, run the pipeline, and map failures to sysexits.h
 * exit codes — 0 success, 64 usage error, 65 data error, 74 I/O error.
 */
fun main(args: Array<String>) {
    val exitCode = try {
        val invocation = Cli.parse(args)
        if (invocation.showHelp) {
            println(Cli.help)
            0
        } else {
            Log.level = invocation.logLevel
            Nem12Pipeline(invocation.config!!).run()
            0
        }
    } catch (e: Cli.UsageException) {
        System.err.println("error: ${e.message}\n")
        System.err.println(Cli.help)
        64
    } catch (e: Nem12ParseException) {
        System.err.println("parse error: ${e.message}")
        65
    } catch (e: IOException) {
        System.err.println("I/O error: ${e.message}")
        74
    }

    if (exitCode != 0) exitProcess(exitCode)
}

