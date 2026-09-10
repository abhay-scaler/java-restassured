# Debugging a Test Failure

A systematic triage flow. Follow it in order — don't jump to "the framework is broken" before
ruling out the cheaper explanations. Per [`../../AGENTS.md`](../../AGENTS.md) rule 5, known
external API quirks must not be misclassified as framework regressions without evidence, but the
reverse mistake (waving away a real regression as "probably the flaky API") is just as bad — the
discipline in both directions is the same: **check the actual HTTP status code and exception type
before concluding anything.**

## Step 1 — Read the actual failure, not just "FAILED"

Open the Surefire report, the console output, or the Extent report node for the failing test and
find:

- The **HTTP status code** actually returned (if any).
- The **exception type** (`ValidationException`? `ApiException`? `IllegalStateException`?
  `NullPointerException`? a plain assertion failure?).
- Whether the failure happened **before or after** `RestClient`'s retry loop exhausted (look for
  `"Attempt N/M received HTTP ..."` log lines — `RestClient` retries 5xx up to `http.retry.count`
  times before giving up).
- Whether `RetryAnalyzer` already re-ran the whole test (`test.retry.count`) before the final
  failure was reported.

## Step 2 — Check against the two known external limitations first

| App | Symptom | Status | Meaning |
|---|---|---|---|
| appA (reqres.in) | `403` with body mentioning `invalid_api_key`, or `429` mentioning `rate_limit_exceeded` | 403 / 429 | The configured API key is invalid/revoked, or the free-tier daily quota is exhausted. **Get a fresh key** (`README.md` → Setup) rather than debugging the framework. |
| appB (Restful Booker) | `text/plain` body `"I'm a Teapot"`, `Content-Type: text/plain` instead of `application/json` | 418 | The public Heroku demo instance returning 418 to Java/RestAssured clients specifically (plain curl to the same endpoint does not reproduce it — this has been isolated outside the framework). This is why `suites/appB/*.xml` run at `thread-count="1"` — reducing concurrency does not eliminate it. |
| appB | `IllegalStateException` from inside `createBooking()` / test setup that calls `.as(...)` immediately | (downstream of 418) | This is the **same** 418 issue, not a fourth independent defect — a test that deserializes the response before checking its status code turns the 418's `text/plain` body into a parsing exception instead of a clean status-code assertion failure. Trace it back to the 418, don't treat it as new. |

If your failure matches one of these rows exactly (same status code, same body shape), it is an
external issue. Note it and move on — do not "fix" it by retrying more aggressively, changing
`http.retry.count`, or adding app-specific handling to shared code (that would itself violate
[`../../AGENTS.md`](../../AGENTS.md) rule 1: 4xx/418 client-error-shaped responses are never
retried, and adding an `if (app.equals("appB"))` branch to swallow 418 in `RestClient` would be
exactly the kind of core-file branching that's prohibited).

If your failure does **not** match one of these rows — different status code, different exception
type, different body shape — treat it as a real problem and keep going.

## Step 3 — Rule out configuration/environment issues

- Is `-Dapp=`/`-Denv=` what you expect? `BaseTest.beforeSuite()` logs the resolved
  Application/Environment/Base URL/Auth Type banner at the start of every suite — check it.
- Does `src/test/resources/config/<app>/<env>.properties` actually have the key you expect, or is
  it silently falling back to `config/default.properties`? `ConfigManager` prints the full
  resolved configuration to stdout on load — check that block.
- If using a named service, is `services.<name>.<key>` spelled with dots (not `-`/`_`)?
  `ServiceConfig` only recognizes the dotted convention.
- Did a `-D` override you passed actually take effect? System properties override file values —
  confirm there isn't a stray `-D` from a shared Maven settings file or CI env shadowing what you
  intended.

See [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md) for specific error messages
(`IllegalStateException: Unknown application ...`, etc.) and their fixes.

## Step 4 — Rule out test-data / shared-state issues

- For appA: tests reuse a fixed fixture id (`2`) for read/update/delete — safe because reqres.in
  doesn't persist writes. If you added a test that *does* mutate shared state, it can collide with
  a parallel test using the same id.
- For appB: bookings are real and persisted. `AppBBookingLifecycleTests` creates its own booking
  id per test rather than assuming one exists — if you wrote a test that assumes a fixed booking
  id, that's a likely cause of flakiness, not a framework bug.
- Check `thread-count` in the suite file that ran. A test that isn't actually thread-safe (a
  static/shared mutable field you added) will show up as intermittent cross-test contamination
  under `parallel="methods"`.

## Step 5 — Only now consider it a framework regression

If you've ruled out Steps 2–4, the candidates worth investigating are:

- A genuine bug in shared code (`auth/`, `builders/`, `clients/`, `config/`, `filters/`,
  `listeners/`, `reporting/`, `retry/`, `utils/`, `validators/`).
- A genuine bug in the specific application's endpoint/model/test code.
- A JSON Schema out of sync with the actual API response shape.

Reproduce with the smallest possible repro (a single `@Test`, `mvn test -Dapp=<app> -Dtest=<Class>#<method>`
if useful) before proposing a fix, and check whether the same class of failure is already described
in `docs/INTERVIEW_GUIDE.md`'s **Important Debugging Stories** section — several past
investigations (a `base-url` vs `base.url` config typo, a CI matrix gap, a reporting metadata gap)
are documented there in detail and may be the same root cause recurring.

## Reporting a finding

When you conclude a failure is external (Step 2) rather than fixing it, say so explicitly and cite
the evidence (status code + body shape) rather than silently skipping or ignoring the test —
per [`../../AGENTS.md`](../../AGENTS.md) rule 5.
