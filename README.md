# API Testing Framework

A modular, industry-pattern Java + RestAssured + TestNG framework for REST API test automation —
**multi-application by design**. The shared/core framework (config resolution, request building,
auth, HTTP execution, retry, validation, reporting) never knows which application is running; only
`-Dapp=` and the resource paths it selects know that. Today the repo ships two independent
applications proving that boundary holds:

| App | Target API | Domain |
|---|---|---|
| `appA` (default) | [reqres.in](https://reqres.in) | Users CRUD |
| `appB` | [Restful Booker](https://restful-booker.herokuapp.com) | Hotel booking lifecycle (auth, create/get/update/delete) |

## Setup

1. **Java 21** and Maven 3.8+.
2. App A needs a free API key (as of reqres.in's 2025 relaunch): sign up at
   **https://reqres.in/signup**, then either pass it ad-hoc:
   ```bash
   mvn clean test -Dapp=appA -Denv=qa -Dauth.api.key.value=YOUR_KEY
   ```
   or replace `REPLACE_WITH_YOUR_REQRES_API_KEY` in `src/test/resources/config/appA/*.properties`.
   Either way, **never commit a real key** — the checked-in files only ever hold the placeholder.
3. App B needs no key — Restful Booker's demo credentials (`admin`/`password123`, its own publicly
   documented test login) are already set in `src/test/resources/config/appB/*.properties`.

## Selecting an application

Every `mvn test` run resolves **which application** (`-Dapp=`) and **which environment**
(`-Denv=`) to run against; both default if omitted:

```bash
# Default: appA + qa (same as explicitly passing both)
mvn clean test

# Explicit application + environment
mvn test -Dapp=appA -Denv=qa
mvn test -Dapp=appB -Denv=qa

# Smoke / regression suites, per application
mvn test -Dapp=appA -Psmoke
mvn test -Dapp=appA -Pregression
mvn test -Dapp=appB -Psmoke
mvn test -Dapp=appB -Pregression
```

`-Dapp=` selects both the TestNG suite file (`suites/<app>/{testng,smoke,regression}.xml`) and the
configuration file (`config/<app>/<env>.properties`) — see **Project layout** and `DESIGN.md` for
how that resolution works. An unrecognized `-Dapp=` value fails suite resolution immediately with
a clear "suite file is not a valid file" error; it does not silently fall back to App A.

## Known external API limitations

Both applications currently have live-execution failures that are **external to this framework**,
not framework or configuration defects — verified by isolating each down to a plain curl/RestAssured
script outside this codebase:

- **App A / reqres.in:** the configured API key returns HTTP 403 `invalid_api_key`. Get a fresh key
  (see **Setup**) if you hit this — it means the key is invalid/revoked/quota-exhausted, not that
  the framework is broken.
- **App B / Restful Booker:** the public Heroku demo instance intermittently returns HTTP 418 to
  Java/RestAssured clients specifically (plain curl to the same endpoints does not reproduce it).
  This is why `suites/appB/*.xml` run sequentially (`thread-count="1"`) — a considerate default on a
  shared free instance, not a fix for the 418s, which persist regardless of concurrency. See the
  inline comment in `suites/appB/testng.xml` for the full investigation notes.

Config/service isolation, suite selection, and the auth layer are unaffected by either limitation —
`ConfigManagerTests`, `AppAServiceConfigTests`, `AppBServiceConfigTests`, and `AppBAuthTests` all
pass independently of both apps' live-API status.

## Changelog — fixes applied since the first draft

| Issue | Fix |
|---|---|
| `RestClient` constructor called a no-arg `.request()` method that doesn't exist on `RequestSpecification` — compile error | Removed; `RequestSpecFactory.createDefault()` already returns a built, usable spec |
| `logback.xml` used `<maxFileSize>` on a plain `TimeBasedRollingPolicy`, which doesn't support it | Switched to `SizeAndTimeBasedRollingPolicy` |
| reqres.in now requires a real `x-api-key` (2025 relaunch) — the placeholder key shipped originally always got a 401 | Config now requires a real key (see Setup); properties file only holds a placeholder |
| A couple of assertions assumed the old reqres.in response shape (empty 404 body, exact key set on create) | Updated to match current behavior: 404 returns `{}`, create response may include extra metadata fields |
| `RestClient` held a `ThreadLocal<RequestSpecification>` internally, seemingly to guard against threading issues | Reverted to a plain field on `RestClient` — see the row below for where the real fix belongs |
| **Critical**: `BaseTest.client` was a plain instance field. TestNG reuses *one instance* of each test class across all its `@Test` methods, even when `parallel="methods"` runs several of them concurrently on different threads — so two tests running at the same moment could overwrite each other's `client` reference and end up chaining `.pathParam()`/`.queryParam()` calls onto the *same* request spec. In practice this showed up as query/path parameters from one test leaking into another's request (e.g. a "get non-existent user" test silently hitting a real user's URL and getting 200 instead of 404) | `BaseTest.client` is now `ThreadLocal<RestClient>`-backed via a `client()` accessor, so each thread gets its own independent instance regardless of class-instance sharing. All test classes updated to call `client()` |
| `TestListener` called `ExtentTestManager.getTest().log(...)` directly; if a `@BeforeMethod` fails, TestNG fires `onTestSkipped` without ever having called `onTestStart`, so `getTest()` returns `null` → `NullPointerException` | Added a fallback that creates the Extent node on the fly if one doesn't exist yet |
| Request/response bodies were logged with a POJO's raw `toString()` instead of JSON, and headers (including `Authorization`/API keys) were logged unmasked | New shared `HttpLogFormatter`: pretty-prints JSON bodies and masks sensitive header values, used by both the SLF4J log filter and the Extent report filter |
| Extent report showed pass/fail only — no request/response detail | New `ExtentReportingFilter` (below) |

## Reports identify application, environment, class/method, and full request/response detail

Every generated `ExtentReport.html` is self-describing — a reviewer can tell what it's a report
*of* without knowing which CI job or local command produced it:

- The report's System/Environment panel states **Application** (`appA`/`appB`) and **Environment**
  (`dev`/`qa`/`stage`/`prod`) explicitly, set once via `ExtentManager.createInstance()` from
  `ConfigManager.getApplication()`/`getEnvironment()`.
- Every test node is named **`ClassName.methodName`** (e.g. `GetUsersTests.testGetSingleUserSuccess`),
  not just the bare method name — so a suite that mixes several test classes still reads unambiguously.
- Every test node is tagged with the active application as an Extent **category**
  (`assignCategory("appA")`/`("appB")`), making runs filterable/attributable in the report UI itself.
- `execution.log` (`target/logs/`) carries the same Application/Environment pair as a `BaseTest`
  banner line at the start of every suite, so the plain-text log matches the HTML report.

This is wired centrally in `listeners/TestListener.java` — individual test classes never call any
reporting API directly, so this applies automatically to every test, in every app, with no test-code
changes.

Independently of the above, every HTTP call made through `RestClient` appears as a collapsible step
under its test node — method, URL, headers (secrets masked), pretty-printed request body, status
code, duration, and pretty-printed response body. This comes from `filters/ExtentReportingFilter`,
registered alongside the existing SLF4J and Allure filters in `RequestSpecFactory`. Nothing needs to
change in test code — it's automatic for every request.

If a request retries (transient 5xx), each attempt gets its own step in the report, so you can see
exactly what came back on each try. Failure/exception detail (the full stack trace) is preserved
in the report node exactly as before these reporting changes — verified against a real generated
report, not just green tests.

## Why it's structured this way

| Concern | Where it lives | Why |
|---|---|---|
| Application + environment config | `config/ConfigManager.java` + `config/<app>/<env>.properties` | Switch application with `-Dapp=`, environment with `-Denv=`, override any single key with `-D` — `ConfigManager` is the *only* core class that ever reads `-Dapp` |
| Request building | `builders/RequestSpecFactory.java` | One place assembles base URI, timeouts, auth, filters — application-agnostic |
| Auth strategy | `auth/AuthProvider.java` | Swap Basic/Bearer/API-Key/OAuth2 via config, not code; no per-app branching |
| HTTP execution + retry | `clients/RestClient.java` | Fluent verbs (get/post/put/patch/delete); auto-retries transient 5xx |
| Assertions | `validators/ResponseValidator.java` | Chainable, descriptive failures with response body attached |
| Contract checks | `validators/SchemaValidator.java` | JSON Schema validation against files in `resources/schemas/<app>/` |
| Test data | `utils/RandomDataGenerator.java`, `dataproviders/DataProviders.java` (generic reader) + each app's own `apps/<app>/dataproviders/AppXDataProviders.java` | Faker-backed random data + JSON/CSV-driven data providers, kept per-app |
| Reporting | `reporting/`, `listeners/TestListener.java` | Allure + ExtentReports wired automatically via TestNG listener |
| Flaky-test resilience | `retry/RetryAnalyzer.java` + `retry/RetryListener.java` | Auto-applied retry on every `@Test`, capped by `test.retry.count` (independent from `http.retry.count`, which bounds `RestClient`'s transport-level 5xx retry) |
| Optional per-app API layer | `apps/<app>/api/*Api.java` (App B only) | Thin wrappers over `RestClient`, added only when a real multi-step workflow or reused call sequence justifies one — see **App A vs. App B: when to add an API layer** below |

## Project layout

```
src/main/java/com/framework/
  auth/          Authentication strategies (Basic, Bearer, API Key, OAuth2, Digest) — shared, app-agnostic
  builders/      RequestSpecification factory (createDefault / forService) — shared
  clients/       RestClient — fluent HTTP verb wrapper with retry — shared
  config/        AppConfig (OWNER) + ConfigManager (app+env resolution) + Environment enum + ServiceConfig — shared
  constants/     HttpMethod, FrameworkConstants — shared
  dataproviders/ DataProviders — generic @DataProvider file-reading helper only — shared
  exceptions/    FrameworkException, ApiException, ValidationException — shared
  filters/       SLF4J request/response log filter + Extent report filter — shared
  listeners/     TestListener — bridges TestNG lifecycle to Extent + Allure — shared
  reporting/     ExtentManager (suite-level) + ExtentTestManager (thread-local) — shared
  retry/         RetryAnalyzer + RetryListener (auto-applied to all tests) — shared
  utils/         JsonUtils, RandomDataGenerator, FileReaderUtils, CsvUtils, HttpLogFormatter — shared
  validators/    ResponseValidator (fluent assertions), SchemaValidator (JSON Schema) — shared

  apps/appA/                 Users API (reqres.in) — no API-layer needed (see below)
    endpoints/UserEndpoints.java
    models/request/, models/response/
  apps/appB/                 Restful Booker — has a thin API layer (multi-step booking lifecycle)
    endpoints/AuthEndpoints.java, BookingEndpoints.java
    models/request/, models/response/
    api/AuthApi.java, BookingApi.java

src/test/java/com/framework/
  base/BaseTest.java          Shared setup/teardown for all test classes — app-agnostic
  config/ConfigManagerTests.java   Deterministic config-isolation coverage for both apps, no HTTP
  apps/appA/
    dataproviders/AppADataProviders.java   App A's own @DataProvider methods
    tests/                                 GetUsers/CreateUser/UpdateUser/DeleteUser/Auth/ServiceClient/AppAServiceConfig
  apps/appB/
    dataproviders/AppBDataProviders.java   App B's own @DataProvider methods
    tests/                                 AppBAuth/AppBGetBooking/AppBCreateBooking/AppBUpdateBooking/AppBDeleteBooking/AppBBookingLifecycle/AppBServiceConfig

src/test/resources/
  config/
    default.properties       Framework-wide fallback (any key an app+env file doesn't set)
    appA/{dev,qa,stage,prod}.properties
    appB/{dev,qa,stage,prod}.properties
  schemas/appA/, schemas/appB/     JSON Schema files for contract validation, per app
  testdata/appA/, testdata/appB/   JSON/CSV data-provider fixtures, per app
  suites/appA/{testng,smoke,regression}.xml
  suites/appB/{testng,smoke,regression}.xml
```

Adding a third application means adding `apps/appC/` (main + test), `config/appC/`, `schemas/appC/`,
`testdata/appC/`, and `suites/appC/` — see `DESIGN.md` for the full walkthrough. Nothing listed above
as "shared" changes; core never learns a new application's name.

### App A vs. App B: when to add an API layer

An `apps/<app>/api/*Api.java` layer (thin wrappers over `RestClient`) is **optional** and only
justified when an application has repeated endpoint/parameter-building logic, a multi-step workflow
(e.g. authenticate → use the result in a later call), or enough endpoint volume that direct calls
from test code become hard to audit.

- **App A** deliberately has none — its five endpoints are simple, independent CRUD calls, so test
  classes call `client()` directly against `UserEndpoints`.
- **App B** has `AuthApi` and `BookingApi` — Restful Booker's booking lifecycle is inherently
  multi-step (obtain an auth token, then use it across create/get/update/delete calls), and that
  sequence is reused across several test classes.

## Prerequisites

- Java 21, Maven 3.8+ (see **Setup** above for the required reqres.in API key)
- Allure CLI (optional, for `allure serve`) — https://docs.qameta.io/allure/#_installing_a_commandline

## Running tests

Three suite variants exist per application (`suites/<app>/{testng,smoke,regression}.xml`), scoped by
TestNG `groups`, not by different code:

| Suite | Selected by | What it runs |
|---|---|---|
| **Full/master** | `mvn clean test` (no profile) | Every class in that application's suite, no group filter — the complete TestNG configuration for that app |
| **Smoke** | `-Psmoke` | A small subset of classes, `groups` filtered to `smoke` only — quick validation of the critical paths |
| **Regression** | `-Pregression` | The full class list, `groups` filtered to `smoke`+`regression`+`negative` — broader coverage, including the app-agnostic `ConfigManagerTests`/`App<X>ServiceConfigTests` |

```bash
# Full master suite (default app = appA, default env = qa)
mvn clean test

# Specific application + environment
mvn clean test -Dapp=appA -Denv=dev
mvn clean test -Dapp=appB -Denv=qa

# Smoke suite only (via Maven profile), per application
mvn clean test -Dapp=appA -Psmoke
mvn clean test -Dapp=appB -Psmoke

# Regression suite, per application
mvn clean test -Dapp=appA -Pregression
mvn clean test -Dapp=appB -Pregression

# Override a single config value ad-hoc
mvn clean test -Dapp=appA -Denv=qa -Dbase.url=https://reqres.in
```

## Local validation

Before opening a PR, run the repository's deterministic health checks from the repository root:

```bash
./tools/validate-framework.sh
```

It's a static, non-AI script — it never runs tests and never modifies a file. It checks a handful
of mechanical invariants that are easy to introduce by hand and easy to miss in review: no
app-specific branching in shared/core code, every TestNG suite XML registering both required
listeners, every suite's referenced test classes resolving to a real source file, every
`services.<name>.<key>` config key using a suffix `ServiceConfig` actually reads, and every
`@Test`-bearing class under `apps/<app>/tests/` being registered in at least one of that
application's suite XML files (registration in any one of `testng.xml`/`smoke.xml`/`regression.xml`
is sufficient). Exit code `0` and `Checks: 5, Failures: 0` means all five passed. See
[`docs/AI/REVIEW_CHECKLIST.md`](docs/AI/REVIEW_CHECKLIST.md) for how it fits into the full review
process.

## Continuous Integration

`.github/workflows/tests.yml` runs two independent jobs — see `DESIGN.md`'s **CI/CD flow** for the
full breakdown:

- **PR / push to `main`** → the `smoke` job, matrixed over `app: [appA, appB]` with
  `fail-fast: false` — each application's smoke suite runs as its own independent check, so App A's
  known reqres.in failures never hide whether App B's suite ran at all, and vice versa.
- **Nightly (cron) / manual dispatch** → the `regression` job — runs the regression suite, but
  **only against App A** (it doesn't pass `-Dapp=`, so it falls through to the pom default); it
  isn't multi-app-matrixed yet. `workflow_dispatch` lets you pick `env`/`suite` manually, but not
  `app`.
- Every job uploads `allure-results`, `extent-reports`, and `surefire-reports` as build artifacts,
  always — even on failure.

## Reports

**Allure** (rich, interactive, step-by-step request/response history):
```bash
mvn allure:serve
# or, after a run:
allure serve target/allure-results
```

**ExtentReports** (single self-contained HTML file, good for emailing/CI artifacts):
```
target/extent-reports/ExtentReport.html
```

Both are generated automatically on every run — no extra flags needed.

## Writing a new test

1. Add/extend a POJO in `apps/<app>/models/request` or `apps/<app>/models/response` if needed.
2. Add the endpoint path to the relevant class in `apps/<app>/endpoints/`.
3. Extend `BaseTest`, use the pre-built `client()` (a `RestClient`) — same for every app:

```java
// App A (apps/appA/tests) — direct endpoint call, no API layer
@Test(groups = {"smoke"}, description = "GET single user returns 200")
public void testGetUser() {
    Response response = client().pathParam("id", 2).get(UserEndpoints.USER_BY_ID);

    ResponseValidator.of(response)
        .assertStatusCode(200)
        .assertJsonValue("data.id", equalTo(2));

    SchemaValidator.validate(response, "appA/single_user_schema.json");
}
```

```java
// App B (apps/appB/tests) — through the thin BookingApi wrapper
@Test(groups = {"smoke"}, description = "GET booking by id returns 200")
public void testGetBookingByIdSuccess() {
    Response response = bookingApi.getBooking(existingBookingId);

    ResponseValidator.of(response).assertStatusCode(200);
    SchemaValidator.validate(response, "appB/booking_schema.json");
}
```

4. Tag with TestNG `groups` (`smoke`, `regression`, `negative`) so it's picked up by the right suite,
   and add the class to the relevant `<classes>` block in `suites/<app>/*.xml`.
5. Need data-driven input? Add a `@DataProvider` in `apps/<app>/dataproviders/App<X>DataProviders.java`
   (or reuse an existing JSON/CSV fixture under `testdata/<app>/`) and reference it via `dataProviderClass`.

## Adding a new application

See `DESIGN.md` for the full walkthrough. In short: a new application needs its own
`config/<app>/*.properties`, `apps/<app>/{endpoints,models}` (and, only if justified, `apps/<app>/api`),
`apps/<app>/dataproviders`, `apps/<app>/tests`, `schemas/<app>/`, `testdata/<app>/`, and
`suites/<app>/{testng,smoke,regression}.xml` — nothing under the shared packages listed in
**Project layout** changes, and no core class ever needs to learn the new app's name.

## Extending to a real project

- Point `base.url` in the relevant `config/<app>/*.properties` at your service.
- Set `auth.type` (`NONE`, `BASIC`, `BEARER_TOKEN`, `API_KEY`, `OAUTH2`, `DIGEST`) and fill in the matching `auth.*` keys.
- Add your own request/response POJOs and JSON Schemas.
- CI: run `mvn clean test -Dapp=appA -Denv=qa` then archive `target/allure-results` and `target/extent-reports` as build artifacts; feed `allure-results` into an Allure Jenkins/GitHub Action plugin for trend history.

## Key design decisions worth knowing

- **Retry only on 5xx**, never on 4xx — a 404/400 is the system under test telling you something real; masking it with a retry would hide genuine bugs.
- **RetryAnalyzer is auto-attached** to every `@Test` via `IAnnotationTransformer`, so test authors don't repeat `retryAnalyzer = RetryAnalyzer.class` everywhere.
- **ExtentTest is ThreadLocal**, `ExtentReports` itself is not — because parallel TestNG threads must not cross-log into each other's report nodes, but the underlying report writer is thread-safe and shouldn't be duplicated per thread.
- **`RestClient` itself is a plain field, not ThreadLocal** — safe *because* `BaseTest.client()` is ThreadLocal-backed, guaranteeing each thread already owns an independent `RestClient` instance. If you ever refactor test setup so a `RestClient` could be shared across threads again, it needs the ThreadLocal treatment back.
- **Sensitive headers are masked in every log/report** — `Authorization`, API keys, tokens, and cookies show as `abcd****(masked)` in both the SLF4J logs and the Extent report, so neither becomes a place secrets leak from.
- **Config resolution order**: `-D` system property → env-specific `.properties` → `default.properties`, so CI can override one value without touching files.
