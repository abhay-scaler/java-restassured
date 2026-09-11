# Design

This document reflects the framework's actual, current implementation — not an aspirational
target. It's kept at the repo root so it stays next to the code it describes.

## Architecture overview

A layered, **multi-application** TestNG + REST Assured framework. Test classes never call
RestAssured directly — they extend `BaseTest`, which hands them a `RestClient`: a thin fluent
wrapper that builds its request spec once (through `RequestSpecFactory`), then executes verbs
against it with automatic 5xx retry. Auth, logging, Allure, and ExtentReports are all composed once
at the factory layer, so no test class ever touches a reporting or auth API directly.

The framework hosts more than one independent application (today: App A / reqres.in, App B /
Restful Booker) side by side in one codebase. The governing rule, enforced throughout this
document: **only configuration and suite selection know an application's name.** `ConfigManager` is
the single class that reads `-Dapp`; every other shared/core class — `RequestSpecFactory`,
`AuthProvider`, `RestClient`, `ServiceConfig`, `BaseTest`, the validators, reporting, and retry
packages — operates on whatever configuration is active for the current thread and never contains
`if (app.equals("appA"))`-style branching. A new application is added entirely through new files
under `apps/<app>/`, `config/<app>/`, `schemas/<app>/`, `testdata/<app>/`, and `suites/<app>/` —
never by editing a shared package.

## Project structure

```
src/main/java/com/framework/
  auth/          AuthProvider (auth-type switch), AuthType — shared, app-agnostic
  builders/      RequestSpecFactory — createDefault() / createWithoutAuth() / forService(name) — shared
  clients/       RestClient — fluent HTTP verbs, 5xx retry, per-client reset supplier — shared
  config/        AppConfig (OWNER), ConfigManager (app+env resolution), Environment, ServiceConfig — shared
  constants/     HttpMethod, FrameworkConstants — shared
  dataproviders/ DataProviders — generic, application-agnostic @DataProvider file reader — shared
  exceptions/    ApiException, FrameworkException, ValidationException — shared
  filters/       SLF4J request/response filter, Extent report filter — shared
  listeners/     TestListener — TestNG lifecycle → Extent + Allure — shared
  reporting/     ExtentManager (suite-level), ExtentTestManager (thread-local) — shared
  retry/         RetryAnalyzer + RetryListener (auto-applied to every @Test) — shared
  utils/         JsonUtils, RandomDataGenerator, FileReaderUtils, CsvUtils, HttpLogFormatter — shared
  validators/    ResponseValidator (fluent assertions), SchemaValidator (JSON Schema) — shared

  apps/appA/                 App A: Users API (reqres.in)
    endpoints/UserEndpoints.java
    models/request/, models/response/    Request/response POJOs (Lombok + Jackson)
    (no api/ layer — see "Optional per-app API layer" below)

  apps/appB/                 App B: Restful Booker
    endpoints/AuthEndpoints.java, BookingEndpoints.java
    models/request/, models/response/
    api/AuthApi.java, BookingApi.java    Thin RestClient wrappers — justified here, see below

src/test/java/com/framework/
  base/BaseTest.java          Shared setup/teardown; owns the ThreadLocal default RestClient — app-agnostic
  config/ConfigManagerTests.java   Deterministic app/env isolation coverage — no HTTP, no live dependency

  apps/appA/
    dataproviders/AppADataProviders.java
    tests/    GetUsersTests, CreateUserTests, UpdateUserTests, DeleteUserTests, AuthTests,
              ServiceClientTests (multi-service mechanism demo), AppAServiceConfigTests

  apps/appB/
    dataproviders/AppBDataProviders.java
    tests/    AppBAuthTests, AppBGetBookingTests, AppBCreateBookingTests, AppBUpdateBookingTests,
              AppBDeleteBookingTests, AppBBookingLifecycleTests (end-to-end), AppBServiceConfigTests

src/test/resources/
  config/
    default.properties            Framework-wide fallback — any key an app+env file doesn't set
    appA/{dev,qa,stage,prod}.properties
    appB/{dev,qa,stage,prod}.properties
  schemas/appA/, schemas/appB/     JSON Schema files for contract validation, per application
  testdata/appA/, testdata/appB/   JSON/CSV data-provider fixtures, per application
  suites/appA/{testng,smoke,regression}.xml
  suites/appB/{testng,smoke,regression}.xml

.github/workflows/tests.yml   CI: smoke on PR/push, regression on schedule/manual dispatch (see CI/CD flow below)
mvnw, mvnw.cmd, .mvn/         Maven wrapper — no local Maven install required
```

