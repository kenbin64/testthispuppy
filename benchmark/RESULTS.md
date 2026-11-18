# TestThisPuppy benchmark: results

Run on 2026-11-18. Method, answer key and TestThisPuppy's settings were committed before any tool ran (commit f6c8f9a). Scores come from `score.py` applying the rules in `METHOD.md`, then a hand audit (`audit.json`) applied the same way to every tool; both numbers are shown. Raw outputs are in `results/raw/`.

## Scores

Bug zoo: a small Spring Boot shop with 21 defects planted on purpose: 9 behaviour (seen only by running the app), 9 in the code, 2 weak tests, 1 vulnerable library.

| Tool | Defects found (audited) | Behaviour | Code | Weak tests | Library | Time |
|---|---|---|---|---|---|---|
| TestThisPuppy, with a written spec | **12 / 21** | 6/9 | 4/9 | 2/2 | 0/1 | 49 s |
| TestThisPuppy, automatic | **10 / 21** | 4/9 | 4/9 | 2/2 | 0/1 | 43 s |
| SonarQube Community, comprehensive profile | 8 / 21 | 1/9 | 7/9 | 0/2 | 0/1 | 13 s scan + 40 s server start |
| SpotBugs + FindSecBugs | 6 / 21 | 1/9 | 5/9 | 0/2 | 0/1 | 14 s |
| SonarQube Community, default profile | 4 / 21 | 1/9 | 3/9 | 0/2 | 0/1 | 15 s scan + 40 s server start |
| OWASP ZAP, full scan | 3 / 21 | 3/9 | 0/9 | 0/2 | 0/1 | 242 s |
| OSV-Scanner | 1 / 21 | 0/9 | 0/9 | 0/2 | 1/1 | 21 s |

- All the other tools together: **12 / 21**. Adding TestThisPuppy: **16 / 21** (automatic) or **18 / 21** (with spec).
- Found by no tool: stack traces sent to the browser (B8), an UPDATE without WHERE that renames every product (B9), and a possible null dereference (C7).
- TestThisPuppy's times include creating a throwaway database, starting the app, the browser tests, the code check and test the tests, and deleting the database.
- The full defect-by-tool table is in `results/SCORES.md`.

## What it took to run

| | TestThisPuppy | The four other tools together |
|---|---|---|
| Tools to install and learn | 1 | 4 (SonarQube server, SpotBugs plus a plugin jar, ZAP, OSV-Scanner) |
| Settings | 1 settings file and a page list; the spec run adds 1 file of HTTP checks and 1 macro | Sonar: server, admin password, token, choice of profile; SpotBugs: plugin path; ZAP: the app and a database started and filled by hand first; OSV: none |
| Running app and database | created and deleted by the tool | started by hand for ZAP |
| One report | JSON, HTML and a fix list | four separate outputs in four formats |
| Total time | 43 to 49 s | about 332 s of tool time, plus manual setup |

## What the numbers say

1. **The tools see different things.** Code analysers find code bugs; only tools that run the app find behaviour bugs; only test-the-tests finds weak tests. The overlap is small: only the SQL injection was found by five tools.
2. **Running the app matters most here.** TestThisPuppy found 4 to 6 of the 9 behaviour defects: the race condition, the email check, the missing-member crash, the injection, and with a spec the email dropped on save and quantity 0 being accepted. ZAP found 3, all security; the code-only tools found only the injection, by reading the code.
3. **Writing down what "correct" means paid off.** Five HTTP lines and one macro (the spec) found two more real bugs that no tool found on its own. That is the idea in one measurement: a person defines correct, software proves it.
4. **On code alone, the specialists win.** SonarQube's comprehensive profile found 7 of 9 code defects; TestThisPuppy's PMD rules found 4. It missed the hard-coded password, the shared date formatter and the Math.abs on a hash code, which SonarQube and SpotBugs both caught.
5. **Only TestThisPuppy found the weak tests** (2 of 2), because no other tool here tests the tests.
6. **Gaps TestThisPuppy has no check for at all:** cross-site scripting (ZAP found it) and vulnerable libraries (OSV-Scanner found it, plus 88 real known vulnerabilities in the Spring Boot 3.5.0 libraries the app is built on).

## Limits

- The same person wrote the bug zoo, the answer key and TestThisPuppy. Everything is published so it can be checked, extended with new defects, and run again.
- 21 defects in one small app is one sample, not a verdict on any tool in general.
- The keyword rules made mistakes in both directions; the audit removed 6 wrong credits (5 from TestThisPuppy across its two runs, 1 from ZAP) and added 4 missed ones (SonarQube and SpotBugs), each with its reason in `audit.json`.
- "Outside the key" counts are not false alarms: most are genuine (real CVEs, real untested code, real style issues, missing security headers).
- Commercial products were not run. Their features are compared in `COMPARISON.md`, from their public documentation.

## What it shows TestThisPuppy should do next

Ranked by defects they would have caught here (estimates, not yet measured):

1. Add SpotBugs with FindSecBugs as a second code engine beside PMD (C8, C9, and the injection statically).
2. Add a library check by running OSV-Scanner (X1).
3. Add a cross-site scripting check to the browser attacks (B7).
4. Check error pages for stack traces (B8).
5. Let a spec say "these other rows must not change" (B9).
6. Fix a bug the benchmark exposed: values filled into a URL (`{member.name}`) are not URL-encoded, so a name with a space broke one spec line.

The pattern of the first four is the same as the rest of the suite: run an established tool, and fold its findings into one report.
