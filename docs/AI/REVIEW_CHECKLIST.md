# Code Review Checklist

Run this against your own diff before opening a PR, or use it as a reviewer. It encodes the rules
in [`../../AGENTS.md`](../../AGENTS.md) as concrete things to grep for and check.

## Architecture boundary

- [ ] `grep -rn "appA\|appB" src/main/java/com/framework/` outside `apps/appA/`/`apps/appB/`
      returns **only** `ConfigManager.DEFAULT_APP = "appA"`. Any other hit is a shared/core class
      branching on an application name — not acceptable, no exceptions.
- [ ] No new `if (app.equals(...))` / `switch` on an application-name string anywhere in a shared
      package (`auth/`, `builders/`, `clients/`, `config/`, `constants/`, `dataproviders/`
      (generic reader), `exceptions/`, `filters/`, `listeners/`, `reporting/`, `retry/`, `utils/`,
      `validators/`, `BaseTest`).
- [ ] A new capability added to shared code (a new auth type, a new validator method, a new
      filter) is genuinely additive — every existing call path (`createDefault()`, the no-arg
      `RestClient()`, etc.) is unaffected — not a branch inserted into existing shared logic.

## Protected files

- [ ] If any of the ~27 protected core files were touched (the shared framework packages listed
      above, `BaseTest.java`, `pom.xml`, `.github/workflows/tests.yml`), confirm this was
      explicitly approved and the reason is stated in the PR description — not merged as an
      incidental change.
- [ ] `.gitignore` is untouched, unless the PR explicitly justifies and calls out the change.

## Secrets

- [ ] No real API key, token, or credential appears in any `.properties` file, test, or commit —
      only placeholders (`REPLACE_WITH_YOUR_REQRES_API_KEY`) or documented public demo credentials
      (App B's `admin`/`password123`).
- [ ] Any new header/credential type that should be masked in logs/reports is covered by
      `utils/HttpLogFormatter`, not a one-off masking implementation in the new code.

## Config conventions

- [ ] New `services.<name>.*` keys use the dotted convention consistently with the rest of the
      file (`services.orders.base.url`, never `base-url`).
- [ ] A new application has `config/<app>/{dev,qa,stage,prod}.properties` — all four environments,
      not just the one you tested against.
- [ ] No new config-reading code bypasses `ConfigManager`/`ServiceConfig` (e.g. reading
      `System.getProperty` directly from application code, or a second ad-hoc properties-loading
      mechanism).

## Retry and thread-safety

- [ ] A 4xx (or 418) response is never retried by new code — only 5xx is transient-retry territory
      in this framework.
- [ ] Any new field added to a class TestNG instantiates once and reuses across parallel threads
      (a test class, `BaseTest`, a listener) is either immutable/stateless or explicitly
      `ThreadLocal`-backed — a plain mutable instance field is the exact bug class documented in
      `README.md`'s changelog (`BaseTest.client` used to be a plain field and leaked path/query
      params across parallel tests).
- [ ] A new/changed suite's `thread-count` is a deliberate choice, not a copy-paste — if it's `1`,
      there's a comment explaining why (the way `suites/appB/testng.xml` documents its 418
      investigation).

## Tests and suites

- [ ] New test classes are actually registered in the relevant `suites/<app>/*.xml` `<classes>`
      block(s) — an untagged/unregistered test silently never runs.
- [ ] Tests are tagged with the right TestNG `groups` (`smoke`/`regression`/`negative`) for the
      suites that should pick them up.
- [ ] A schema-validated response uses `SchemaValidator.validate`/`validateStrict` against a real
      schema file under `schemas/<app>/`, not an inline ad-hoc shape check duplicating that.

## CI

- [ ] A change to `.github/workflows/tests.yml` preserves `fail-fast: false` on any per-app matrix
      job and the `if: always()` artifact upload step. See [`CI.md`](CI.md) before approving.
- [ ] The PR doesn't claim to have added Maven dependency caching — it already exists
      (`cache: maven` on `actions/setup-java@v4`, present since before PR #5) and re-adding it is a
      no-op, not a fix.

## Failure classification

- [ ] A test failure attributed to "the external API" in the PR description actually matches the
      documented symptom (403/429 for App A, 418/text-plain for App B — see
      [`DEBUG_TEST_FAILURE.md`](DEBUG_TEST_FAILURE.md)), not just asserted without evidence.
- [ ] Conversely, a fix described as "framework bug" isn't actually one of the two known external
      quirks being misdiagnosed.

## Documentation sync

- [ ] If this PR changes project structure, a CI job, a config convention, or anything else a
      `docs/AI/*.md` guide, `README.md`, or `DESIGN.md` describes, those docs are updated in the
      same PR — not left stale for a follow-up.
- [ ] A new, genuinely new-category recurring task got a new `docs/AI/*.md` guide (and an entry in
      [`SKILLS.md`](SKILLS.md)) rather than being wedged into an unrelated existing guide.

## AI-assisted contributions specifically

- [ ] If any code/tests in this PR were AI-generated, they were reviewed by a human against
      [`API_TEST_GENERATION.md`](API_TEST_GENERATION.md)'s checklist before merging — not merged
      on the assumption that "it compiled and passed."
