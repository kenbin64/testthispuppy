# Benchmark scores

Bug zoo: 21 planted defects (9 behaviour, 9 code, 2 tests, 1 dependency).
Scored by `score.py` against `defects.json` with the rules in `METHOD.md`.

Found = after the hand audit in `audit.json` (every change has a reason); the mechanical score from the keyword rules alone is shown beside it.

| Tool | Found (audited) | Mechanical | Behaviour | Code | Tests | Dependency | Findings | Outside the key |
|---|---|---|---|---|---|---|---|---|
| TestThisPuppy (automatic) | **10 / 21** | 13 | 4/9 | 4/9 | 2/2 | 0/1 | 172 | 156 |
| TestThisPuppy (with spec) | **12 / 21** | 14 | 6/9 | 4/9 | 2/2 | 0/1 | 177 | 157 |
| TestThisPuppy v2 (automatic) | **15 / 21** | 17 | 6/9 | 6/9 | 2/2 | 1/1 | 205 | 179 |
| TestThisPuppy v2 (with spec) | **18 / 21** | 18 | 9/9 | 6/9 | 2/2 | 1/1 | 210 | 179 |
| SonarQube (default profile) | **4 / 21** | 3 | 1/9 | 3/9 | 0/2 | 0/1 | 5 | 2 |
| SonarQube (comprehensive profile) | **8 / 21** | 6 | 1/9 | 7/9 | 0/2 | 0/1 | 15 | 9 |
| SpotBugs + FindSecBugs | **6 / 21** | 5 | 1/9 | 5/9 | 0/2 | 0/1 | 12 | 7 |
| OWASP ZAP | **3 / 21** | 4 | 3/9 | 0/9 | 0/2 | 0/1 | 17 | 4 |
| OSV-Scanner | **1 / 21** | 1 | 0/9 | 0/9 | 0/2 | 1/1 | 89 | 88 |

## Which tool found which defect (audited)

| Defect | TestThisPuppy (automatic) | TestThisPuppy (with spec) | TestThisPuppy v2 (automatic) | TestThisPuppy v2 (with spec) | SonarQube (default profile) | SonarQube (comprehensive profile) | SpotBugs + FindSecBugs | OWASP ZAP | OSV-Scanner |
|---|---|---|---|---|---|---|---|---|---|
| B1 member form silently drops the email |  | yes |  | yes |  |  |  |  |  |
| B2 order quantity 0 accepted (boundary) |  | yes |  | yes |  |  |  |  |  |
| B3 no server-side email validation | yes | yes | yes | yes |  |  |  |  |  |
| B4 order for a missing member is a 500 crash | yes | yes | yes | yes |  |  |  | yes |  |
| B5 member number assigned as max+1 (race: concurrent creates collide) | yes | yes | yes | yes |  |  |  |  |  |
| B6 SQL injection in member search | yes | yes | yes | yes | yes | yes | yes | yes |  |
| B7 reflected cross-site scripting on /hello |  |  | yes | yes |  |  |  | yes |  |
| B8 stack traces sent to the browser on errors |  |  | yes | yes |  |  |  |  |  |
| B9 renaming one product renames every product (UPDATE without WHERE) |  |  |  | yes |  |  |  |  |  |
| C1 exception swallowed by an empty catch block |  |  |  |  |  | yes |  |  |  |
| C2 file reader never closed (resource leak) | yes | yes | yes | yes |  |  | yes |  |  |
| C3 equals without hashCode | yes | yes | yes | yes |  | yes | yes |  |  |
| C4 strings compared with == | yes | yes | yes | yes | yes | yes | yes |  |  |
| C5 hard-coded password |  |  |  |  | yes | yes |  |  |  |
| C6 overly complex method | yes | yes | yes | yes |  | yes |  |  |  |
| C7 possible null dereference |  |  |  |  |  |  |  |  |  |
| C8 shared static SimpleDateFormat (not thread safe) |  |  | yes | yes | yes | yes | yes |  |  |
| C9 Math.abs(hashCode()) % n can be negative |  |  | yes | yes |  | yes | yes |  |  |
| T1 a test that runs the price code but checks almost nothing | yes | yes | yes | yes |  |  |  |  |  |
| T2 discount threshold boundary never tested | yes | yes | yes | yes |  |  |  |  |  |
| X1 vulnerable library: commons-text 1.9 (CVE-2022-42889, Text4Shell) |  |  | yes | yes |  |  |  |  | yes |

All the other tools together: 12 / 21.
Those tools plus TestThisPuppy (automatic): 16 / 21.
Those tools plus TestThisPuppy (with spec): 18 / 21.
Found by no tool: C7.
