# Onboarding

For a new engineer, or a fresh AI agent with no conversation history. Read
[`../../AGENTS.md`](../../AGENTS.md) first for the hard rules; this guide is the tour.

## 1. What you're looking at

A layered Java 21 + REST Assured 5 + TestNG framework for REST API test automation, built
multi-application from the ground up. Two independent applications ship today:

| App | Target API | Domain | Auth |
|---|---|---|---|
| `appA` (default) | https://reqres.in | Users CRUD | API key |
| `appB` | https://restful-booker.herokuapp.com | Hotel booking lifecycle | Basic (for `/auth`), cookie token thereafter |

The governing architectural rule: **only configuration and suite selection know an application's
name.** Every shared/core class operates on whatever config is active on the current thread and
never branches on `"appA"`/`"appB"`. See `DESIGN.md`'s Architecture overview for the full
statement of this rule.

## 2. Setup

1. Java 21, Maven 3.8+ (or just use the bundled `./mvnw`).
2. App A needs a free API key from https://reqres.in/signup. Either pass it ad-hoc
   (`-Dauth.api.key.value=YOUR_KEY`) or replace the placeholder in
   `src/test/resources/config/appA/*.properties`. **Never commit a real key.**
3. App B needs no key — its demo credentials are already in
   `src/test/resources/config/appB/*.properties`.

## 3. Run something

```bash
# Default: appA + qa
./mvnw clean test

# Explicit app + env
./mvnw test -Dapp=appA -Denv=qa
./mvnw test -Dapp=appB -Denv=qa

# Smoke / regression profiles, per app
./mvnw test -Dapp=appA -Psmoke
./mvnw test -Dapp=appB -Pregression
```

`-Dapp=` selects both the config directory (`src/test/resources/config/<app>/`) and the TestNG
suite file (`src/test/resources/suites/<app>/{testng,smoke,regression}.xml`). An unrecognized
`-Dapp=` fails suite resolution immediately — it does not silently fall back to App A.

After a run: open `target/extent-reports/ExtentReport.html`, or `./mvnw allure:serve` for Allure.

## 4. Project layout (the parts you'll touch most)

```
src/main/java/com/framework/
  auth/ builders/ clients/ config/ constants/ dataproviders/ exceptions/
  filters/ listeners/ reporting/ retry/ utils/ validators/     <- shared, app-agnostic, protected
  apps/appA/   endpoints/, models/request|response/            <- App A: no api/ layer
  apps/appB/   endpoints/, models/, api/*Api.java              <- App B: has a thin api/ layer

src/test/java/com/framework/
  base/BaseTest.java                    <- shared setup/teardown, protected
  config/ConfigManagerTests.java        <- app-agnostic config isolation coverage
  apps/appA/{dataproviders,tests}/
  apps/appB/{dataproviders,tests}/

src/test/resources/
  config/{default.properties, appA/*.properties, appB/*.properties}
  schemas/appA/, schemas/appB/          <- JSON Schema contract files
  testdata/appA/, testdata/appB/        <- JSON/CSV data-provider fixtures
  suites/appA/, suites/appB/            <- {testng,smoke,regression}.xml
```

See `README.md`'s **Project layout** section for the complete tree, and `DESIGN.md`'s
**Component responsibilities** table for what each shared class owns.

**A note on the `apps/<app>/`-style shorthand**: this guide and every other `docs/AI/*.md` guide
(plus `AGENTS.md`, `README.md`, `DESIGN.md`) routinely write `apps/<app>/`, `config/<app>/`,
`schemas/<app>/`, `testdata/<app>/`, and `suites/<app>/` as short-hand for real project-layout
locations — **not** repository-root directories. There is no top-level `apps/`, `config/`,
`schemas/`, `testdata/`, or `suites/` folder in this repository. The actual roots, per the tree
above, are:
- `apps/<app>/` → `src/main/java/com/framework/apps/<app>/` (main code: endpoints, models, optional
  `api/`) or `src/test/java/com/framework/apps/<app>/` (test code: `dataproviders/`, `tests/`) —
  which one depends on whether the guide is talking about production or test code.
