# Feature comparison with popular products

From each product's public documentation, pricing pages and official repositories, checked 2026-11-17/18. Not run (licences), so this compares stated features, not results. Y = yes, P = partial, N = no, NC = not confirmed from an official source. Sources are listed below the table.

| Product | Code analysis | Browser/UI | API/HTTP | Test database isolation or verified restore | Security: running app / libraries | Tests the tests (mutation) | Race testing | AI | Open source | Price (published) |
|---|---|---|---|---|---|---|---|---|---|---|
| **TestThisPuppy** | Y (PMD rules, Java) | Y (Selenium: happy path, wrong input, injection, macros) | Y | **Y: throwaway database, or snapshot, restore and verify** | P: SQL injection only; no XSS, no library scan | **Y (PIT)** | **Y (simultaneous requests)** | Optional: explains failures | Y | Free |
| SonarQube Community Build | Y (40+ languages) | N | N | N | P: no injection detection or library scan in Community | N | P: static thread-safety rules only | N | Y (LGPL-3.0) | Free |
| SonarQube Cloud / Server | Y | N | N | N | P: static injection analysis; library scan is a separate add-on | N | P: static rules | Y: AI CodeFix | N | Cloud free to 50k lines; Team $34/mo |
| Tricentis Tosca | N | Y (codeless, model-based) | Y | P: test data management | NC | N | NC | Y: self-healing, agentic | N | Quote |
| mabl | N | Y (agent builds and heals tests) | Y | P: query steps against a live database, no isolation | N | N | N | Y | N | Quote |
| Katalon | N | Y (record and playback, low-code) | Y | P: database as a data source | NC | N | N | Y: self-healing, generation | N | Free; Pro $1,800/seat/yr |
| Selenium | N | P (framework; you write tests) | N | N | N | N | N | NC | Y (Apache-2.0) | Free |
| Playwright | N | Y (framework plus recorder) | Y | N | N | N | N | Y: test agents | Y (Apache-2.0) | Free |
| Cypress | N | Y (framework) | Y | N | N | N | N | Y | App Y (MIT) | Free; Cloud Team $67/mo |
| Postman | N | N | Y | N | P: secret scanning | N | N | Y | N | Free; Team $19/user/mo |
| Testcontainers | N | N | N | **Y: throwaway real database per run** | N | N | N | N | Y (MIT) | Free |
| PIT / Stryker | N | N | N | N | N | **Y** | N | N | Y (Apache-2.0) | Free |
| OWASP ZAP | N | N | P (as a proxy) | N | **Y: running app (SQL injection, XSS)** | N | N | NC | Y (Apache-2.0) | Free |
| Snyk | Y | N | Y | N | **Y: running app and libraries** | N | N | Y: Agent Fix | N | Free tier; Team from $25/mo |
| GitHub Advanced Security / CodeQL | Y | N | N | N | P: code and secrets; Dependabot for libraries | N | NC | Y: Copilot Autofix | N | $19 to $30 per committer/mo; free on public repos |
| Diffblue Cover | N | N | N | N | N | N | N | Y (reinforcement learning writes Java unit tests) | N | Community free |

## Where TestThisPuppy is different, and where it is not

- **Different:** none of the products above documents running a verified database restore, browser checks for wrong input and injection, a race test and mutation testing together in one run. Its pieces exist separately (Testcontainers, PIT, ZAP), which is what it builds on.
- **Not unique:** turning findings into fixes for an AI is now common (SonarQube AI CodeFix, Snyk Agent Fix, Copilot Autofix, Playwright's healer). TestThisPuppy has no SARIF output, the format code-scanning tools exchange.
- **Behind the specialists:** SonarQube and CodeQL on code analysis and languages; ZAP and Snyk on security; Playwright and Cypress on browser tooling; the commercial platforms on codeless authoring and self-healing tests.
- **Fair summary:** a free, all-in-one starting point with a few distinct checks (verified database restore, race test, test the tests), each other part shallower than the specialist tool it bundles. The benchmark (`RESULTS.md`) shows the combination finding more than the specialists separately on one sample app.

## Sources

- SonarQube: sonarsource.com/plans-and-pricing; docs.sonarsource.com/sonarqube-community-build/feature-comparison-table; github.com/SonarSource/sonarqube; rules.sonarsource.com/java/rspec-2885
- Tricentis Tosca: tricentis.com/products/automate-continuous-testing-tosca (and /features); docs.tricentis.com/tosca-2026.1 AI data usage policy
- mabl: mabl.com/pricing; help.mabl.com "How to test with database queries"; help.mabl.com/docs/api-steps
- Katalon: katalon.com/pricing; docs.katalon.com test suite reports
- Selenium: github.com/SeleniumHQ/selenium; selenium.dev/documentation/ide
- Playwright: playwright.dev/docs/api-testing, /codegen, /test-agents, /test-reporters
- Cypress: cypress.io/pricing; docs.cypress.io/api/commands/request
- Postman: postman.com/pricing
- Testcontainers: java.testcontainers.org/modules/databases; github.com/testcontainers/testcontainers-java
- PIT: pitest.org/quickstart/maven; github.com/hcoles/pitest. Stryker: stryker-mutator.io/docs
- OWASP ZAP: zaproxy.org/docs/alerts; zaproxy.org/docs/desktop/addons/report-generation
- Snyk: snyk.io/plans; docs.snyk.io (Snyk API & Web; Snyk Code automatic fixes)
- GitHub Advanced Security: github.com/security/advanced-security; docs.github.com "About GitHub Advanced Security"
- Diffblue Cover: cover-docs.diffblue.com; diffblue.com/resources (plans and usage limits)
