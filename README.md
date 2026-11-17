# TestThisPuppy

Real-database testing that leaves no trace.

Most tests either mock the database, which tests the mock and not the system, or run against a shared database and leave rows behind. TestThisPuppy tests the real thing: a running application, a real PostgreSQL database and a real browser. Then it puts the database back exactly as it found it.

## One run, in order

1. **Guard.** It refuses any database that is not on the approved list in `puppy.properties`, and any whose address contains "prod", even if listed.
2. **Snapshot.** It records every row of every table, and where every id sequence stands.
3. **Seed.** It fills each table with dummy rows, parents first, so every foreign key points at a real row.
4. **HTTP tests.** It sends real requests to the running application and checks the status and the body.
5. **Browser tests.** Selenium opens each page, scans it for every field, fills each one with a fitting dummy value, submits, and checks the page that comes back.
6. **Restore.** It deletes every row added since the snapshot, including rows the application created itself, sets back any row that changed, puts back any row that was removed, and resets every sequence. This all happens in one transaction.
7. **Verify and report.** It takes a second snapshot, confirms it matches the first exactly, and writes the results to the console and to `puppy-report.json`.

The restore always runs, even when seeding or a test fails partway through.

Why not just roll back a transaction? Because the application writes through its own database connections, which a test's transaction cannot see or undo. Comparing against a snapshot catches everything.

## Tests are tables

Both test files use the same format. The first line names the columns, and every line after it is one test.

`puppy/http-cases.txt`:

```
name                   | method | path                         | body | expectedStatus | expectedText
read a seeded customer | GET    | /api/customers/{customer.id} |      | 200            | {customer.name}
```

`{table.column}` is filled from the first row seeded into that table, so a test can ask for a record that is known to exist.

`puppy/pages.txt`:

```
name                   | path           | expectedText
fill the customer form | /customers/new | Saved customer
```

## A real run against the demo app

The database already held one real customer and one real order before this run.

```
  PASS  setup    seed dummy data                       38 ms  6 rows into 2 tables [customer, purchase_order]
  PASS  http     list customers                        59 ms  status 200 (expected 200)
  PASS  http     read a seeded customer                13 ms  status 200 (expected 200)
  PASS  http     a missing customer is 404              9 ms  status 404 (expected 404)
  PASS  http     create a customer                     18 ms  status 201 (expected 201)
  PASS  http     reject a customer with no email        9 ms  status 400 (expected 400)
  PASS  http     list orders                           17 ms  status 200 (expected 200)
  PASS  browser  fill the customer form              1444 ms  filled 3 fields; page said "Saved customer 6"
  PASS  browser  fill the order form                  941 ms  filled 4 fields; page said "Saved order 5"
  PASS  cleanup  restore the database                  30 ms  deleted 9, set back 0, put back 0, reset 2 sequences; database matches the snapshot exactly (2 rows)
```

Afterwards the database still held only the real customer and the real order, and both id sequences were back where they started, so the next real customer still gets the next id.

## Run it

You need Java 21, Maven, Docker and Chrome.

```
docker compose up -d                                   # PostgreSQL on port 5433
mvn -q -DskipTests install                             # build both modules
java -jar demo-app/target/demo-app-1.0.0.jar           # the demo app on port 8080 (leave it running)
cd puppy && mvn -q exec:java                           # run TestThisPuppy
mvn -q -pl puppy test                                  # the unit tests (22)
```

On Windows, if Maven cannot download because of a certificate error (common behind antivirus that inspects secure traffic), tell Java to use the Windows certificate store: `set MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT`.

## What it does not claim yet

- It reads the `public` schema of PostgreSQL. Other databases need their own schema queries and sequence handling.
- Every table needs a primary key, so rows can be told apart.
- If a test points an existing row at a brand-new parent and also deletes an original parent, the restore order can hit a foreign key. The restore then rolls back and reports the failure instead of leaving a half-restored database.
- The form filler picks the first real option in each dropdown and ticks every checkbox. Pages that need specific values need their own test data.

## Layout

```
demo-app/   a small Spring Boot app (customers and orders, REST and HTML forms) to test against
puppy/      the tester: one class per job (guard, schema reader, snapshot, seeder, testers, restorer, report)
```

## Credit

Concept and direction: Kenneth W. Bingham, rebuilding a test harness he first wrote and used at Axis41, Finicity and InComm. Built with the help of Claude AI under a standing directive to be skeptical, to insist on proof, and to allow no claim that is not demonstrated in tested code. The ideas are the author's; the AI implemented and verified them, it did not originate them.
