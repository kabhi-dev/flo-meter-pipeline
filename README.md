# flo-meter-pipeline
Converts NEM12 electricity interval-meter files into SQL `INSERT` statements for
the `meter_readings` table. Input and output are streamed, so files larger than
memory process fine.
```
NEM12 file -> Nem12Parser (state machine) -> Sequence<MeterReading> -> BatchedSqlInsertWriter -> SQL
```
Example output:
```sql
INSERT INTO meter_readings ("nmi", "timestamp", "consumption") VALUES
  ('NEM1201009', '2005-03-01 00:00:00', 0),
  ('NEM1201009', '2005-03-01 06:00:00', 0.461),
  ...
ON CONFLICT ("nmi", "timestamp") DO NOTHING;
```
## Quick start
Requires JDK 21. No third-party runtime dependencies.
```bash
./gradlew build                                          # compile + test
./gradlew run --args="-i nem12.csv -o inserts.sql"       # convert a file
cat nem12.csv | ./gradlew -q run | psql "$DATABASE_URL"  # use as a stdin->stdout filter
./gradlew run --args="-i big.csv.gz -o out.sql.gz -b 5000 -m lenient"
```
Load the target table once from [`db/schema.sql`](db/schema.sql), then apply the
generated SQL. For a standalone launcher that doesn't need Gradle:
```bash
./gradlew installDist
./build/install/flo-meter-pipeline/bin/flo-meter-pipeline -i nem12.csv -o out.sql
```
## CLI
| Option | Default | Description |
|---|---|---|
| `-i, --input <path>` | stdin | NEM12 input. `*.gz` is decompressed. |
| `-o, --output <path>` | stdout | SQL output. `*.gz` is compressed. |
| `-b, --batch-size <n>` | `1000` | Rows per multi-row `INSERT`. |
| `-c, --on-conflict <s>` | `do-nothing` | `do-nothing` \| `do-update` \| `error`. |
| `-m, --mode <s>` | `strict` | `strict` (fail fast) \| `lenient` (skip + count bad rows). |
| `-v` / `-q` | info | Verbose / quiet logging (stderr). |
| `-h, --help` | | Show help. |
SQL goes to stdout; diagnostics go to stderr. Exit codes follow `sysexits.h`:
`0` ok, `64` usage, `65` data, `74` I/O.
## How it works
NEM12 is a record-oriented format; each line starts with a record indicator. Two
carry the data we need:
- `200` sets the NMI and interval length (e.g. 30 minutes).
- `300` holds an interval date and one consumption value per interval
  (`1440 / interval_length` of them, so 48 for 30-minute data).
A `300` row inherits the NMI and interval length from the previous `200`. The
parser expands each `300` into one `MeterReading` per value, timestamped at the
interval start. `100/400/500/900` are recognised and skipped.
## Design notes
- **Streaming.** A lazy `Sequence` carries one record at a time from reader to
  writer, so memory stays flat on multi-GB files. Output is folded into multi-row
  `INSERT`s and written straight to a buffered stream.
- **One-directional packages.** `domain`, `parser`, `sql`, `pipeline` and `cli`
  each own one concern, and dependencies point one way toward `domain` (no
  cycles). The pipeline orchestrates two interfaces -- a `MeterReadingSource` (the
  parser) and a `MeterReadingSink` (the SQL writer) -- so either side can be
  swapped (e.g. a JDBC/`COPY` sink) without touching the other.
- **Exact and idempotent.** Consumption is `BigDecimal` (the column is `numeric`);
  timestamps are zoneless `LocalDateTime`. The default `ON CONFLICT DO NOTHING`
  makes re-runs safe and tolerates duplicate keys within a batch.
- **Strict vs lenient.** `strict` fails on the first bad record; `lenient` skips
  and counts it so one bad row doesn't abort a large ingest.
No CSV library or ORM: NEM12 is a hierarchical state machine rather than flat CSV,
and we generate bulk SQL instead of mapping objects, so neither would help.
## Assumptions
- Timestamps mark the interval **start**; `NmiInterval.timestampAt` is the single
  place to change this if interval-ending is wanted.
- Interval count comes from the `200` length, which validates each `300` row's
  width and ignores trailing quality/audit fields.
- Unknown record indicators are skipped, not fatal.
- UTF-8, comma-delimited, unquoted fields.
## Testing
`./gradlew test` covers interval expansion (30/15/5-min), exact decimals,
timestamp math, NMI carry-over, every strict/lenient failure path, SQL shape and
batching, CLI parsing, and an end-to-end run over
[`sample.csv`](src/test/resources/nem12/sample.csv) (8 records -> 384 rows, plus
gzip round-trip and lenient recovery).
## Possible next steps
- A JDBC / `COPY` sink for very large loads (the `MeterReadingSink` seam is ready).
- Fuller NEM12 conformance: `400` quality flags, checksums, multiple registers,
  daylight-saving day lengths.
- Structured logging and rows/sec metrics.
## Layout
```
src/main/kotlin/com/flo/nem12/
  Main.kt          CLI shell -> exit codes
  cli/             argument parsing
  domain/          MeterReading, NmiInterval
  parser/          Nem12Parser (MeterReadingSource) + record types, modes, stats
  sql/             MeterReadingSink, BatchedSqlInsertWriter, ConflictStrategy
  pipeline/        Nem12Pipeline, PipelineConfig/Result, I/O wiring
  logging/Log.kt   logging facade
db/schema.sql      target table DDL
```
