# TestThisPuppy

**QA in a box.** Point it at a web application and its database, and one command tests the network path, the API, the forms, wrong input, SQL injection, race conditions and whole user workflows, then puts the database back exactly as it found it.

Most tests either mock the database, which tests the mock and not the system, or run against a shared database and leave rows behind. TestThisPuppy tests the real thing: a running application, a real PostgreSQL database and a real browser. Then it leaves no trace.

## What it tests

| Check | What it proves |
|---|---|
| Connection | The database host resolves, its port answers, a real login runs `SELECT 1`, and the network route is traced |
| HTTP | Each API call in a simple table returns the expected status and text |
| Happy path | Every form, filled with believable Latin dummy data, saves |
| Wrong input | Each field, given a wrong value with the browser's own checks switched off, is refused cleanly by the server: not saved, and not a crash |
| SQL injection | Attack strings through every text field and the API drop no table, delete no rows, and are stored only as plain text (throwaway database only) |
| Race conditions | Many requests released at the same instant all succeed, and exactly that many rows appear |
| Workflows | Multi-step macros, written by hand or recorded by clicking through the app, run as many times as you like with fresh data |
| Cleanup | The database matches its starting snapshot exactly, including the id sequences |
| Log analysis | Stack traces are reduced to root causes, secrets hidden, and Claude can explain each failure (optional) |

## Two database modes

- **External:** connect to an existing test database with credentials. It must be on an approved list, and it is restored to its exact snapshot afterwards.
- **Internal:** a brand-new PostgreSQL is created in Docker for the run, the app is started against it, and everything is deleted at the end. SQL injection tests only run here.

## What it found on its first run

Against the demo app in this repository, the wrong-input checks found four real bugs that the browser's own checks had been hiding. The server saved a customer with an empty name, saved "not-an-email" as an email address, crashed with a server error on an order for a customer that does not exist, and saved an order with an empty product. The fixes are in the demo app, and four regression tests now pin them. Those tests were run against the original code to prove they catch the bugs: they failed there, and they pass now.

## A real run

Internal mode, each macro repeated 50 times: **143 passed, 0 failed**, including:

```
  PASS  network  ping the database                     14 ms  logged in and SELECT 1 answered; PostgreSQL 16.16
  PASS  race     25 POST /api/customers at once       132 ms  25 of 25 succeeded; customer grew by 25 (exact)
  PASS  wrong    fill the order form: customerId set to an option that does not exist   907 ms  refused cleanly: New order
  PASS  injection fill the customer form: name = x'); DROP TABLE customer; --   757 ms  stored safely as plain text; tables intact
  PASS  macro    customer-then-order #50             3493 ms  14 steps passed
  PASS  cleanup  restore the database                  31 ms  deleted 27, set back 0, put back 0, reset 2 sequences; database matches the snapshot exactly
  PASS  cleanup  delete the throwaway database          0 ms  container stopped and removed
```

Behind it are 54 unit, controller, regression and error-path tests. The error-path tests force every failure branch on purpose: a seed that cannot succeed, an unreachable database, a restore blocked partway through (it rolls back completely), an app that dies at start, and more.

## Quick start

You need Java 21, Maven, Docker and Chrome.

```
git clone https://github.com/kenbin64/testthispuppy.git
cd testthispuppy
mvn -q install                                             # build and run the unit tests
cd puppy
mvn -q exec:java -Dexec.args=puppy-internal.properties     # the full run, on a throwaway database
```

On Windows, if Maven reports a certificate error (common behind antivirus that inspects secure traffic): `set MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT`.

## Tests are tables

Every kind of test is a plain table: a header row, then one row per test. Anyone can add a test without writing code.

```
name                   | method | path                         | body | expectedStatus | expectedText
read a seeded customer | GET    | /api/customers/{customer.id} |      | 200            | {customer.name}
```

```
action      | target         | value
open        | /customers/new |
type        | name=email     | {dummy:email}
submit      |                |
expect-text |                | Saved customer
```

## Documentation

- [User guide](docs/GUIDE.md): modes, every setting, writing tests, the checks, reading the report, CI and troubleshooting
- [Macros](docs/MACROS.md): workflow steps, recording by clicking through the app, and running hundreds of variations
- [Architecture](docs/ARCHITECTURE.md): the classes, why it snapshots instead of rolling back, the safety rules, and the tests behind it

## Layout

```
demo-app/   a small Spring Boot app (customers and orders, REST and HTML forms, embedded Tomcat) to test against
puppy/      the tester, one class per job, with its settings, test tables and macros
docs/       the guides
.github/    the CI workflow that runs everything on every push
```

## Known limits

- It reads the `public` schema of PostgreSQL. Other databases need their own schema queries and sequence handling.
- Every table needs a primary key, so rows can be told apart.
- If a test points an existing row at a brand-new parent and also deletes an original parent, the restore order can hit a foreign key. The restore then rolls back and reports the failure instead of leaving a half-restored database.
- The form filler picks the first real option in each dropdown and ticks every checkbox. Pages that need specific values are better covered by a macro.
- The AI analysis is built and its prompt is tested, but it needs your own Anthropic API credentials, and each analysis is a paid call.

## Credit

Concept and direction: Kenneth W. Bingham, rebuilding a test harness he first wrote and used at Axis41, Finicity and InComm. Built with the help of Claude AI under a standing directive to be skeptical, to insist on proof, and to allow no claim that is not demonstrated in tested code. The ideas are the author's; the AI implemented and verified them, it did not originate them.
