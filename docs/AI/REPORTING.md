# Reporting

How this framework's reporting is wired, so you can explain a generated report or extend it
without duplicating logic. The implementation lives in protected files
(`reporting/ExtentManager.java`, `reporting/ExtentTestManager.java`,
`reporting/AllureEnvironmentWriter.java`, `listeners/TestListener.java`,
`filters/ExtentReportingFilter.java`) — see [`../../AGENTS.md`](../../AGENTS.md) before changing
any of them.

## Two independent backends

- **Allure** — populated automatically via `allure-testng` + the `AllureRestAssured` RestAssured
  filter (registered in `RequestSpecFactory`). Class name, thread name, suite hierarchy, and
  `@Epic` groupings (`"Users API"` / `"Restful Booker API"`) come from the integration itself, no
  custom code required. Its Environment widget is populated by `AllureEnvironmentWriter`, described
  below.
- **ExtentReports** — a single self-contained HTML file
  (`target/extent-reports/ExtentReport.html`, path driven by `report.extent.path`), built and
  populated by this framework's own code, described below.

Both are generated on every run automatically — no extra flags needed. Removing one filter/backend
doesn't affect the other; they're wired independently in `RequestSpecFactory`, and
`AllureEnvironmentWriter`'s call site in `TestListener` is a separate call from `ExtentManager`'s
own system-info setup — dropping either one doesn't affect the other backend.

## What's in the current ExtentReports output

1. **Report-level system info**, set once in `ExtentManager.createInstance()`:
   - `Framework` = "REST Assured + TestNG", `Reporting` = "ExtentReports" (static)
   - `Application` = `ConfigManager.getApplication()` (`appA`/`appB`)
   - `Environment` = `ConfigManager.getEnvironment().getValue()` (`dev`/`qa`/`stage`/`prod`)

   This is what makes a generated report self-describing — a reviewer can tell what it's a report
   *of* without knowing which CI job or local command produced it.

2. **Per-test node naming**, in `TestListener.startTestNode()`: `ClassName.methodName`
   (e.g. `GetUsersTests.testGetSingleUserSuccess`), not the bare method name — necessary once a
   suite mixes more than one test class.

3. **Application category tag**, same method: `assignCategory(ConfigManager.getApplication())` —
   makes runs filterable/attributable in the Extent UI itself.

4. **Pass/fail/skip capture**, via `TestListener` implementing `ITestListener` — individual test
   classes never call any Extent/Allure API directly. Includes a fallback
   (`ensureTestNode`) that creates the Extent node on the fly if `onTestStart` never ran (happens
   when a `@BeforeMethod`/`@BeforeClass` failure causes TestNG to skip straight to
   `onTestSkipped`), so a skip never NPEs.

5. **Per-request detail**, via `ExtentReportingFilter` (a RestAssured `Filter`, registered
   alongside the Allure and SLF4J filters in `RequestSpecFactory`): every HTTP call made through
   `RestClient` gets its own collapsible child node under the test — method, URI, headers
   (secrets masked), pretty-printed request body, status code (color-coded green/orange/red),
   duration, pretty-printed response body. If a request retries (transient 5xx), each attempt gets
   its own node, so you can see exactly what came back on each try. If reporting itself throws for
   any reason, the filter catches it and logs a warning to the test node rather than failing the
   test — reporting must never break a test run.

6. **Execution log banner**, in `BaseTest.beforeSuite()`: the same Application/Environment pair
   (plus Base URL, Auth Type, retry counts) is logged via SLF4J at the start of every suite, so
   `target/logs/*.log` matches the HTML report without needing to open it.

7. **Secret masking**, via the shared `utils/HttpLogFormatter`, used by both
   `filters/RequestResponseLoggingFilter` (SLF4J) and `ExtentReportingFilter` — `Authorization`,
   API keys, tokens, and cookies render as `abcd****(masked)` in every log and report. This is
   shared logic, not duplicated per backend; don't reimplement masking in a new filter.

## What's in the current Allure output

Beyond what `allure-testng`/`AllureRestAssured` populate automatically (see above),
`AllureEnvironmentWriter.write()` — called once from `TestListener.onStart()`, before any
`@Test` method runs — writes `Application`/`Environment` (the same two facts `ExtentManager`
reads via `ConfigManager`) to `<allure-results>/environment.properties`. Allure only reads this
file at report-generation time (`allure serve`, `mvn allure:report`, or an external Allure
plugin/server consuming a downloaded CI artifact), so write timing within a suite run doesn't
matter — it only needs to exist in the results directory by then. Any failure to write it is
logged as a warning and never fails the test run, the same "reporting must never break a test
run" convention `ExtentReportingFilter` already follows. Verified by the network-free
`AllureEnvironmentWriterTests` (`src/test/java/com/framework/reporting/`), registered in
`testng.xml`/`regression.xml` for both apps — the same suites `ConfigManagerTests` is registered
in, and not `smoke.xml`, since smoke stays scoped to each app's own critical-path checks.

## Adding a new application's reporting

Nothing — this is the point of the multi-app design. `ExtentManager` reads
`ConfigManager.getApplication()`/`getEnvironment()` generically, so a new application's reports are
self-describing automatically. Do not add `if (app.equals("appC"))` anywhere in the reporting
layer to special-case a new application's report output.

## If you need to extend reporting

- Prefer adding to `TestListener` (which already owns the TestNG lifecycle call sites) over
  changing `ExtentTestManager` — `ExtentTestManager` already exposes `startTest(name, description)`
  and `getTest()`, which has been sufficient for every reporting change made so far, including
  node-naming and category tagging.
- If a change requires reading a config value application-specifically inside the reporting layer,
  that's a signal you actually need a new config key (read generically via `ConfigManager`), not a
  branch on the application name — see [`../../AGENTS.md`](../../AGENTS.md) rule 1.
- These files are protected — get explicit approval before changing them, and update this doc plus
  `README.md`'s reporting section and `DESIGN.md`'s **Reporting and logging** section in the same
  change, per rule 7.
