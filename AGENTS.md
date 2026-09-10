# AGENTS.md

Instructions for any AI coding agent (or new engineer) working in this repository. Read this file
first, before touching any code. It is deliberately short — it states the hard rules and then
points at `docs/AI/` for everything else.

## What this repository is

A Java 21 + REST Assured + TestNG API automation framework, **multi-application by design**. The
shared/core framework (config resolution, request building, auth, HTTP execution, retry, schema
validation, reporting) is application-agnostic. Two independent applications currently prove that
boundary holds:

| App | Target API | Domain |
|---|---|---|
| `appA` (default) | reqres.in | Users CRUD |
| `appB` | Restful Booker | Hotel booking lifecycle |

Full architecture: [`DESIGN.md`](DESIGN.md). User-facing setup/usage: [`README.md`](README.md).
Interview-style deep dive with debugging stories: [`docs/INTERVIEW_GUIDE.md`](docs/INTERVIEW_GUIDE.md).

## Start here

[`docs/AI/SKILLS.md`](docs/AI/SKILLS.md) is the index of every task-specific guide in this
repository (onboarding, adding an API/service/application, debugging a failure, CI, reporting,
code review, AI-assisted test generation). Find the guide that matches your task before writing
any code.

## Hard rules — non-negotiable

These are enforced by convention, by code review, and in places by the architecture itself. Do not
violate them even if a task seems to require it — stop and ask instead.

1. **No application-specific branching in shared/core code.** Never write
   `if (app.equals("appA"))`, `if (app.equals("appB"))`, or any equivalent switch/lookup keyed on
   an application name inside a shared package (`auth/`, `builders/`, `clients/`, `config/`,
   `constants/`, `dataproviders/` (the generic reader), `exceptions/`, `filters/`, `listeners/`,
   `reporting/`, `retry/`, `utils/`, `validators/`, or `BaseTest`). The **only** accepted exception
   in the entire codebase is `ConfigManager.DEFAULT_APP = "appA"` — a single default value, not
   branching logic. If a change to shared code seems to require knowing which application is
   active, the application should read its own config (`ConfigManager`/`ServiceConfig`) instead —
   see [`docs/AI/ADD_NEW_APPLICATION.md`](docs/AI/ADD_NEW_APPLICATION.md) and
   [`docs/AI/ADD_NEW_SERVICE.md`](docs/AI/ADD_NEW_SERVICE.md).
2. **Protected core files.** This project maintains a "must remain unchanged without explicit
   approval" convention over its shared framework layer (roughly 27 files spanning
   `src/main/java/com/framework/{auth,builders,clients,config,constants,dataproviders,exceptions,
   filters,listeners,reporting,retry,utils,validators}`, `src/test/java/com/framework/base/BaseTest.java`,
   `pom.xml`, and `.github/workflows/tests.yml`). Do not modify any of these without the user's
   explicit, informed approval for that specific change. Anything under `apps/<app>/`,
   `config/<app>/`, `schemas/<app>/`, `testdata/<app>/`, or `suites/<app>/` is not protected in
   this sense — that's exactly where new application/service work belongs.
3. **`.gitignore` stays untouched** unless there is a compelling, explicitly approved reason.
4. **Never commit real secrets.** Properties files only ever hold a placeholder
   (`REPLACE_WITH_YOUR_REQRES_API_KEY`) or a documented public demo credential (App B's Restful
   Booker `admin`/`password123`). Real values are supplied via `-D` or a CI secret.
5. **Don't misclassify external API failures as framework regressions.** App A (reqres.in) is
   known to return HTTP 429/403 depending on key/quota state; App B (Restful Booker) is known to
   return HTTP 418 (which cascades into a `createBooking()` parsing exception downstream). Neither
   is a framework defect by itself. See
   [`docs/AI/DEBUG_TEST_FAILURE.md`](docs/AI/DEBUG_TEST_FAILURE.md) before concluding a test
   failure is a real regression.
6. **Don't conflate the two parallelism layers.** GitHub Actions matrix (`strategy.matrix.app`,
   process-level, CI-only) and TestNG `parallel="methods"` (thread-level, in-process) are
   independent mechanisms. See [`docs/AI/CI.md`](docs/AI/CI.md).
7. **Keep documentation in sync with code.** If a change alters project structure, CI behavior, or
   a documented convention, update the relevant file(s) in the same change — `README.md`,
   `DESIGN.md`, and/or the matching `docs/AI/*.md` guide. Stale docs are treated as a defect.
8. **This is documentation-first, human-reviewed automation.** There is no runtime AI in this
   framework. Any AI-assisted output (generated tests, generated code) is a draft for human review,
   never auto-merged — see
   [`docs/AI/API_TEST_GENERATION.md`](docs/AI/API_TEST_GENERATION.md).

## Before you open a PR

Run [`docs/AI/REVIEW_CHECKLIST.md`](docs/AI/REVIEW_CHECKLIST.md) against your own diff. It is the
same checklist a reviewer will use.

## When something fails

Don't guess. Follow [`docs/AI/DEBUG_TEST_FAILURE.md`](docs/AI/DEBUG_TEST_FAILURE.md) to triage,
and [`docs/AI/TROUBLESHOOTING.md`](docs/AI/TROUBLESHOOTING.md) for known setup/environment error
messages and their fixes.