Two independent, coexisting extension axes exist in this codebase and are easy to conflate:

- **A new application** (`apps/<app>/`) — a separate target API entirely, with its own package
  tree, config directory, schemas, test data, and suites. This is what App A vs. App B demonstrates.
- **A new named service within one application** (`ServiceConfig` / `services.<name>.*` keys) — a
  second endpoint group inside the *same* application's config file, for when one application talks
  to more than one backend. `ServiceClientTests` (App A) and `AppBServiceConfigTests` (App B)
  exercise this mechanism independently in each app. See **Configuration and environment strategy**
  below for how the two compose.

## Component responsibilities

| Component | Responsibility |
|---|---|
| `AppConfig` | Typed access to the framework-wide ("default" service) config values — unchanged by, and unaware of, the multi-app work |
| `ConfigManager` | Resolves `config/<app>/<env>.properties` per thread (`-Dapp`/`-Denv`, both defaulted); layers `-D` overrides on top; exposes raw properties for `ServiceConfig`. The **only** core class that ever reads `-Dapp` — see `getApplication()`, validated against the filesystem (a `config/<app>/` directory must exist), not an enum or registry |
| `ServiceConfig` | A named slice of the same properties, read as `services.<name>.*` with fallback to the top-level keys |
| `RequestSpecFactory` | The one place a `RequestSpecification` gets assembled — default, no-auth, or per-service |
| `AuthProvider` | Applies the configured auth strategy to a spec builder; shared by both `AppConfig`- and `ServiceConfig`-driven callers |
| `RestClient` | Fluent HTTP verbs over a spec; retries 5xx; rebuilds its spec from whatever produced it (default / named service / custom) after every call |
| `BaseTest` | Per-thread default `RestClient` lifecycle for test classes |
| `ResponseValidator` / `SchemaValidator` | Fluent assertions and JSON Schema contract checks |
| `TestListener` / `RetryListener` | Bridge TestNG's lifecycle to reporting and auto-attach retry, so test authors never call either API directly |

## Test execution flow

1. TestNG starts a suite (`suites/<app>/*.xml`, selected by `-Dapp=`), which registers
   `TestListener` and `RetryListener`.
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

Configuration resolution has two independent dimensions, both resolved by the same `ConfigManager`:

1. **Which application** — `-Dapp=` (default `appA`), selecting a whole
   `config/<app>/<env>.properties` file. `ConfigManager.getApplication()` validates this against the
   filesystem: the `config/<app>/` directory must exist, or resolution fails immediately with a
   specific "unknown application" error naming the expected path. This is deliberately a plain,
   validated `String`, not an enum or registry — adding a third application never means editing
   `ConfigManager`.
2. **Which environment** — `-Denv=` (default `qa`), selecting the `<env>.properties` file *within*
   that application's directory (`dev`/`qa`/`stage`/`prod`, from the `Environment` enum — itself
   app-agnostic; the same four environment names apply to every application).

Any single resolved key can still be overridden ad-hoc with `-D` without editing a file —
`ConfigManager` layers system properties over the application+environment file before handing the
merged map to OWNER. Keys neither file nor `-D` sets fall back to `config/default.properties`
(shared across every application, untouched by the multi-app work).

```bash
# app=appA (default), env=qa (default) → loads config/appA/qa.properties
mvn test

# app=appB, env=qa → loads config/appB/qa.properties
mvn test -Dapp=appB -Denv=qa

# any single key still overridable ad-hoc, regardless of app:
mvn test -Dapp=appA -Denv=qa -Dbase.url=https://reqres.in
```

