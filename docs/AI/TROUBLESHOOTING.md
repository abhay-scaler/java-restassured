# Troubleshooting

Known setup/build/execution error messages and their fixes — this is about the framework refusing
to *run* correctly, not about a test assertion failing. For triaging an actual test failure once
the suite is running, see [`DEBUG_TEST_FAILURE.md`](DEBUG_TEST_FAILURE.md) instead.

## `IllegalStateException: Unknown application '<app>'.`

**Cause:** `-Dapp=<app>` doesn't match any directory under `src/test/resources/config/`.
`ConfigManager.getApplication()` validates the application name against the filesystem, not
against an enum — there is no "closest match" or silent fallback.

**Fix:** Check the spelling of `-Dapp=`, and confirm
`src/test/resources/config/<app>/` actually exists. Valid values today: `appA`, `appB`.

## `IllegalStateException: Configuration file not found for application '<app>' and environment '<env>'`

**Cause:** The application directory exists, but `<env>.properties` doesn't exist inside it — e.g.
you passed `-Denv=staging` when the file is named `stage.properties`.

**Fix:** Check `src/test/resources/config/<app>/` for the exact filename. Valid environments:
`dev`, `qa`, `stage`, `prod` (from the `Environment` enum).

## `IllegalArgumentException: Unknown environment: <value>. Supported: dev, qa, stage, prod`

**Cause:** `-Denv=` doesn't match one of the four values in `Environment.java`, thrown from
`Environment.fromString()` — distinct from the file-not-found case above, which is a filesystem
check; this one is an enum check used elsewhere (e.g. `ConfigManager.getEnvironment()`).

**Fix:** Use one of `dev`/`qa`/`stage`/`prod`, or add the new environment properly — see
`DESIGN.md`'s **How to add a new environment** (add the properties file under every application
*and* the enum value).

## Suite fails to resolve / "suite file is not a valid file"

**Cause:** `-Dapp=` doesn't correspond to a real
`src/test/resources/suites/<app>/{testng,smoke,regression}.xml` file — most often a typo in
`-Dapp=`, or a new application (see [`ADD_NEW_APPLICATION.md`](ADD_NEW_APPLICATION.md)) that's
missing its suite files.

**Fix:** Confirm the suite file exists at the path Maven's `suiteXmlFile` property
(`src/test/resources/suites/${app}/testng.xml`, or `smoke.xml`/`regression.xml` under the matching
profile) resolves to.

## `IllegalStateException: Unknown auth.type '<value>'. Supported values: [NONE, BASIC, BEARER_TOKEN, API_KEY, OAUTH2, DIGEST]`

**Cause:** The resolved `auth.type` — from the active `config/<app>/<env>.properties`, a
`services.<name>.auth.type` override, or a `-Dauth.type=` flag — isn't one of the six `AuthType`
values. `AuthProvider` throws while the request spec is being built, before any HTTP call is made;
it deliberately does **not** fall back to `NONE`, because a silent downgrade to no auth would only
surface later as a confusing remote 401/403.

**Fix:** Set `auth.type` to one of `NONE`, `BASIC`, `BEARER_TOKEN`, `API_KEY`, `OAUTH2`, `DIGEST`.
Matching is case-insensitive (`api_key` resolves fine), but surrounding whitespace is not trimmed —
a trailing space makes the value unrecognized, which is why the message quotes it. An empty or
whitespace-only value is rejected the same way and does not mean `NONE`; write `NONE` explicitly if
that's what you intend.

## `IllegalStateException: Configuration key '<key>' is required for the configured auth.type but is missing or blank.`

**Cause:** The resolved `auth.type` needs a credential whose value is null, empty, or
whitespace-only. `AuthProvider` validates this locally rather than sending a blank credential and
letting the target API reject it as a confusing remote 401/403. Which key is required depends on
the type:

| `auth.type` | Required keys |
|---|---|
| `API_KEY` | `auth.api.key.value` |
| `BEARER_TOKEN`, `OAUTH2` | `auth.token` |
| `BASIC`, `DIGEST` | `auth.username`, `auth.password` |
| `NONE` | none |

**Fix:** Supply the named key via the active `config/<app>/<env>.properties`, a
`services.<name>.<key>` override, or `-D<key>=value` — e.g.
`-Dauth.api.key.value=YOUR_KEY` for App A. The message names the canonical configuration key only,
never the value. Note that a service-level override which is explicitly blank does **not** fall
back to the application-level value — remove the override rather than blanking it.

## `403 invalid_api_key` / `429 rate_limit_exceeded` against App A (reqres.in)

**Not a framework bug.** This is App A's documented external limitation — see
[`DEBUG_TEST_FAILURE.md`](DEBUG_TEST_FAILURE.md). Get a fresh key from https://reqres.in/signup and
pass it via `-Dauth.api.key.value=YOUR_KEY`, or update
`src/test/resources/config/appA/*.properties` (never commit the real value).

## `418` / `text/plain "I'm a Teapot"` against App B (Restful Booker), or an `IllegalStateException` from `createBooking()`

**This was a real framework bug, now fixed** — it was previously (mis)documented here as an
unavoidable external quirk. `RequestSpecFactory` was sending RestAssured's `ContentType.JSON` as
the `Accept` header, which expands to `application/json, application/javascript, text/javascript,
text/json`; Restful Booker's demo API returns 418 for that broader value specifically, and a `200`
for a plain `Accept: application/json`. The fix sends the literal `application/json` value instead
— see [`DEBUG_TEST_FAILURE.md`](DEBUG_TEST_FAILURE.md) for the full evidence. If you still see a
418 after this fix, it means some request path isn't going through `RequestSpecFactory` (e.g. a
hand-built `RequestSpecification`) — that's worth investigating as a real, new issue, not writing
off as "the known Restful Booker quirk."

## `NoClassDefFoundError` / AspectJ weaver errors during `mvn test`

**Cause:** The Surefire `argLine` requires the AspectJ weaver jar to exist at
`${settings.localRepository}/org/aspectj/aspectjweaver/<version>/aspectjweaver-<version>.jar`
(needed for Allure's `@Step` annotations). This is normally resolved automatically by Maven.

**Fix:** Run `mvn dependency:resolve` or a plain `mvn clean test` first to ensure the local repo
has the artifact before assuming a real classpath problem.

## A property change in `-D` doesn't seem to take effect

**Cause / thing to check:** System properties override file properties, which override
`default.properties` — but only for the *current* build invocation. If you're running through an
IDE run configuration or a wrapper script, confirm the `-D` flag is actually being passed to the
Surefire-forked JVM, not just to the outer Maven process. `ConfigManager` prints its fully resolved
configuration (including the winning value for every key) to stdout on load — check that block
first before assuming a bug.

## Compiling fails after pulling changes, with Lombok-annotation-related errors

**Cause:** Lombok's annotation processor needs to run during compilation (it's registered as an
`annotationProcessorPath` on `maven-compiler-plugin` in `pom.xml`). An IDE that hasn't picked up
annotation processing (or has a stale Lombok plugin version) will show unresolved builder/getter
methods that compile fine from the command line.

**Fix:** Run `mvn clean compile` from the command line to confirm it's an IDE-only issue, then
re-enable annotation processing / update the Lombok plugin in your IDE.

## Still stuck

If the error doesn't match anything above, treat it as new: capture the exact message and stack
trace, check whether `docs/INTERVIEW_GUIDE.md`'s **Important Debugging Stories** section describes
the same class of issue, and if it's genuinely new, follow
[`DEBUG_TEST_FAILURE.md`](DEBUG_TEST_FAILURE.md)'s Step 5 before proposing a fix to shared code.
