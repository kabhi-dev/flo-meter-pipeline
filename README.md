# flo-meter-pipeline

Reads NEM12 interval-meter files and writes SQL `INSERT` statements for the
`meter_readings` table. It processes the input one line at a time, so a
multi-gigabyte file uses about the same memory as a small one.

A `300` row holding 48 half-hour readings comes out as one row per interval:

```sql
INSERT INTO meter_readings ("nmi", "timestamp", "consumption") VALUES
  ('NEM1201009', '2005-03-01 00:00:00', 0),
  ('NEM1201009', '2005-03-01 06:00:00', 0.461),
  ...
ON CONFLICT ("nmi", "timestamp") DO NOTHING;
```

## Running it

You need a JDK 21; the build pins the toolchain. There are no third-party runtime
libraries.

```bash
./gradlew build                                          # compile and test
./gradlew run --args="-i nem12.csv -o inserts.sql"       # file in, file out
cat nem12.csv | ./gradlew -q run | psql "$DATABASE_URL"  # or use it as a filter
./gradlew run --args="-i big.csv.gz -o out.sql.gz -b 5000 -m lenient"
```

If you'd rather not go through Gradle each time:

```bash
./gradlew installDist
./build/install/flo-meter-pipeline/bin/flo-meter-pipeline -i nem12.csv -o out.sql
```

Create the table once from `db/schema.sql`, then apply the generated SQL.

### Options

| Flag | Default | Meaning |
|---|---|---|
| `-i, --input <path>` | stdin | NEM12 file to read; a `.gz` file is decompressed automatically. |
| `-o, --output <path>` | stdout | SQL file to write; a `.gz` name is compressed automatically. |
| `-b, --batch-size <n>` | `1000` | Rows per `INSERT` statement. |
| `-c, --on-conflict <mode>` | `do-nothing` | `do-nothing`, `do-update`, or `error`. |
| `-m, --mode <mode>` | `strict` | `strict` stops on a bad row; `lenient` skips and counts it. |
| `-v` / `-q` | info | More or less logging on stderr. |
| `-h, --help` | | Print usage. |

Logging goes to stderr and only SQL goes to stdout, so piping into `psql` stays
clean. Exit codes follow the usual convention: `0` success, `64` bad arguments,
`65` bad input data, `74` I/O failure.

## How NEM12 maps to rows

NEM12 is a record-based format rather than a flat CSV. Each line starts with a
number that says what it is, and a line's meaning depends on the lines above it:

- A `200` row names the meter — the NMI is the second field — and gives the
  interval length in minutes as the ninth field (`30` in the sample).
- A `300` row has a date in the second field and then the interval readings. How
  many readings there are is `1440 / interval_length`, so 48 for half-hour data.

A `300` row takes its NMI and interval length from the most recent `200` row. The
parser turns each reading into one `meter_readings` row and works out the timestamp
from the date and the reading's position. The `100`, `400`, `500` and `900` rows
aren't needed for this task, so they're read and ignored.

## Layout

| Package | What's in it |
|---|---|
| `domain` | `MeterReading` and `NmiInterval` (turns an interval index into a timestamp). Depends on nothing else. |
| `parser` | `Nem12Parser`, plus `RecordType`, `ParsingMode`, `ParsingStats`, `Nem12ParseException`. |
| `sql` | The `MeterReadingSink` interface, `BatchedSqlInsertWriter`, and `ConflictStrategy`. |
| `pipeline` | `Nem12Pipeline` with `PipelineConfig` / `PipelineResult`, and the file / gzip / console wiring. |
| `cli` | `Cli` — argument parsing, written without side effects so it can be tested directly. |
| `logging` | `Log`, a one-object stderr logger. |
| (root) | `Main` — reads the arguments, runs the pipeline, turns failures into exit codes. |

The dependencies point one way. `domain` knows nothing about parsing, SQL or files;
the parser knows nothing about SQL; everything to do with files, gzip and the console
lives in `pipeline`. That's what lets each piece be tested on its own, and it's why
adding a database-backed writer later only touches the `sql` package.

## Decisions and trade-offs

Streaming. The requirement that drove the design is handling very large files. The
parser reads a lazy sequence of lines and hands back a lazy sequence of readings, so
at any point it holds the current line, the current `200` context, and at most one
day's worth of readings. Memory stays flat as the file grows. Reading the whole file
into a list would be simpler to write but would fall over on a multi-gigabyte input.

No CSV library and no ORM. The fields are plain comma-separated tokens, so splitting
them is a `String.split`; the real work is the state between rows (a `300` inheriting
from the last `200`), which a CSV library wouldn't help with. An ORM is built for
mapping objects to and from rows, not for emitting bulk SQL quickly, so it would be in
the way here. The writer sits behind a small interface, so a real database client can
replace it later without the parser changing.

