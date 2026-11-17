# How TestThisPuppy is built

One class per job. `PuppyRun` calls them in order; nothing else knows the order.

## The classes

| Class | Its one job |
|---|---|
| `PuppyRun` | Runs the steps in order, and makes sure the restore always runs |
| `PuppyConfig` | Reads the settings file |
| `DatabaseGuard` | Refuses databases that are not approved, and anything named like production |
| `InternalDatabase` | Starts and deletes a throwaway PostgreSQL with Testcontainers |
| `AppProcess` | Starts the app under test, waits until it answers, and stops it |
| `ConnectionCheck` | Resolves the host, opens the port, pings the database, traces the route |
| `SchemaReader` | Reads tables, columns, primary keys and foreign keys from the database itself |
| `TableOrder` | Orders tables parents first (for inserting) or children first (for deleting), and reports loops |
| `SnapshotTaker`, `DatabaseSnapshot` | Records every row and every sequence |
| `DatabaseSeeder` | Inserts dummy rows, parents first, so foreign keys always point at real rows |
| `DummyValues`, `FillerWords` | Makes believable values from the Latin words in `filler-words.csv` |
| `GridFile` | Reads the test tables: a header row, then one row per test |
| `HttpCase`, `HttpTester`, `Placeholders` | Runs one HTTP test, with `{table.column}` filled from seeded data |
| `RaceTester` | Releases many requests at once and checks the row count |
| `PageScanner`, `FormField` | Finds every fillable field on a page |
| `BrowserTester` | Happy-path and wrong-input checks in Chrome |
| `WrongValues` | The wrong value to try for each kind of field |
| `InjectionTester` | SQL injection through the forms and the API, judged from the database |
| `MacroRunner`, `MacroRecorder` | Runs and records multi-step workflows |
| `DatabaseRestorer` | Puts the database back exactly, in one transaction |
| `LogAnalyzer` | Finds root causes and errors in the app log, and hides secrets |
| `AiAnalyzer` | Asks Claude to explain failures from the redacted evidence |
| `Reporter`, `TestResult` | Prints the results and writes the JSON report |

## Why a snapshot instead of a rollback

The usual trick for leaving no trace is to run each test inside a transaction and roll it back. That cannot work here: the app under test writes through its own database connections, which the test's transaction cannot see or undo. So TestThisPuppy records the database's contents before the run, compares afterwards, and undoes the difference.

The restore runs in one transaction:

1. Rows that exist now but not in the snapshot are deleted, children first, so no foreign key is broken.
2. Rows whose values changed are set back.
3. Rows that were in the snapshot but are gone are put back, parents first.
4. Every sequence is reset, so the next id is the one it would have been.

If any step fails, the whole restore rolls back, so the database is never left half-restored, and the failure is reported. A second snapshot is then compared with the first, and the cleanup only passes if they match exactly.

## Safety rules built into the code

- External mode will not start unless the database is on the approved list, and refuses anything whose address contains "prod".
- SQL injection tests are refused outside internal mode, and the refusal is reported first, however far the run gets.
- If the database cannot be reached, the run stops before anything is seeded or changed.
- The restore runs in a `finally` block, so it runs even when seeding or a test throws.
- Logs are redacted before anything is written or sent to an AI.

## The tests behind it

| Suite | What it proves |
|---|---|
| `DummyValuesTest`, `HttpTesterTest`, `PageScannerTest` | The three drill pieces (graders) |
| `PlumbingTest` | Grid files, table order, placeholders, filler words and the guard |
| `FailurePathsTest` | Every error path fails safely: seeding failures, an unreachable database, a refused injection run, a restore that cannot finish (it rolls back completely), a table without a key, an app that dies at start, a page without a submit button, a missing test file |
| `LogAnalyzerTest` | Root causes, grouping, error lines, redaction, and that the AI prompt carries no secrets |
| `MacroRecorderTest` | Reading recorded steps and writing a runnable table |
| demo-app `ObjectsTest` | The entities hold their values and start in the right state |
| demo-app `ApiControllerTest`, `PageControllerTest` | The controllers, with the database mocked, including four regression tests for the bugs the wrong-input checks found |

The regression tests were checked against the original, buggy controller: all of them failed there, and all pass on the fixed one.