- `config/<app>/`, `schemas/<app>/`, `testdata/<app>/`, `suites/<app>/` → all under
  `src/test/resources/`.

If a guide's shorthand path doesn't resolve where you expect, it's this mapping you're missing, not
a broken reference — you don't need to hunt for a top-level `apps/` directory that doesn't exist.

## 5. Core concepts you need before writing anything

- **`ConfigManager`** is the *only* class that reads `-Dapp`/`-Denv`. It resolves
  `config/<app>/<env>.properties`, layers `-D` system properties on top, and falls back to
  `config/default.properties`. See `DESIGN.md`'s **Configuration and environment strategy**.
- **`ServiceConfig`** is a *second, independent* axis: a named backend *inside* one application,
  read as `services.<name>.*` with fallback to that app's top-level keys. Don't confuse "new
  application" with "new service" — see [`ADD_NEW_API.md`](ADD_NEW_API.md) to pick the right one.
- **Two separate retry knobs**: `RestClient` retries a single 5xx response
  (`http.retry.count`); `RetryAnalyzer` (auto-attached to every `@Test` via `RetryListener`)
  reruns a whole failed test method (`test.retry.count`). Never on 4xx — that's signal, not noise.
- **Retry and reporting both depend on `<listener>` registration in the suite XML — and this is a
  silent failure mode if you get it wrong.** `RetryAnalyzer` only reruns failed tests because
  `RetryListener` is registered as `<listener class-name="com.framework.retry.RetryListener"/>` in
  the suite file; `TestListener` (which bridges TestNG's lifecycle to Allure/ExtentReports) needs
  the same kind of registration. Every suite file in this repo registers both today. If a new or
  edited suite XML omits either `<listener>` entry, **tests still run and still pass or fail
  normally** — there is no compile error and no obvious runtime error — but `test.retry.count`
  silently stops rerunning failed tests, and Allure/ExtentReports silently stop being populated for
  that suite. Always copy the `<listeners>` block from an existing suite (e.g.
  `suites/appA/testng.xml`) rather than writing one from memory.
- **Thread safety**: TestNG reuses one instance of each test class across parallel threads.
  `BaseTest` owns a `ThreadLocal<RestClient>` with a per-test lifecycle: a fresh `RestClient` is
  created in `@BeforeMethod` and removed in `@AfterMethod`, so each thread's in-flight test always
  gets its own instance regardless of class-instance sharing. `ConfigManager` holds its `AppConfig`
  in a `ThreadLocal` too, for the same reason.
  `RestClient` itself is deliberately **not** `ThreadLocal`. It doesn't need to be: once
  `BaseTest`'s per-test `ThreadLocal` lifecycle already guarantees exactly one thread owns one
  `RestClient` instance for the duration of one test method, `RestClient` can be an ordinary,
  reusable object *within* that lifecycle — making it `ThreadLocal` on top would add overhead
  without adding any safety. See `DESIGN.md`'s design principles for the fuller rationale if you're
  touching `clients/RestClient.java` or `BaseTest` itself.
  If you ever introduce shared mutable state in a class TestNG reuses across threads, it needs the
  same treatment `BaseTest` gives its client: `ThreadLocal`-backed, with a defined per-test
  create/remove lifecycle — not just a plain field.
- **Reporting is automatic**: every request through `RestClient` shows up in both the Allure and
  ExtentReports output with no test-code changes needed — see [`REPORTING.md`](REPORTING.md).
- **Two applications, two known external quirks**: App A/reqres.in can return 429/403 depending on
  key/quota state; App B/Restful Booker can return 418, which cascades into a `createBooking()`
  parsing exception. Neither is a framework bug by itself — see
  [`DEBUG_TEST_FAILURE.md`](DEBUG_TEST_FAILURE.md).

## 6. Where to go next

- Adding something new? Start at [`ADD_NEW_API.md`](ADD_NEW_API.md).
- Something failed? Go to [`DEBUG_TEST_FAILURE.md`](DEBUG_TEST_FAILURE.md).
- About to open a PR? Run [`REVIEW_CHECKLIST.md`](REVIEW_CHECKLIST.md).
- Full guide index: [`SKILLS.md`](SKILLS.md).
