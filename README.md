# API Testing Framework

A modular, industry-pattern Java + RestAssured + TestNG framework for REST API test automation.
Demo tests run against [reqres.in](https://reqres.in) — as of its 2025 relaunch this now requires
a free personal API key (see **Setup** below), so this is a one-time step, not optional.

## Setup

1. **Java 21** and Maven 3.8+.
2. Get a free API key at **https://reqres.in/signup**.
3. Set it without editing any file:
   ```bash
   mvn clean test -Denv=qa -Dauth.api.key.value=YOUR_KEY
   ```
   or replace `REPLACE_WITH_YOUR_REQRES_API_KEY` in `src/test/resources/config/*.properties`.
   Either way, **never commit a real key** — the checked-in files only ever hold the placeholder.

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

## Extent reports now show full request/response detail

Every HTTP call made through `RestClient` now appears as a collapsible step under its test in the
Extent HTML report — method, URL, headers (secrets masked), pretty-printed request body, status
code, duration, and pretty-printed response body. This comes from a new filter,
`filters/ExtentReportingFilter`, registered alongside the existing SLF4J and Allure filters in
`RequestSpecFactory`. Nothing needs to change in test code — it's automatic for every request.

If a request retries (transient 5xx), each attempt gets its own step in the report, so you can see
exactly what came back on each try.

## Why it's structured this way

| Concern | Where it lives | Why |
|---|---|---|
| Environment config | `config/AppConfig.java` + `config/*.properties` | Switch `dev/qa/stage/prod` with `-Denv=`, override any single key with `-D` |
| Request building | `builders/RequestSpecFactory.java` | One place assembles base URI, timeouts, auth, filters |
| Auth strategy | `auth/AuthProvider.java` | Swap Basic/Bearer/API-Key/OAuth2 via config, not code |
| HTTP execution + retry | `clients/RestClient.java` | Fluent verbs (get/post/put/patch/delete); auto-retries transient 5xx |
| Assertions | `validators/ResponseValidator.java` | Chainable, descriptive failures with response body attached |
| Contract checks | `validators/SchemaValidator.java` | JSON Schema validation against files in `resources/schemas/` |
| Test data | `utils/RandomDataGenerator.java`, `dataproviders/DataProviders.java` | Faker-backed random data + JSON/CSV/Excel-driven data providers |
| Reporting | `reporting/`, `listeners/TestListener.java` | Allure + ExtentReports wired automatically via TestNG listener |
| Flaky-test resilience | `retry/RetryAnalyzer.java` + `retry/RetryListener.java` | Auto-applied retry on every `@Test`, capped by `max.retry.count` |

## Project layout

```
src/main/java/com/framework/
  auth/          Authentication strategies (Basic, Bearer, API Key, OAuth2, Digest)
  builders/      RequestSpecification factory
  clients/       RestClient — fluent HTTP verb wrapper with retry
  config/        AppConfig (OWNER) + ConfigManager + Environment enum
  constants/     HttpMethod, FrameworkConstants
  dataproviders/ Reusable TestNG @DataProvider sources (JSON/CSV/Excel)
  endpoints/     Endpoint path constants
  exceptions/    FrameworkException, ApiException, ValidationException
  filters/       SLF4J request/response log filter + Extent report filter
  listeners/     TestListener — bridges TestNG lifecycle to Extent + Allure
  models/        Request/response POJOs (Lombok + Jackson)
  reporting/     ExtentManager (suite-level) + ExtentTestManager (thread-local)
  retry/         RetryAnalyzer + RetryListener (auto-applied to all tests)
  utils/         JsonUtils, RandomDataGenerator, FileReaderUtils, CsvUtils, ExcelUtils, HttpLogFormatter
  validators/    ResponseValidator (fluent assertions), SchemaValidator (JSON Schema)

src/test/java/com/framework/
  base/BaseTest.java      Shared setup/teardown for all test classes
  tests/                  Demo test classes (Get/Create/Update/Delete/Auth)

src/test/resources/
  config/{dev,qa,stage,prod,default}.properties
  schemas/                JSON Schema files for contract validation
  testdata/               JSON/CSV data-provider fixtures
  suites/{testng,smoke,regression}.xml
```

## Prerequisites

- Java 21, Maven 3.8+ (see **Setup** above for the required reqres.in API key)
- Allure CLI (optional, for `allure serve`) — https://docs.qameta.io/allure/#_installing_a_commandline

## Running tests

```bash
# Full master suite (default env = qa)
mvn clean test

# Specific environment
mvn clean test -Denv=dev

# Smoke suite only (via Maven profile)
mvn clean test -Psmoke

# Regression suite
mvn clean test -Pregression

# Override a single config value ad-hoc
mvn clean test -Denv=qa -Dbase.url=https://reqres.in
```

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

1. Add/extend a POJO in `models/request` or `models/response` if needed.
2. Add the endpoint path to the relevant class in `endpoints/`.
3. Extend `BaseTest`, use the pre-built `client` (a `RestClient`):

```java
@Test(groups = {"smoke"}, description = "GET single user returns 200")
public void testGetUser() {
    Response response = client().pathParam("id", 2).get(UserEndpoints.USER_BY_ID);

    ResponseValidator.of(response)
        .assertStatusCode(200)
        .assertJsonValue("data.id", equalTo(2));

    SchemaValidator.validate(response, "single_user_schema.json");
}
```

4. Tag with TestNG `groups` (`smoke`, `regression`, `negative`) so it's picked up by the right suite.
5. Need data-driven input? Add a `@DataProvider` in `dataproviders/DataProviders.java` (or reuse an existing JSON/CSV fixture) and reference it via `dataProviderClass`.

## Extending to a real project

- Point `base.url` in the relevant `config/*.properties` at your service.
- Set `auth.type` (`NONE`, `BASIC`, `BEARER_TOKEN`, `API_KEY`, `OAUTH2`, `DIGEST`) and fill in the matching `auth.*` keys.
- Add your own request/response POJOs and JSON Schemas.
- CI: run `mvn clean test -Penv=qa` then archive `target/allure-results` and `target/extent-reports` as build artifacts; feed `allure-results` into an Allure Jenkins/GitHub Action plugin for trend history.

## Key design decisions worth knowing

- **Retry only on 5xx**, never on 4xx — a 404/400 is the system under test telling you something real; masking it with a retry would hide genuine bugs.
- **RetryAnalyzer is auto-attached** to every `@Test` via `IAnnotationTransformer`, so test authors don't repeat `retryAnalyzer = RetryAnalyzer.class` everywhere.
- **ExtentTest is ThreadLocal**, `ExtentReports` itself is not — because parallel TestNG threads must not cross-log into each other's report nodes, but the underlying report writer is thread-safe and shouldn't be duplicated per thread.
- **`RestClient` itself is a plain field, not ThreadLocal** — safe *because* `BaseTest.client()` is ThreadLocal-backed, guaranteeing each thread already owns an independent `RestClient` instance. If you ever refactor test setup so a `RestClient` could be shared across threads again, it needs the ThreadLocal treatment back.
- **Sensitive headers are masked in every log/report** — `Authorization`, API keys, tokens, and cookies show as `abcd****(masked)` in both the SLF4J logs and the Extent report, so neither becomes a place secrets leak from.
- **Config resolution order**: `-D` system property → env-specific `.properties` → `default.properties`, so CI can override one value without touching files.
