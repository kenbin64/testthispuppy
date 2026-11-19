# TestThisPuppy user guide

Everything you need to run TestThisPuppy, write tests for it, and read what it reports.

## The quick check: no settings file

Point it at something and it works out what to do:

```
check.cmd -                                   (Windows; ./check.sh on Mac and Linux) code piped in, e.g.  type Snippet.java | check.cmd -
check.cmd Game.java                           one source file
check.cmd C:\code\my-app                      a project folder
check.cmd https://github.com/owner/repo       a Git repository
check.cmd https://my-site.example             a running site or web app
```

| Target | What it checks |
|---|---|
| A snippet or one file | Syntax first, then bugs and practices. A Java fragment (a method, or loose statements) is wrapped so it parses, without moving any line, so line numbers match what you gave. |
| A project folder | The code check and its libraries; the project's own unit tests (Maven: every test by name; npm: the test script); test the tests (Maven projects with the pitest plugin). |
| A Git repository | Copied to a temporary folder, read, then deleted: the code and its libraries. Its tests are not run and it is not built unless you add `--trust`, because running tests or a build runs the project's own code. |
| A site | Loaded in a real browser: does it answer and how fast, JavaScript errors on the console, same-site links that are broken, error pages that show a stack trace, and security headers (listed as advice). It only reads: no forms are submitted. |

