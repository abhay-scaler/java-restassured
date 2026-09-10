# Adding a New Application

Use this when the new API is a genuinely independent target — its own base URL, its own auth, its
own suite — the way App B (Restful Booker) was added alongside App A (reqres.in). If you're not
sure this is the right case, go back to [`ADD_NEW_API.md`](ADD_NEW_API.md) and re-check the
decision tree; adding a whole application is heavier than most tasks actually need.

This is the same checklist as `DESIGN.md`'s **How to add a new application**, written as an
actionable step list. `DESIGN.md` is the source of truth if the two ever disagree.

Paths below like `apps/appC/`, `config/appC/`, `schemas/appC/`, `testdata/appC/`, and
`suites/appC/` are shorthand for their real locations under `src/main/java/com/framework/`,
`src/test/java/com/framework/`, and `src/test/resources/` — there is no top-level `apps/`
directory in this repository. See [`ONBOARDING.md`](ONBOARDING.md) section 4 for the full mapping
if you haven't read it yet.

## Checklist

Assume the new application is called `appC` below — substitute your real name.

1. **Config** — create `src/test/resources/config/appC/{dev,qa,stage,prod}.properties`. Copy an
   existing application's files as a starting point; only `base.url`, `auth.*`, and anything else
   that genuinely differs need new values. Everything else falls back to
   `config/default.properties`. Only a placeholder or documented public demo credential belongs in
   these files — never a real secret.
2. **Endpoints + models** — add `src/main/java/com/framework/apps/appC/endpoints/*Endpoints.java`
   and request/response POJOs under `apps/appC/models/request/` and `apps/appC/models/response/`,
   using the same Lombok + Jackson conventions as `appA`/`appB`.
3. **API wrapper — optional** — add `apps/appC/api/*Api.java` only if the new application has a
   real multi-step workflow or a call sequence reused across several test classes (App B's
   justification for `AuthApi`/`BookingApi`). If its endpoints are simple, independent calls (App
   A's shape), skip this — test classes call `client()` directly.
4. **Data providers** — add `src/test/java/com/framework/apps/appC/dataproviders/AppCDataProviders.java`
   for any data-driven input, backed by fixtures under `src/test/resources/testdata/appC/`.
5. **Test classes** — write them under `src/test/java/com/framework/apps/appC/tests/`, extending
   `BaseTest`, using `client()` (or the new app's API wrappers, if you added them). Tag with
   TestNG `groups` (`smoke`, `regression`, `negative`).
6. **JSON Schemas** — add `src/test/resources/schemas/appC/*.json` for any contract checks.
7. **Suite files** — add `src/test/resources/suites/appC/{testng,smoke,regression}.xml`,
   referencing only `com.framework.apps.appC.tests.*` classes. Register `TestListener` and
   `RetryListener` the same way the existing suites do — copy the `<listeners>` block from an
   existing suite file (e.g. `suites/appA/testng.xml`) rather than writing it from scratch.
   **This matters more than it looks**: omitting either `<listener>` entry does not fail the build
   or fail any test — the suite runs and reports pass/fail via bare TestNG/Surefire output exactly
   as if nothing were wrong — it just silently loses `test.retry.count` reruns and silently stops
   populating Allure/ExtentReports for that suite. See [`ONBOARDING.md`](ONBOARDING.md) section 5
   for the full explanation. Pick a `thread-count` deliberately — App A
   runs parallel (`3`/`5`); App B runs sequential (`1`) specifically because of its external 418
   quirk under concurrency. Don't copy a value blindly; justify it in a comment the way
   `suites/appB/testng.xml` does.
8. **Verify suite isolation** — run `mvn test -Dapp=appC -Denv=qa` and confirm (via the Surefire
   XML output, not just the exit code) that only `appC`'s classes plus the shared
   `ConfigManagerTests` ran. An unrecognized `-Dapp=` should fail suite resolution immediately with
   a clear error, not silently fall back to `appA`.
9. **Update docs** — add `appC` to the application table in `README.md` and `DESIGN.md`'s project
   structure tree, and to the CI matrix (see below) if it should run in CI. Per
   [`../../AGENTS.md`](../../AGENTS.md) rule 7, this is part of the same PR, not a follow-up.

## CI

`.github/workflows/tests.yml`'s `smoke` job is matrixed over `app: [appA, appB]`. Adding `appC` to
CI means adding it to that matrix list — see [`CI.md`](CI.md) before touching the workflow file,
since it's on the protected-files list.

## What must NOT change

Nothing in `auth/`, `builders/`, `clients/`, `config/` (the classes themselves — new `.properties`
files are expected and fine), `constants/`, `dataproviders/` (the generic reader), `exceptions/`,
`filters/`, `listeners/`, `reporting/`, `retry/`, `utils/`, or `validators/` should need to change
to add an application. If your plan requires editing one of those, stop and re-read
[`../../AGENTS.md`](../../AGENTS.md) rule 1 — you have likely found a case that needs a new,
additive extension point (the way `RequestSpecFactory.forService()` and `RestClient(String)` were
added as pure additions for multi-service support) rather than a branch inside existing shared
code. Raise it for discussion before implementing.

## Verify before calling it done

- [ ] `mvn test -Dapp=appC -Denv=qa` runs only `appC`'s tests (plus `ConfigManagerTests`).
- [ ] `grep -r "appC" src/main/java/com/framework/` outside `apps/appC/` returns nothing — the new
      application's name appears nowhere in shared code.
- [ ] `README.md` and `DESIGN.md` mention the new application in their layout/table sections.
- [ ] Run [`REVIEW_CHECKLIST.md`](REVIEW_CHECKLIST.md) before opening the PR.
