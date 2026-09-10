# Adding a New API — Start Here

"Add support for API X" is ambiguous in this framework — it can mean three different things, each
with a different checklist and a different blast radius. Answer the question below before writing
any code.

## Which of these are you actually doing?

```
Is the new API a completely independent target you'll test on its own
(its own base URL, its own auth, its own test suite), the way App B
(Restful Booker) sits alongside App A (reqres.in)?
│
├── YES → You're adding a NEW APPLICATION.
│         Go to ADD_NEW_APPLICATION.md.
│
└── NO → Is it a second backend that an EXISTING application (appA or appB)
          needs to call — same test suite, same app identity, just another
          set of endpoints/base URL under the services.<name>.* convention?
          │
          ├── YES → You're adding a NEW SERVICE within an existing application.
          │         Go to ADD_NEW_SERVICE.md.
          │
          └── NO → You're adding a NEW ENDPOINT (or a new resource) to an
                    application that already exists and already has the base
                    URL/auth it needs. Keep reading below.
```

Don't default to "new application" just because it's the most-documented path — most real
day-to-day work is the third case (a new endpoint on an app that already exists).

## Case 3: Adding a new endpoint to an existing application

Example: App A's Users API gains a new `/users/{id}/avatar` endpoint, or App B's Restful Booker
gains a resource you haven't touched yet. No new config, no new base URL, no new suite file.

1. **Endpoint path** — add the constant to that app's existing `endpoints/*Endpoints.java`
   (e.g. `apps/appA/endpoints/UserEndpoints.java`), or create a new `*Endpoints.java` in that same
   package if it's a genuinely separate resource (Restful Booker split `AuthEndpoints` from
   `BookingEndpoints` this way).
2. **Request/response POJOs** — add to `apps/<app>/models/request/` and
   `apps/<app>/models/response/`, following the existing Lombok + Jackson conventions already used
   by that app's other models.
3. **Decide: direct `client()` calls, or an API wrapper?** App A calls `client()` directly against
   its endpoints (no `api/` layer) because its endpoints are simple, independent CRUD calls. App B
   has `AuthApi`/`BookingApi` because its booking lifecycle is a real multi-step workflow (auth →
   use the token across several calls) that's reused across several test classes. Only add an
   `apps/<app>/api/*Api.java` wrapper if your new endpoint has that same kind of repeated,
   multi-step, or reused-sequence shape — otherwise call `client()` directly from the test. See
   `README.md`'s **App A vs. App B: when to add an API layer**.
4. **JSON Schema** (if you want contract validation) — add a file under `schemas/<app>/` and call
   `SchemaValidator.validate(response, "<app>/your_schema.json")`.
5. **Test data** (if data-driven) — add a fixture under `testdata/<app>/` and a `@DataProvider`
   method in that app's `apps/<app>/dataproviders/App<X>DataProviders.java`.
6. **Write the test** — extend `BaseTest`, use `client()` (or the app's API wrapper), assert with
   `ResponseValidator`. Tag with the right TestNG `groups` (`smoke`, `regression`, `negative`).
7. **Register the test class** in the relevant `<classes>` block(s) of
   `suites/<app>/{testng,smoke,regression}.xml` — a test not listed in a suite file never runs, no
   matter how it's tagged.
8. **Run it**: `mvn test -Dapp=<app> -Denv=qa` (add `-Psmoke`/`-Pregression` to scope to one
   profile while iterating).

**Nothing under a shared package changes for this case.** If you find yourself editing anything in
`auth/`, `builders/`, `clients/`, `config/`, `filters/`, `listeners/`, `reporting/`, `retry/`,
`utils/`, or `validators/` to add one endpoint, stop — that's very likely solvable through existing
mechanisms (a config key, a `ServiceConfig` override, an existing validator method) instead. See
[`ADD_NEW_API.md`](#which-of-these-are-you-actually-doing) reasoning above, and
[`../../AGENTS.md`](../../AGENTS.md) rule 1.

## Before you start, in every case

- Read `README.md`'s **Project layout** and **Writing a new test** sections — this guide assumes
  that structure and doesn't repeat it.
- Check [`REVIEW_CHECKLIST.md`](REVIEW_CHECKLIST.md) so your PR matches what a reviewer expects.
- If you're using an AI agent to draft the endpoint/POJO/test code, follow
  [`API_TEST_GENERATION.md`](API_TEST_GENERATION.md)'s human-review workflow rather than merging
  generated code directly.