Prefix `site:`, `repo:` or `code:` when an address could be either. Options: `--level quick|standard|thorough`, `--checks`, `--language`, `--globals`, `--skip`, `--trust`, `--no-tests`, `--no-mutation`, `--xss /path?q=` (sites), `--settle` (milliseconds to let a site's scripts run, default 3000), `--out` (folder for the reports, default `puppy-check`).

It tests what software does, not how it looks: functionality, not appearance or images.

## Contents

1. What a run does
2. Installing
3. The two database modes
4. Running it
5. Settings reference
6. Writing HTTP tests
7. Writing page tests
8. The browser checks: happy path, wrong input, SQL injection
9. Macros (multi-step workflows)
10. The race test
11. The connection check
12. Log and AI analysis
13. Reading the report
14. Running in CI
15. Troubleshooting

## 1. What a run does

Every run follows the same order, and the cleanup always runs, even when something fails partway through.

| Step | What happens |
|---|---|
| Guard | Refuses any database that is not on the approved list, and any whose address contains "prod" (external mode). |
| Connection check | Resolves the database host, opens its port, logs in and runs `SELECT 1`, and traces the network route. If the database cannot be reached, the run stops before touching anything. |
| Snapshot | Records every row of every table and where every id sequence stands. |
| Seed | Fills each table with Latin dummy rows, parents first, so every foreign key points at a real row. |
| HTTP tests | Sends each request in `http-cases.txt` to the running app and checks the status and the body. |
| Race test | Releases many create requests at the same instant and checks the table grew by exactly that many. |
| Browser checks | For each page in `pages.txt`: the happy path, then wrong input in each field, then SQL injection (internal mode only). |
| Macros | Runs every workflow in the `macros` folder, as many times as `macro.repeat` says. |
| Restore | Deletes every row added since the snapshot (including rows the app created), sets back changed rows, puts back removed rows, and resets the sequences, in one transaction. |
| Verify | Takes a second snapshot and confirms it matches the first exactly. |
| Check the code | Optional (`code.source`). Reads the source without building or running it: syntax first, then structure, likely bugs, practices, security and style, with PMD's established rules (no AI). Writes `code-fixes.md`, a fix list with file, line, the problem and why it matters, for a developer or an AI to work from. The source can be a folder or a Git URL (copied to a temporary folder and deleted afterwards). |
| Test the tests | Optional (`mutation.project`). Plants small bugs in the app's code one at a time (a flipped comparison, a dropped call, a changed return value), reruns the app's own tests, and scores how many they catch. Each bug they miss is listed with its file and line. Full output in `mutation.log`. |
| Analyze | Reads the app's log for stack traces and errors, writes `puppy-analysis.md`, and optionally asks Claude to explain any failures. |
| Report | Prints a table to the console and writes a JSON report and an HTML report. The process exits with 0 when everything passed, 1 when a test failed, and 2 if the run itself crashed. |

## 2. Installing

You need Java 21, Maven, Docker and Google Chrome. Selenium finds and downloads the matching ChromeDriver itself.

```
git clone https://github.com/kenbin64/testthispuppy.git
cd testthispuppy
mvn -q install
```

On Windows, if Maven cannot download because of a certificate error (common when antivirus inspects secure traffic), tell Java to use the Windows certificate store first:

```
set MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT
```

## 3. The two database modes

**External mode** (`db.mode=external`, the default) connects to a database that already exists, with the credentials you give. Use it for a shared test database or a developer's local database. The database must be listed in `db.allowed`, and it is restored to its exact snapshot at the end.

**Internal mode** (`db.mode=internal`) creates a brand-new PostgreSQL database in Docker just for this run, applies your schema file, starts your app pointed at it, runs the tests, then stops the app and deletes the database. Nothing exists before or after. SQL injection tests only run in this mode.

| | External | Internal |
|---|---|---|
| Database | Yours, with credentials | Throwaway, created by the run |
| App | Already running | Started by the run (`app.command`) |
| After the run | Restored to the snapshot | Restored, then deleted entirely |
| SQL injection tests | Refused, reported as skipped | Allowed |

## 4. Running it

External mode, with the demo app already running on port 8080:

```
docker compose up -d
java -jar demo-app/target/demo-app-1.0.0.jar
cd puppy
mvn -q exec:java
```

Internal mode (no database or app needs to be running):

```
cd puppy
mvn -q exec:java -Dexec.args=puppy-internal.properties
```

Any properties file can be passed the same way, so one project can keep several setups, for example one per environment.

## 5. Settings reference

Settings live in a properties file (`puppy.properties` by default). Paths are relative to that file.

### Database

| Setting | Meaning | Default |
|---|---|---|
| `db.mode` | `external` or `internal` | `external` |
| `db.url` | JDBC address of the database (external mode) | none |
| `db.user`, `db.password` | Login for that database | none |
| `db.allowed` | Comma-separated list of the only databases the run may touch | empty, so nothing is allowed |
| `db.schema` | SQL file to create the tables in the throwaway database (internal mode) | none |

### The app under test

| Setting | Meaning | Default |
|---|---|---|
| `app.baseUrl` | Where the app answers, for example `http://localhost:8080` | none |
| `app.command` | How to start the app (internal mode). `{db.url}`, `{db.user}` and `{db.password}` are filled with the throwaway database. | none, meaning the app is already running |

### Tests

| Setting | Meaning | Default |
|---|---|---|
| `seed.rowsPerTable` | Dummy rows put in each table | `3` |
| `http.cases` | HTTP test table | `http-cases.txt` |
| `browser.pages` | Page test table | `pages.txt` |
| `browser.checks` | Which browser checks run: any of `happy`, `wrong-input`, `sql-injection` | `happy` |
| `browser.headless` | `true` runs Chrome hidden, `false` lets you watch | `true` |
| `browser.macros` | Folder of macro files | `macros` |
| `macro.repeat` | How many times each macro runs, each time with fresh dummy data | `1` |
| `injection.readPath` | API path the injection tests append attack strings to, for example `/api/customers/` | none |
| `injection.createPath` | API path the injection tests post attack strings to | none, meaning no API injection tests |
| `injection.createBody` | JSON body for that post; `{payload}` is replaced with each attack string | none |
| `race.path` | API path for the race test | none, meaning no race test |
| `race.method` | HTTP method for the race test | `POST` |
| `race.body` | Request body; `{n}` becomes each request's number, so unique values stay unique | none |
| `race.table` | Table that should grow by exactly the number of requests | none |
| `race.requests` | How many requests are released at once | `20` |

### Check the code

`db.mode=none` runs only the code check and test the tests, with no app and no database (see `puppy-code.properties`).

| Setting | Meaning | Default |
|---|---|---|
| `code.source` | A folder, or a Git URL (copied to a temporary folder, read, then deleted) | none, meaning off |
| `code.language` | `auto`, `java` or `javascript`. `auto` picks whichever has more source files. | `auto` |
| `code.checks` | Which groups: any of `structure`, `bugs`, `practices`, `security`, `style`, `libraries`. Syntax is always checked. | `structure,bugs,practices,security,libraries` |
| `code.engines` | Java: `pmd` and `spotbugs` (SpotBugs builds the project, so it only runs on a local Maven project, never on code from a URL). JavaScript: `eslint` (installed once, pinned, into `~/.testthispuppy`; needs Node.js). | Java `pmd,spotbugs`; JavaScript `eslint` |
| `code.build` | `on` lets the check run Maven on a local project: SpotBugs, and working out library versions a parent POM sets (without that, those libraries cannot be checked, and the report says so). Always off for code from a URL. | `on` |
| `code.globals` | JavaScript names that come from outside the project, such as a library loaded from a CDN (`THREE`). Names declared at the top of the project's own browser scripts are found automatically. | none |
| `code.level` | `quick`, `standard` or `thorough`: only the most serious issues, more, or everything | `standard` |
| `code.failOn` | `high`, `medium`, `any` or `never`: which issues fail the run. Everything found is in the fix list either way. | `high` |
| `code.skip` | Folders to leave out, comma separated, relative to the source, for example `src/test`. Dependencies, build output and minified files are always left out. | none |
| `code.ignoreRules` | Rules that do not apply to this project, by name, for example `SystemPrintln` for a command-line tool | none |

The `libraries` group runs OSV-Scanner (Google's open-source scanner, pinned version, in Docker) over the project's dependency files (`pom.xml`, `package-lock.json` and others) and lists each library with known vulnerabilities, rated by its most severe one.

### Running-app exposure checks

| Setting | Meaning | Default |
|---|---|---|
| `errors.check` | `on` causes a few errors on purpose (a missing page, a quote on the injection read path) and fails if an error page shows a stack trace | `on` |
| `errors.paths` | More paths to try, comma separated | none |
| `xss.paths` | Paths to test for reflected cross-site scripting, each ending where the value goes, for example `/search?q=`. A harmless marked piece of HTML is sent; the check fails if it comes back unescaped. | none, meaning off |

HTTP tests can use `{table.column#2}` for the second seeded row (and so on), so a spec can say "editing the first row leaves the second unchanged". Values filled into a request path are URL-encoded.

The fix list is a starting point for judgement, not a verdict. Some rules are right in general and wrong for a given project (a database entity class is meant to be a plain data holder); leave those with a comment, or ignore the rule in the settings.

### Test the tests (mutation testing)

Line coverage says a line ran; this says whether the tests would notice it being wrong. On the demo app, the tests ran 99% of the lines but caught only 31 of 41 planted bugs, until the tests were fixed. The project needs the `pitest-maven` plugin and `pitest-junit5-plugin` in its `pom.xml` once (copy the block from `demo-app/pom.xml`).

| Setting | Meaning | Default |
|---|---|---|
| `mutation.project` | Folder of the Maven project whose tests are checked | none, meaning off |
| `mutation.target` | Which classes get bugs planted, for example `com.example.*` | the project's own package |
| `mutation.level` | `quick`, `standard` or `thorough`: how many kinds of bug to plant. More kinds find more gaps and take longer. | `standard` |
| `mutation.minScore` | The % of planted bugs the tests must catch. At or above it the check passes and missed bugs are listed to look at; below it they are failures. | `80` |
| `mutation.excludeTests` | Tests to leave out, comma separated, for example slow ones that start a database | none |

### Output and analysis

| Setting | Meaning | Default |
|---|---|---|
| `demo.frames` | Folder to save a captioned screenshot of every browser step into (for demos) | none, meaning off |
| `demo.pauseMs` | Pause after each saved frame, so a person watching can follow | `0` |
| `report.file` | JSON report | `puppy-report.json` |
| `log.file` | The app's log, for analysis (external mode) | none; internal mode uses `app-under-test.log` |
| `ai.analysis` | `claude` to have Claude explain failures, `off` otherwise | `off` |

## 6. Writing HTTP tests

`http-cases.txt` is a table. The first line names the columns; every line after it is one test. Cells are separated by `|`, so a cell cannot contain one. Blank lines and lines starting with `#` are ignored.

```
name                   | method | path                         | body                                   | expectedStatus | expectedText
read a seeded customer | GET    | /api/customers/{customer.id} |                                        | 200            | {customer.name}
create a customer      | POST   | /api/customers               | {"name": "Lorem", "email": "l@e.test"} | 201            | Lorem
```

`{table.column}` is replaced with that column from the first row seeded into the table, so a test can ask for a record that is known to exist. `expectedText` may be empty, in which case only the status is checked.

## 7. Writing page tests

`pages.txt` lists the forms to test:

```
name                   | path           | expectedText
fill the customer form | /customers/new | Saved customer
```

The page is opened, scanned for every field a person could fill in (inputs, dropdowns, text areas and checkboxes, but not hidden fields or buttons), each field is filled, the form is submitted, and the next page must contain `expectedText`.

Dummy values fit each field: Latin words from `puppy/src/main/resources/filler-words.csv` for text and names, valid emails and phone numbers built from those words, small positive numbers, and today's date. Edit the CSV to change the words.

## 8. The browser checks

**Happy path** (`happy`): every field gets a good value and the page must say it saved.

**Wrong input** (`wrong-input`): for each field in turn, a wrong value goes in while the others stay good. The browser's own checks are switched off first, the way an attacker would bypass them, so this tests the server. Wrong values are: an invalid email, letters in a number field, a non-date in a date field, a dropdown option that does not exist, and an empty value in a required field. A test passes only if the app refuses cleanly: it must not save, and it must not crash with a server error.

**SQL injection** (`sql-injection`, internal mode only): attack strings such as `x'); DROP TABLE customer; --` go into every text field and into the API. A test passes when every table still exists, no table lost rows, the app did not crash, and any attack string it saved is stored as plain text, which proves it never ran as SQL. If this check is switched on against a real database, it is refused and reported as skipped.

## 9. Macros

Macros are multi-step workflows, such as "create a customer, then order for that customer". See [MACROS.md](MACROS.md) for the step reference, how to record a macro by clicking through the app, and how to run hundreds of variations.

## 10. The race test

The race test releases `race.requests` identical requests at the same instant. It passes when every request succeeded and `race.table` grew by exactly that many rows. Lost writes, duplicates and crashes under load all show up as a wrong count or a failed request.

## 11. The connection check

Before anything touches the database, the run checks the road to it: the host name resolves, the port accepts a connection (a TCP ping, because an ICMP ping needs administrator rights on many systems), and a real login runs `SELECT 1`, timed, with the database version. The route trace (`tracert` on Windows, `traceroute` elsewhere) is shown for information and never fails the run. If the database cannot be reached, the run stops before seeding or changing anything.

## 12. Log and AI analysis

After every run, the app's log is read without any AI. Each stack trace is reduced to its root cause (the deepest "Caused by"), repeats are grouped, ERROR lines are kept, and passwords, tokens, API keys and credentials inside database addresses are hidden. The result goes into `puppy-analysis.md`.

With `ai.analysis=claude`, and at least one failed test, the failed tests and the redacted log findings are sent to Claude (model `claude-opus-5-5`), which explains the likely cause and a fix for each failure, or says plainly when the evidence is not enough to tell. The explanation is added to `puppy-analysis.md`.

It is off by default because each analysis is a paid API call and logs can hold real data. Credentials come from `ANTHROPIC_API_KEY`, or a profile created with `ant auth login`. If no credentials are found, or the API cannot be reached, the analysis is skipped with a plain reason and the run is not affected.

## 13. Reading the report

The console shows one line per step or test:

```
  PASS  wrong    fill the order form: quantity = "abc"   863 ms  refused cleanly: Whitelabel Error Page
```

The columns are the outcome, the kind of test (`network`, `setup`, `http`, `race`, `browser`, `wrong`, `injection`, `macro`, `cleanup`, `log`, `ai`), the test's name, how long it took, and what happened. The same results go into the JSON report, and the exit code is 0 only when everything passed, so a CI system can fail the build on any failure.

Files a run can leave next to the settings file: the JSON report, the HTML report (same name, `.html`), `puppy-analysis.md`, `app-under-test.log` (internal mode), and a screenshot named after any macro that failed.

## 14. Running in CI

`.github/workflows/ci.yml` runs on every push and pull request: all unit, controller, regression and error-path tests, then a full run in internal mode. The report, the analysis, the app log and any failure screenshots are kept as a downloadable artifact.

## 15. Troubleshooting

| Symptom | Likely cause and fix |
|---|---|
| `Refusing to run against ...` | The database is not in `db.allowed`, or its address contains "prod". Add it to the list if it is truly a test database. |
| `reach the database port` fails | The database is not running, or a firewall blocks the port. For the demo, run `docker compose up -d`. |
| `PKIX path building failed` during the build | Antivirus or a proxy inspecting secure traffic. Set `MAVEN_OPTS` as in section 2. |
| Testcontainers says no valid Docker configuration | Docker is not running, or an old Testcontainers version is talking to a newer Docker. Start Docker; this project uses Testcontainers 1.21.4, which works with Docker Engine 29. |
| `Unable to rename ... .jar` on Windows | The app is still running from that jar. Stop it before rebuilding. |
| A browser check fails with `failed: no such element` | The page has no submit button, or the field names changed. Check the page, or the macro's targets. |
| `Table ... has no primary key` | Every table needs a primary key so its rows can be told apart. |