Batched inserts. Readings are written as multi-row `INSERT`s, 1000 rows at a time by
default. One statement per row would multiply the parse and round-trip cost when you're
loading millions of rows. For the heaviest loads Postgres `COPY` is faster still, which
is noted under the questions below.

Exact numbers and timestamps. Consumption is a `BigDecimal` because the column is
`numeric` and this is billing data — a `Double` would drift. Timestamps are
`LocalDateTime` because NEM12 carries no timezone and the column is `timestamp` without
a zone.

Duplicates. The table has a unique key on `(nmi, timestamp)`, and meter reads get
re-sent, so the default is `ON CONFLICT DO NOTHING`: you can apply the output again
without an error, and repeated keys inside a single batch are fine — which they are not
under `DO UPDATE`. If you want corrections to win, `-c do-update`; if you want the
database to reject duplicates, `-c error`.

Bad data. `strict` mode stops at the first malformed row, which is what you want in CI
or as a quality gate. `lenient` mode skips the row, counts it, and keeps going, which is
what you want when backfilling a large feed where one bad row shouldn't sink the job.

## Assumptions

- The timestamp is the start of the interval, so half-hour data for 1 March runs from
  00:00 to 23:30 and stays on that date. NEM12 can also be read as interval-ending;
  that's the only place it matters and it's a one-line change in
  `NmiInterval.timestampAt`.
- The reading count on a `300` row comes from the `200` interval length, not from
  counting commas. That also checks the row is wide enough and lets the trailing
  quality and audit fields be ignored.
- Unrecognised record types are skipped rather than treated as errors.
- Input is UTF-8 with unquoted fields, which holds for the record types here.

## Tests

`./gradlew test` runs 41 tests:

- Parser: interval expansion for 30/15/5-minute data, exact decimals, the timestamp
  maths, the NMI carrying across rows, skipped and blank lines, and each failure path in
  both strict and lenient modes.
- SQL writer: statement shape, batch boundaries, quote escaping, the three conflict
  modes, and plain-decimal output.
- Domain and CLI: the interval maths and its guards; every flag, the defaults, and the
  argument errors.
- End to end: the sample from the brief through the whole pipeline (8 rows in, 384 out,
  with a few values checked by hand), plus a gzip round-trip, batch splitting, and a
  recovered bad row.

The sample at `src/test/resources/nem12/sample.csv` is the example from the brief,
committed as-is.

## Assessment questions

### Q1 — Why these technologies

Kotlin on the JVM. The repository was set up for it, and it fits the work: sequences
give lazy streaming for the large-file case, data classes give small immutable models
for free, and the JVM already has the rest — buffered I/O, gzip, `BigDecimal`,
`java.time` — so there's nothing third-party to pull in at runtime. I left out a CSV
parser and an ORM on purpose: the format isn't flat CSV, and the goal is fast bulk SQL
rather than object mapping. Tests use JUnit through `kotlin.test`, which the project was
already configured for.

### Q2 — What I'd do with more time

- A `COPY`- or JDBC-based writer for the largest loads, behind the existing sink
  interface.
- More of the NEM12 spec: the `400` quality flags, the record counts and checksums,
  multiple registers and units, and the daylight-saving days that aren't 48 intervals
  long.
- A real logging library and a few throughput and skip-rate numbers, so a scheduler can
  see what happened.
- A benchmark over a synthetic multi-gigabyte file to keep the memory and speed claims
  honest, plus property-based parser tests and a golden-file test for the full SQL.
- Splitting parse and write across threads, but only once a profiler shows I/O isn't
  already the limit.

### Q3 — Why the design looks like this

It follows the two things the brief asks for — be correct, and handle very large files —
and tries not to box in the changes I'd expect next. Streaming is the direct answer to
the size requirement. Keeping the domain clear of I/O and SQL, and pushing the things
most likely to change — files, the SQL dialect, the CLI — out to the edges means a
database writer, a different output format or extra record types stay local changes.
Exact decimals and an idempotent `ON CONFLICT` reflect that this is billing data going
into a uniquely-keyed table. Strict-versus-lenient and the conflict mode are options
rather than fixed choices because the right one depends on whether you're gating data or
backfilling it.

## Project tree

```
src/main/kotlin/com/flo/nem12/
  Main.kt            command-line entry point
  cli/Cli.kt         argument parsing
  domain/            MeterReading, NmiInterval
  parser/            Nem12Parser, RecordType, ParsingMode, ParsingStats, exceptions
  sql/               MeterReadingSink, BatchedSqlInsertWriter, ConflictStrategy
  pipeline/          Nem12Pipeline, PipelineConfig, PipelineResult, I/O wiring
  logging/Log.kt     stderr logger
src/test/kotlin/com/flo/nem12/    tests, one package per source package
src/test/resources/nem12/sample.csv
db/schema.sql        the meter_readings table
```

