# Benchmark method

Written and committed with the answer key (`defects.json`) before any tool was run, so the rules cannot be bent to fit the results.

## What is compared

A small Spring Boot shop, `bugzoo/`, with 21 defects planted on purpose in five kinds: behaviour you only see by running the app (9), problems in the code itself (9), weak tests (2), and a vulnerable library (1). Several are in areas TestThisPuppy has no check for (cross-site scripting, vulnerable libraries), so the benchmark can show where it loses.

Tools, each in its usual free form, each set up the way its documentation describes:

| Tool | Kind | How it is run |
|---|---|---|
| TestThisPuppy | all-in-one: code, behaviour, attacks, race, tests of the tests | one settings file, internal mode (throwaway database) |
| SonarQube Community Build | code analysis | Docker server plus the Maven scanner |
| SpotBugs with FindSecBugs | code analysis (bug and security patterns) | Maven plugin |
| OWASP ZAP | attacks a running web app | Docker, full scan against the running app |
| OSV-Scanner | known-vulnerable libraries | Docker, against `pom.xml` |

TestThisPuppy is scored two ways:

- **Automatic**: only what it does with no expectations written by a person: the page list (which pages exist), the injection and race targets, the code check and test the tests.
- **With a written spec**: the above plus HTTP tests and macros that state what the app should do (`spec/`). This is the "people define correct, software proves it" idea, and it costs a person's time, which is reported.

## Scoring

- A **code finding** counts for a defect when it is in the defect's file, within `span` lines of its anchor, and its rule name or message contains one of the defect's keywords.
- A **running-app finding** (a TestThisPuppy report line, a ZAP alert) counts when it names the defect's endpoint (where one is given) and contains one of its keywords. Endpoints are compared on their words: text and endpoint are lower-cased and every character that is not a letter or digit becomes a space, so `/api/members` matches "POST api members".
- What counts as a finding, per tool: for TestThisPuppy, every **failed** report line (its kind, name and detail joined), every item in `code-fixes.md`, and every "not caught" line from test the tests; for SonarQube, every issue; for SpotBugs, every bug instance; for ZAP, every alert above informational (name, URL, parameter and evidence joined); for OSV-Scanner, every vulnerability.
- A **library finding** counts when it names the CVE or the library.
- Each defect counts at most once per tool. A finding that matches no defect is counted as **noise** (ZAP informational alerts are left out of noise).
- Also recorded: wall-clock time per tool, and how many separate tools and settings files it takes to cover the same ground.

## Known limits

- The same person wrote the bug zoo, the answer key and TestThisPuppy's checks. The anchors, keywords and spec are published so anyone can check them, add defects, and run it again.
- 21 defects in one small app is a sample, not a survey. Results say what each tool found here, not how good it is in general.
- Commercial products are not run (licences); they appear only in a separate feature table, from their public documentation, labelled as such.