**Independently, within one application's config file**, a named-service sub-convention lets a
single application talk to more than one backend without a second config file or loading mechanism:

```properties
# config/appA/qa.properties — App A's top-level keys, the implicit "default" service:
base.url=https://reqres.in
base.path=/api
auth.type=API_KEY
auth.api.key.name=x-api-key
auth.api.key.value=REPLACE_WITH_YOUR_REQRES_API_KEY

# A second service inside the SAME application adds its own prefixed block —
# no new file, no code change. Convention is dotted, matching every other key
# in this file (base.url, auth.type, ...) — NOT base-url:
# services.orders.base.url=https://orders.internal.example.com
# services.orders.auth.type=BEARER_TOKEN
# services.orders.auth.token=${ORDERS_TOKEN}
```

`ServiceConfig.of("orders")` reads `services.orders.*` first, falling back to the top-level key for
anything not overridden — so registering a service with no properties at all (as `ServiceClientTests`
does for `"users"` in App A) resolves to that application's own top-level defaults for free.
`ServiceConfig` never learns "application" is a concept either — it only ever asks `ConfigManager`
for whatever's active on the current thread, so it is automatically scoped to whichever application's
suite is running. `AppAServiceConfigTests` and `AppBServiceConfigTests` each prove this
independently for their own application; `ConfigManagerTests` proves the app-level resolution itself
(including that App A's and App B's `services.*` values never collide) without depending on either
application's live API.

