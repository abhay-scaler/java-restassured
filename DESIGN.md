# Design

This document reflects the framework's actual, current implementation — not an aspirational
target. It's kept at the repo root so it stays next to the code it describes.

## Architecture overview

A layered TestNG + REST Assured framework. Test classes never call RestAssured directly — they
extend `BaseTest`, which hands them a `RestClient`: a thin fluent wrapper that builds its request
spec once (through `RequestSpecFactory`), then executes verbs against it with automatic 5xx retry.
Auth, logging, Allure, and ExtentReports are all composed once at the factory layer, so no test
class ever touches a reporting or auth API directly.

## Project structure

```
src/main/java/com/framework/
  auth/          AuthProvider (auth-type switch), AuthType
  builders/      RequestSpecFactory — createDefault() / createWithoutAuth() / forService(name)
  clients/       RestClient — fluent HTTP verbs, 5xx retry, per-client reset supplier
  config/        AppConfig (OWNER), ConfigManager, Environment, ServiceConfig
  constants/     HttpMethod, FrameworkConstants
  dataproviders/ Reusable TestNG @DataProvider sources (JSON/CSV)
  endpoints/     Endpoint path constants (one file per resource, e.g. UserEndpoints)
  exceptions/    ApiException, FrameworkException, ValidationException
  filters/       SLF4J request/response filter, Extent report filter
  listeners/     TestListener — TestNG lifecycle → Extent + Allure
  models/        Request/response POJOs (Lombok + Jackson)
  reporting/     ExtentManager (suite-level), ExtentTestManager (thread-local)
  retry/         RetryAnalyzer + RetryListener (auto-applied to every @Test)
  utils/         JsonUtils, RandomDataGenerator, FileReaderUtils, CsvUtils, HttpLogFormatter
  validators/    ResponseValidator (fluent assertions), SchemaValidator (JSON Schema)

src/test/java/com/framework/
  base/BaseTest.java   Shared setup/teardown; owns the ThreadLocal default RestClient
  tests/               GetUsersTests, CreateUserTests, UpdateUserTests, DeleteUserTests,
                        AuthTests (the Users API), ServiceClientTests (multi-service mechanism)

src/test/resources/
  config/{dev,qa,stage,prod,default}.properties   One file per environment
  schemas/            JSON Schema files for contract validation
  testdata/           JSON/CSV data-provider fixtures
  suites/{testng,smoke,regression}.xml

.github/workflows/tests.yml   CI: smoke on PR/push, regression on schedule/manual dispatch
mvnw, mvnw.cmd, .mvn/         Maven wrapper — no local Maven install required
```

A new service's endpoints and models currently live alongside the existing ones (e.g.
`endpoints/OrderEndpoints.java`, `models/request/CreateOrderRequest.java`) rather than under a
`services/<name>/` subtree — there's only one real service in this repo today, so introducing that
extra folder layer now would be structure with no second occupant. Regroup into `services/<name>/`
the day a second real service actually lands; nothing else here depends on the folder layout.

## Component responsibilities

| Component | Responsibility |
|---|---|
| `AppConfig` | Typed access to the framework-wide ("default" service) config values |
| `ConfigManager` | Loads the active environment's properties file per thread; layers `-D` overrides on top; exposes raw properties for `ServiceConfig` |
| `ServiceConfig` | A named slice of the same properties, read as `services.<name>.*` with fallback to the top-level keys |
| `RequestSpecFactory` | The one place a `RequestSpecification` gets assembled — default, no-auth, or per-service |
| `AuthProvider` | Applies the configured auth strategy to a spec builder; shared by both `AppConfig`- and `ServiceConfig`-driven callers |
| `RestClient` | Fluent HTTP verbs over a spec; retries 5xx; rebuilds its spec from whatever produced it (default / named service / custom) after every call |
| `BaseTest` | Per-thread default `RestClient` lifecycle for test classes |
| `ResponseValidator` / `SchemaValidator` | Fluent assertions and JSON Schema contract checks |
| `TestListener` / `RetryListener` | Bridge TestNG's lifecycle to reporting and auto-attach retry, so test authors never call either API directly |

## Test execution flow

1. TestNG starts a suite (`suites/*.xml`), which registers `TestListener` and `RetryListener`.
2. `@BeforeSuite` (`BaseTest.beforeSuite`) logs the resolved environment/config once per thread.
3. `@BeforeMethod` (`BaseTest.setUp`) creates a fresh `RestClient` and stores it in a `ThreadLocal`,
   so parallel threads never share one client instance even though TestNG reuses one test-class
   instance across all its `@Test` methods.
