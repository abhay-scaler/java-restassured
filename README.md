# API Testing Framework

A modular, industry-pattern Java + RestAssured + TestNG framework for REST API test automation.
Demo tests run against the public [reqres.in](https://reqres.in) API so the suite works out of the box.

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
  filters/       Custom SLF4J request/response logging filter
  listeners/     TestListener — bridges TestNG lifecycle to Extent + Allure
  models/        Request/response POJOs (Lombok + Jackson)
  reporting/     ExtentManager (suite-level) + ExtentTestManager (thread-local)
  retry/         RetryAnalyzer + RetryListener (auto-applied to all tests)
  utils/         JsonUtils, RandomDataGenerator, FileReaderUtils, CsvUtils, ExcelUtils
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

- Java 17+
- Maven 3.8+
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
    Response response = client.pathParam("id", 2).get(UserEndpoints.USER_BY_ID);

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
- **Config resolution order**: `-D` system property → env-specific `.properties` → `default.properties`, so CI can override one value without touching files.