**Secrets:** properties files only ever hold a placeholder (`REPLACE_WITH_YOUR_REQRES_API_KEY` for
App A) or a documented public demo credential (App B's Restful Booker `admin`/`password123`, which
is that service's own published test login, not a real secret). Real values are supplied via `-D`
locally or a CI secret mapped to an env var — never committed.

## Test-data strategy

`RandomDataGenerator` (Datafaker-backed) generates non-deterministic field values so parallel tests
never collide on a fixed value. `DataProviders` exposes the shared, generic JSON/CSV file-reading
`@DataProvider` source; each application layers its own provider methods on top
(`apps/appA/dataproviders/AppADataProviders`, `apps/appB/dataproviders/AppBDataProviders`), reading
fixtures from its own `testdata/<app>/` directory. Tests against reqres.in reuse a fixed fixture id
(`2`) for read/update/delete paths — safe here because reqres.in doesn't persist writes. App B's
`AppBBookingLifecycleTests` instead creates its own booking id within the test rather than assuming
one exists, since Restful Booker's booking resource is real and persisted. Against any real,
stateful service, mutating tests should create their own fixture per test and clean it up afterward
rather than sharing a hardcoded id across parallel threads.

## API/service organization — two levels

**Adding a new application** (see **How to add a new application** below) gets its own
`endpoints/*Endpoints` class(es), request/response POJOs under `models/`, its own `config/<app>/`
directory, its own `schemas/<app>/` and `testdata/<app>/`, and its own test classes under
`apps/<app>/tests/`. Shared framework packages (`auth`, `builders`, `clients`, `config`, `filters`,
`retry`, `reporting`, `validators`) are never application-specific and are never touched when an
application is added.

**Adding a new named service** *within* an existing application (see **How to add a new service
within an application** below) is a lighter-weight addition: a `services.<name>.*` block in that
application's own properties files, only for the values it needs to override.

### Optional per-app API layer

An `apps/<app>/api/*Api.java` layer (thin wrappers over `RestClient`) is optional per application,
added only when justified by repeated endpoint/parameter-building logic, a real multi-step workflow,
or enough endpoint volume that direct client calls from test code become hard to audit:

- **App A** has none — its endpoints are simple, independent CRUD calls; test classes call
  `client()` directly against `UserEndpoints`.
- **App B** has `AuthApi` and `BookingApi` — Restful Booker's booking lifecycle is inherently
  multi-step (obtain an auth token, then use it across create/get/update/delete calls), and that
  exact sequence is reused across `AppBUpdateBookingTests`, `AppBDeleteBookingTests`, and
  `AppBBookingLifecycleTests`. Both wrappers are thin: they hold a `RestClient` and translate one
  method call into one HTTP call plus its endpoint/token plumbing — no business logic, no retries or
  auth logic of their own (that stays in `RestClient`/`AuthProvider`).

Whether to add this layer is a per-application judgment call, made once when that application is
introduced — it is never required, and its absence in App A is intentional, not an oversight.

## Reporting and logging

Every request logs through SLF4J (`logback.xml`: console + size/time-rolled file under
`target/logs/`) and appears as a step in both the Allure report and the ExtentReports HTML report
(`target/extent-reports/ExtentReport.html`, path driven by `report.extent.path` — previously
hardcoded and silently ignoring that key). Sensitive headers (`Authorization`, API keys, tokens,
cookies) are masked in all three via the shared `HttpLogFormatter`.

**Every generated report is self-describing**, independent of which CI job or local command
produced it: `ExtentManager.createInstance()` sets `Application`/`Environment` as report-level
system info (read from `ConfigManager.getApplication()`/`getEnvironment()`, the same accessors
`ConfigManager`'s own resolution already exposes); `TestListener` names every Extent node
`ClassName.methodName` rather than the bare method name, and tags it with the active application as
an Extent category via `assignCategory(...)`; `BaseTest.beforeSuite()` logs the same Application
value into `execution.log` alongside the pre-existing Environment/Base URL/etc. banner. None of this
required touching `ExtentTestManager`, `ExtentReportingFilter`, or Allure — verified empirically
against real generated `ExtentReport.html` output for both applications (System/Environment panel,
node naming, category tags, zero cross-app leakage), not just passing tests.

Allure's Environment widget is populated the same way: `AllureEnvironmentWriter.write()`, called
once from `TestListener.onStart()` before any `@Test` method runs, writes the same
`Application`/`Environment` pair to `<allure-results>/environment.properties`. It's wired as a
separate call from `ExtentManager`'s own system-info setup, preserving the two backends'
independence — dropping either one doesn't affect the other. Allure only reads this file at
report-generation time (`allure serve`/`mvn allure:report`, or an external Allure plugin/server
consuming a downloaded CI artifact), so write timing within a suite run doesn't matter, and any
write failure is logged as a warning rather than failing the test run, matching
`ExtentReportingFilter`'s existing "reporting must never break a test run" convention. Verified by
the network-free `AllureEnvironmentWriterTests`, registered in `testng.xml`/`regression.xml` for
both applications alongside `ConfigManagerTests`.

## CI/CD flow

`.github/workflows/tests.yml` runs three independent jobs:

- **`smoke` — PR / push to `main`:** matrixed over `app: [appA, appB]` (`strategy.matrix`,
  `fail-fast: false`), each running that application's smoke suite as its own independent check —
  `./mvnw -B clean test -Psmoke -Dapp=${{ matrix.app }} -Denv=qa`. `fail-fast: false` is deliberate:
  App A and App B are independent validation targets, so App A's known external failure
  (reqres.in 403/429) must never cancel App B's job before it even runs, and vice versa.
  Artifact names are disambiguated per app (`reports-smoke-<app>-<run>`).
- **`framework-health` — PR / push to `main`:** same trigger condition as `smoke`, but no matrix
  (runs exactly once) and no `needs:` on `smoke` — it's fully independent, running in parallel.
  Checks out the repo, installs `libxml2-utils` (for `xmllint`), then runs
  `./tools/validate-framework.sh` — the same static, non-AI, mechanical-invariant checker described
  in **Validation results and known external limitations** below and in
  [`docs/AI/CI.md`](docs/AI/CI.md). No JDK, no Maven, no `QA_API_KEY`, no call to reqres.in or
  Restful Booker — it never touches `pom.xml`, so it skips `actions/setup-java` entirely and
  finishes in seconds. It was deliberately built as its own job rather than a step inside `smoke`:
  `smoke` is matrixed over two apps, so a step there would run the validator twice per PR/push for
  a check that isn't app-scoped in that sense.
- **`regression` — nightly (cron) / manual dispatch:** matrixed over `app: [appA, appB]`
  (`strategy.matrix`, `fail-fast: false`), the same pattern `smoke` uses — each application's
  regression suite runs as its own independent check, passing `-Dapp=${{ matrix.app }}`.
  `workflow_dispatch` lets a developer pick `env`/`suite` manually, but there is no `app` input —
  a manual dispatch always runs both matrix legs. Artifact names are disambiguated per app
  (`reports-regression-<app>-<run>`).
- `smoke` and `regression` upload `allure-results`, `extent-reports`, and `surefire-reports` as
  build artifacts, always — even on failure — so a red build is diagnosable from the Actions UI
  alone. `framework-health` produces no such output (it isn't a test run) and has no artifact step.
- The API key is read from a `QA_API_KEY` repository secret and passed via `-D`; **this secret must
  be added in the repo's GitHub Actions settings before `smoke` or `regression` can authenticate** —
  nothing in this repo can create it automatically. `framework-health` needs no secret.
- `mvnw`/`mvnw.cmd` mean CI (and every contributor) builds with the exact Maven version this
  project expects, without relying on whatever happens to be installed globally.
- `framework-health`'s pass/fail is visible as a normal GitHub Actions check on every PR, but it is
  not currently a hard merge gate: this repository's branch-protection "required status checks"
  feature is unavailable on its current GitHub plan/visibility, independent of anything in this
  workflow file.

## Design principles

- Retry only on 5xx (`RestClient`); a 4xx is signal, not noise, and is never retried.
- `test.retry.count` (whole-`@Test` reruns) and `http.retry.count` (single-call 5xx retries) are
  separate knobs — tuning one no longer silently tunes the other.
- Thread-safety lives at the layer that owns the lifecycle (`BaseTest`'s `ThreadLocal<RestClient>`,
  `ConfigManager`'s `ThreadLocal<AppConfig>`), not duplicated inside every class that layer hands out.
- **Only configuration/suite selection knows an application's name.** `ConfigManager` is the one
  place `-Dapp` is read; a shared/core class taking on `if (app == "appA")`-shaped branching is
  treated as an architecture violation, not a shortcut — see the repository-wide grep in
  **Validation results** below.
- A new application is new files under `apps/<app>/`, `config/<app>/`, `schemas/<app>/`,
  `testdata/<app>/`, `suites/<app>/` — never a change to a shared framework package. A new named
  service *within* one application is a config block plus new endpoint/model classes for that
  application — also never a shared-package change.
- Prefer additive changes (a new constructor, a new factory method) over branching inside existing
  shared methods — `forService()` and `RestClient(String)` are both purely additive; every existing
  call path is untouched. `ConfigManager.loadFor(app, env)` and `resolveRawProperty(app, env, key)`
  are the same pattern applied to the multi-app work: pure, parameterized, additive entry points
  used by `ConfigManagerTests` to compare two applications' configuration in one process, alongside
  the existing ambient `-Dapp`/`-Denv`-driven `ThreadLocal` path, which is otherwise unchanged.
- An optional per-app API layer (`apps/<app>/api/`) is added per application, not by default — see
  **Optional per-app API layer** above.
- Don't build a mechanism for a consumer that doesn't exist yet — real OAuth2 token lifecycle and
  Excel-driven test data remain deferred until a real need justifies them.

## How to add a new application

1. Create `config/<app>/{dev,qa,stage,prod}.properties` (copy an existing application's files as a
   starting point — only `base.url`, `auth.*`, and anything else that differs need new values;
   everything else falls back to `config/default.properties`).
2. Add `apps/<app>/endpoints/*Endpoints.java` and request/response POJOs under
   `apps/<app>/models/` — same Lombok + Jackson conventions as the existing applications.
3. (Optional — see **Optional per-app API layer**) add `apps/<app>/api/*Api.java` wrappers only if
   the new application has a real multi-step workflow or reused call sequence worth centralizing.
4. Add `apps/<app>/dataproviders/App<X>DataProviders.java` for any data-driven test input, backed by
   fixtures under `testdata/<app>/`.
5. Write test classes under `apps/<app>/tests/`, extending `BaseTest`, using `client()` (or the new
   application's API wrappers, if added).
6. Add `schemas/<app>/*.json` for any JSON Schema contract checks.
7. Add `suites/<app>/{testng,smoke,regression}.xml`, referencing only `apps.<app>.tests.*` classes.
8. Run `mvn test -Dapp=<app> -Denv=qa` — an unrecognized `<app>` fails suite resolution immediately
   with a clear error, rather than silently falling back to another application.

Nothing in `auth/`, `builders/`, `clients/`, `config/`, `filters/`, `retry/`, `reporting/`,
`dataproviders/` (the shared generic reader), or `validators/` needs to change.

## How to add a new service within an application

For a second backend *inside* an existing application (not a new application) — see **Configuration
and environment strategy** above for the underlying mechanism:

1. Add a `services.<name>.*` block to that application's environment properties file(s) — only the
   keys that differ from its top-level defaults (base URL and auth are the usual ones), using the
   dotted convention (`services.<name>.base.url`, not `base-url`).
2. Add an `endpoints/<Name>Endpoints.java` under that application's `apps/<app>/endpoints/` with the
   new service's paths.
3. Add request/response POJOs under that application's `apps/<app>/models/`.
4. Write tests extending `BaseTest`, using `new RestClient("<name>")` instead of `client()`.
5. Add the new test classes to the relevant `<classes>` block(s) in that application's
   `suites/<app>/*.xml`.
6. Drop any JSON Schemas the new service needs under that application's `schemas/<app>/`.

Nothing in `builders/`, `clients/`, `config/`, `filters/`, `retry/`, or `reporting/` needs to change.

## How to add a new environment

1. Create `<env>.properties` under **every** application's config directory that needs it
   (`config/appA/<env>.properties`, `config/appB/<env>.properties`, …) with the same keys as an
   existing environment file for that application.
2. Add the new value to the `Environment` enum in `config/Environment.java` — this enum is shared
   and app-agnostic; the same environment names apply across every application.
3. Run with `-Denv=<env>` (combined with whichever `-Dapp=` you're targeting).

## Validation results and known external limitations

The multi-app architecture has been validated end-to-end — suite selection, configuration
isolation, and thread-safety — independently of whether either application's target API is
reachable/healthy at any given moment:

- **Suite selection:** `mvn test -Dapp=appA`, `-Dapp=appB`, and each app's `-Psmoke`/`-Pregression`
  variant were each confirmed via the actual Surefire XML class-name output (not just exit codes)
  to run only that application's classes plus the shared, app-agnostic `ConfigManagerTests` — never
  a mix of both apps' test classes. An unrecognized `-Dapp=` value fails suite resolution
  immediately with a specific "suite file is not a valid file" error rather than silently defaulting
  to App A.
- **Configuration isolation:** `ConfigManagerTests` (via the stateless `ConfigManager.loadFor(app,
  env)` path) proves App A's and App B's resolved configuration — including their `services.*`
  values — never collide, resolve independently within one process, and both still fall back to
  `config/default.properties` and honor `-D` override precedence. `AppAServiceConfigTests` /
  `AppBServiceConfigTests` prove the same for the real, unmodified `ServiceConfig` class under each
  application's actual suite execution.
- **Thread-safety:** App A's suites run `thread-count="3"`/`"5"`; App B intentionally runs
  `thread-count="1"` as a considerate default on a shared free demo instance (not because parallel
  execution is unsafe here — a temporary `thread-count="3"` experiment was run and reverted, and
  showed the same failure set as sequential, with all three worker threads genuinely exercised and
  no cross-thread or cross-app config contamination). Static analysis confirmed no mutable
  `RestAssured.*` global state and no non-final mutable static fields anywhere in `src/main`.
- **No app-specific branching in core:** a repository-wide search for `"appA"`/`"appB"` string
  literals and equality checks across every shared/core package (everything outside `apps/appA/` and
  `apps/appB/`) turns up exactly one hit — `ConfigManager.DEFAULT_APP = "appA"`, a single default
  value (the same pattern as `DEFAULT_ENV = "qa"`), not branching logic.

**Known external limitation — not a framework defect**, isolated down to a plain curl/RestAssured
script outside this codebase before being ruled external:

- **App A / reqres.in:** live test execution currently fails with HTTP 403 `invalid_api_key` (the
  configured key is invalid, revoked, or quota-exhausted — this has also been observed as a 429
  `rate_limit_exceeded` on reqres.in's free-tier daily quota in earlier sessions; both are the same
  class of external key/quota issue, not a regression). A fresh key resolves it (see README Setup).

This limitation does not affect, and must not be conflated with, the architecture/configuration/
suite-selection/thread-safety validation above, which is independent of App A's live API health.

**App B / Restful Booker's HTTP 418 was previously documented here as a second external
limitation — it was not.** It was a real, fixed framework bug: `RequestSpecFactory` sent
RestAssured's `ContentType.JSON` as the `Accept` header, which expands to
`application/json, application/javascript, text/javascript, text/json`. Restful Booker's demo API
treats that broader value as a trigger for its 418 easter egg; a curl carrying the identical
compound header reproduces the 418 on demand, while a curl with a plain `Accept: application/json`
(or no `Accept` header at all) gets a normal `200` every time. The earlier "plain curl doesn't
reproduce it" conclusion held only because that comparison curl never sent the same compound
header — it used curl's own default, which naturally succeeded, making the issue look
client-specific when it wasn't. Fixed by sending the literal `application/json` Accept value from
all three `RequestSpecFactory` builder methods; confirmed via a full App B smoke-suite run going
from 12×418/4 failures to 0×418/0 failures, with App A unaffected either way. App B's suites still
run sequentially (`thread-count="1"`) as a considerate default on a shared free instance — that was
never the fix for the 418s and remains unrelated to this one; see the inline investigation notes in
`suites/appB/testng.xml`.

## Interview explanation

"It's a layered, multi-application REST Assured + TestNG framework. Test classes never touch
RestAssured directly — they extend `BaseTest`, which hands them a `RestClient` built through one
`RequestSpecFactory`, so base URL, timeouts, auth, and three response filters (Allure, SLF4J, Extent)
are assembled in exactly one place.

The concurrency story: TestNG reuses one instance of each test class across parallel threads, so a
plain instance field for the client would let one thread's request state leak into another's
mid-flight. `BaseTest` holds the client in a `ThreadLocal`, created fresh per test method — each
thread gets an independent request builder regardless of class-instance sharing.

Retry is deliberately narrow and explicitly two-layered: `RestClient` retries only a single 5xx
response (a 4xx is the system under test telling you something real), bounded by
`http.retry.count`; a separate `RetryAnalyzer`, auto-attached to every `@Test`, reruns a whole
failed test method, bounded by its own `test.retry.count` — split into two keys specifically so
tuning one doesn't silently change the other.

Multi-*service* support (one application talking to more than one backend) is a naming convention,
not a new abstraction layer: `ServiceConfig` reads `services.<name>.*` keys with fallback to that
application's top-level keys, and `RequestSpecFactory.forService(name)` / `RestClient(String)` are
pure additions — every existing call path (`createDefault()`, the no-arg `RestClient()`) is
byte-for-byte unchanged.

Multi-*application* support (this framework hosting more than one independent target API) is the
same discipline one level up: `-Dapp=` is resolved in exactly one class, `ConfigManager`, into
`config/<app>/<env>.properties` and `suites/<app>/*.xml`. Every other shared class — auth, request
building, HTTP execution, retry, validation, reporting — reads whatever configuration is active on
the current thread and has no idea an 'application' concept even exists. Proving that boundary held
was the point of standing up a second, genuinely different application (Restful Booker, not another
reqres.in-shaped API) rather than just designing for it on paper: different auth requirement, different
response shapes, and — deliberately, unlike App A — a thin API-wrapper layer, added only because its
booking lifecycle is a real multi-step workflow, not by default. Adding a third application is new
files under `apps/appC/`, `config/appC/`, `schemas/appC/`, `testdata/appC/`, `suites/appC/` — never a
change to shared framework code, which is the boundary I drew deliberately, before it's forced by a
second team wanting to onboard."