4. The test calls `client()` (or, for a named service, `new RestClient("serviceName")` directly),
   chains fluent calls, and invokes a verb.
5. `TestListener` logs pass/fail/skip to both Allure and ExtentReports; `RetryAnalyzer` reruns a
   failed method up to `test.retry.count` times before it's reported as a final failure.
6. `@AfterMethod` attaches the failure stacktrace to Allure (if any) and clears the ThreadLocal.
7. `@AfterSuite`-equivalent (`TestListener.onFinish`) flushes the Extent report to disk.

## Request/response flow

`RequestSpecFactory.createDefault()` (or `.forService(name)`) builds one `RequestSpecification`:
base URI, JSON content type, connection/socket timeouts, auth (via `AuthProvider`), and three
filters (Allure, SLF4J logging, Extent reporting). `RestClient` holds that spec, applies fluent
mutations (`.pathParam()`, `.queryParam()`, `.body()`, …), and on a verb call dispatches through
RestAssured. A response with status `< 500` returns immediately; `>= 500` retries up to
`http.retry.count` additional times, then throws `ApiException` if still failing. Either way, the
client's spec is rebuilt from scratch afterward — from the *same* source it was originally built
from (default / named service / custom spec) — so per-call state never leaks into the next call,
and a service-scoped or custom-spec client doesn't drift back to framework defaults after its first
call.

## Authentication flow

`AuthProvider.apply()` reads `auth.type` (`NONE`/`BASIC`/`BEARER_TOKEN`/`API_KEY`/`DIGEST`/`OAUTH2`)
and adds the matching header or RestAssured auth scheme to the spec builder at build time —
switching strategy is a config change, never a test-code change. The same switch logic is shared
between the `AppConfig`-driven and `ServiceConfig`-driven paths, so a second service reuses every
auth strategy the first one has.

`OAUTH2` currently behaves identically to `BEARER_TOKEN` (a pre-issued token from config) — there
is no token-fetch/cache/refresh flow implemented. That's intentional: no service in this repo
needs one yet, and building a real client-credentials flow with nothing to validate it against
would be speculative. Implement it against the first service that actually requires it.

## Configuration and environment strategy

One properties file per environment (`dev`/`qa`/`stage`/`prod`), selected with `-Denv=`. Any single
key can be overridden ad-hoc with `-D` without editing a file — `ConfigManager` layers system
properties over the environment file before handing the merged map to OWNER.

Per-service values are namespaced within that *same* file:

```properties
# Today's top-level keys — the implicit "default" service, unchanged:
base.url=https://reqres.in
base.path=/api
auth.type=API_KEY
auth.api.key.name=x-api-key
auth.api.key.value=REPLACE_WITH_YOUR_REQRES_API_KEY

# A second service adds its own prefixed block — no new file, no code change:
# services.orders.base-url=https://orders.internal.example.com
# services.orders.auth.type=BEARER_TOKEN
# services.orders.auth.token=${ORDERS_TOKEN}
```

`ServiceConfig.of("orders")` reads `services.orders.*` first, falling back to the top-level key for
anything not overridden — so registering a service with no properties at all (as `ServiceClientTests`
does for `"users"`) resolves to today's single-service defaults for free.

**Secrets:** properties files only ever hold a placeholder (`REPLACE_WITH_YOUR_REQRES_API_KEY`,
matching the README's own documented convention). Real values are supplied via `-D` locally or a CI
secret mapped to an env var — never committed.

## Test-data strategy

`RandomDataGenerator` (Datafaker-backed) generates non-deterministic field values so parallel tests
never collide on a fixed value. `DataProviders` exposes reusable JSON- and CSV-backed
`@DataProvider` sources for fixed data-driven cases. Tests against reqres.in reuse a fixed fixture
id (`2`) for read/update/delete paths — safe here because reqres.in doesn't persist writes. Against
a real, stateful service, mutating tests should create their own fixture per test and clean it up
afterward rather than sharing a hardcoded id across parallel threads.

## API/service organization

Each service gets: an `endpoints/*Endpoints` class, request/response POJOs under `models/`, its
own `services.<name>.*` config block (only for the values it needs to override), and its own test
classes. Shared framework packages (`auth`, `builders`, `clients`, `config`, `filters`, `retry`,
`reporting`, `validators`) are never service-specific and are never touched when a service is added.

## Reporting and logging

Every request logs through SLF4J (`logback.xml`: console + size/time-rolled file under
`target/logs/`) and appears as a step in both the Allure report and the ExtentReports HTML report
(`target/extent-reports/ExtentReport.html`, path driven by `report.extent.path` — previously
hardcoded and silently ignoring that key). Sensitive headers (`Authorization`, API keys, tokens,
cookies) are masked in all three via the shared `HttpLogFormatter`.

## CI/CD flow

`.github/workflows/tests.yml`:
- **PR / push to `main`:** smoke suite against QA — fast feedback, fails the check on any red test.
- **Nightly (cron) / manual dispatch:** full regression suite; `workflow_dispatch` lets a developer
  pick the environment and suite manually.
- Both jobs upload `allure-results`, `extent-reports`, and `surefire-reports` as build artifacts,
  always — even on failure — so a red build is diagnosable from the Actions UI alone.
- The API key is read from a `QA_API_KEY` repository secret and passed via `-D`; **this secret must
  be added in the repo's GitHub Actions settings before the workflow can authenticate** — nothing
  in this repo can create it automatically.
- `mvnw`/`mvnw.cmd` mean CI (and every contributor) builds with the exact Maven version this
  project expects, without relying on whatever happens to be installed globally.

## Design principles

- Retry only on 5xx (`RestClient`); a 4xx is signal, not noise, and is never retried.
- `test.retry.count` (whole-`@Test` reruns) and `http.retry.count` (single-call 5xx retries) are
  separate knobs — tuning one no longer silently tunes the other.
- Thread-safety lives at the layer that owns the lifecycle (`BaseTest`'s `ThreadLocal<RestClient>`),
  not duplicated inside every class that layer hands out.
- A new service is a config block plus new endpoint/model classes — never a change to a shared
  framework package.
- Prefer additive changes (a new constructor, a new factory method) over branching inside existing
  shared methods — `forService()` and `RestClient(String)` are both purely additive; every existing
  call path is untouched.
- Don't build a mechanism for a consumer that doesn't exist yet — real OAuth2 token lifecycle,
  Excel-driven test data, and a `services/<name>/` folder split are all deferred until a real second
  need justifies them (see the review's "Remove" / "deferred" sections for the reasoning).

## How to add a new API/service

1. Add a `services.<name>.*` block to each environment's properties file — only the keys that
   differ from the top-level defaults (base URL and auth are the usual ones).
2. Add an `endpoints/<Name>Endpoints.java` with that resource's paths.
3. Add request/response POJOs under `models/` — same Lombok + Jackson conventions as the existing
   models.
4. Write tests extending `BaseTest`, using `new RestClient("<name>")` instead of `client()`.
5. Add the new test classes to the relevant `<classes>` block(s) in `suites/*.xml`.
6. Drop any JSON Schemas the new service needs under `schemas/`.

Nothing in `builders/`, `clients/`, `config/`, `filters/`, `retry/`, or `reporting/` needs to change.

## How to add a new environment

1. Create `config/<env>.properties` with the same keys as an existing environment file.
2. Add the new value to the `Environment` enum in `config/Environment.java`.
3. Run with `-Denv=<env>`.

## Interview explanation

"It's a layered REST Assured + TestNG framework. Test classes never touch RestAssured directly —
they extend `BaseTest`, which hands them a `RestClient` built through one `RequestSpecFactory`, so
base URL, timeouts, auth, and three response filters (Allure, SLF4J, Extent) are assembled in
exactly one place.

The concurrency story: TestNG reuses one instance of each test class across parallel threads, so a
plain instance field for the client would let one thread's request state leak into another's
mid-flight. `BaseTest` holds the client in a `ThreadLocal`, created fresh per test method — each
thread gets an independent request builder regardless of class-instance sharing.

Retry is deliberately narrow and now explicitly two-layered: `RestClient` retries only a single 5xx
response (a 4xx is the system under test telling you something real), bounded by
`http.retry.count`; a separate `RetryAnalyzer`, auto-attached to every `@Test`, reruns a whole
failed test method, bounded by its own `test.retry.count` — split into two keys specifically so
tuning one doesn't silently change the other.

Multi-API support is a naming convention, not a new abstraction layer: `ServiceConfig` reads
`services.<name>.*` keys with fallback to today's top-level keys, and `RequestSpecFactory.forService(name)`
/ `RestClient(String)` are pure additions — every existing call path (`createDefault()`, the
no-arg `RestClient()`) is byte-for-byte unchanged. Adding a real second service is a config block
and a couple of new classes, never a change to shared framework code — which is the boundary I'd
draw deliberately, before it's forced by a second team wanting to onboard."
