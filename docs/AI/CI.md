# CI

What `.github/workflows/tests.yml` actually does today, what it doesn't cover, and how to extend
it safely. This file is a protected file per [`../../AGENTS.md`](../../AGENTS.md) — read this
guide fully before proposing a change to it.

## The three jobs

### `smoke` — runs on `pull_request` and `push` to `main`

```yaml
strategy:
  fail-fast: false
  matrix:
    app: [appA, appB]
steps:
  - uses: actions/setup-java@v4
    with: { distribution: temurin, java-version: "21", cache: maven }
  - run: ./mvnw -B clean test -Psmoke -Dapp=${{ matrix.app }} -Denv=qa -Dauth.api.key.value=${{ secrets.QA_API_KEY }}
```

- **Matrixed over `app: [appA, appB]`** — each application's smoke suite runs as its own
  independent GitHub Actions job (real process isolation, not TestNG parallelism — see
  **Two different kinds of parallelism** below).
- **`fail-fast: false` is deliberate**, not an oversight: App A and App B are independent
  validation targets. Without it, one app's known external failure (reqres.in 403/429) could
  cancel App B's job before it even starts running, and vice versa (Restful Booker's 418).
- Maven dependency caching is already enabled via `cache: maven` on `actions/setup-java@v4` — this
  has been present since before the multi-app work (PR #5) and is not something to "add" again.
- Uploads `target/allure-results`, `target/extent-reports`, `target/surefire-reports` as build
  artifacts named `reports-smoke-<app>-<run_number>`, always (`if: always()`), so a red build is
  diagnosable from the Actions UI without re-running anything locally.

### `framework-health` — runs on `pull_request` and `push` to `main`

```yaml
steps:
  - uses: actions/checkout@v4
  - run: sudo apt-get update && sudo apt-get install -y libxml2-utils
  - run: ./tools/validate-framework.selftest.sh
  - run: ./tools/validate-framework.sh
```

- **Two stages, in this order:** first `tools/validate-framework.selftest.sh`, then
  `tools/validate-framework.sh` itself. The self-test proves the validator's Checks A–E still
  correctly detect their intended violations (and correctly stay quiet on valid input) against a
  set of isolated, synthetic fixtures — it says nothing about this repository. The second step is
  the validator proper, run against the real repository. Running the self-test first means a CI
  failure there is diagnosed as "the validator itself is broken," not misread as "the repository
  has a violation" — the two failure modes are kept clearly separate. Neither step has
  `continue-on-error`; either one failing fails the job.
- **Same trigger condition as `smoke`** (`github.event_name == 'pull_request' || github.event_name
  == 'push'`), but **no matrix** — it runs exactly once per workflow invocation, not once per app.
  It deliberately does **not** live as a step inside the `smoke` job: `smoke` is matrixed over
  `app: [appA, appB]`, so a step added there would run the validator twice per PR/push (once per
  matrix leg) for a check that isn't app-scoped in that sense — a dedicated job avoids that
  duplication entirely.
- **No `needs:`** — it runs independently of, and in parallel with, `smoke`. Nothing about its
  outcome affects whether `smoke` runs, and vice versa.
- **No JDK, no Maven, no `QA_API_KEY`, no secrets, no network call to reqres.in or Restful
  Booker.** Both `tools/validate-framework.sh` and `tools/validate-framework.selftest.sh` are pure
  bash + `xmllint`/`grep`/`sed`/`awk`/`find` — neither touches `pom.xml` nor runs `mvn`, so this job
  skips `actions/setup-java` entirely and finishes in seconds, unlike `smoke`/`regression`. The
  self-test's own fixtures are built under a throwaway `mktemp` directory it creates and removes
  itself — nothing it does touches this checkout.
- **`libxml2-utils` (which provides `xmllint`, used by the validator's suite-XML checks) is
  installed explicitly**, rather than assumed present on the `ubuntu-latest` image — it does not
  appear in GitHub's own documented preinstalled-package manifest for that runner, so relying on it
  being there already would be an unverified assumption, not a confirmed fact.
- **No artifact-upload step** — unlike `smoke`/`regression`, this job produces no
  `allure-results`/`extent-reports`/`surefire-reports` (it isn't a test run), so there is nothing to
  upload; this is an intentional difference from the other two jobs, not an oversight of the
  "preserve the artifact upload step" guidance below.
- **Not currently a hard merge gate.** This repository's branch-protection "required status checks"
  feature is unavailable on its current GitHub plan/visibility (confirmed via `gh api
  repos/<owner>/<repo>/branches/main/protection` → 403 "Upgrade to GitHub Pro or make this
  repository public to enable this feature"). The job still fails loudly (no `continue-on-error`)
  and shows a red ❌ on the PR's checks list when a check fails, but nothing in this repo's files can
  make that failure block the merge button today — that would require a GitHub-side plan/visibility
  change, not a workflow-file change.

### `regression` — runs on `schedule` (nightly, `0 6 * * *` UTC) and `workflow_dispatch`

```yaml
env:
  RUN_ENV: ${{ github.event.inputs.env || 'qa' }}
  RUN_SUITE: ${{ github.event.inputs.suite || 'regression' }}
run: ./mvnw -B clean test -P"$RUN_SUITE" -Denv="$RUN_ENV" -Dauth.api.key.value=${{ secrets.QA_API_KEY }}
```

**Which suite actually runs depends on the trigger — state this precisely, it is easy to get wrong:**

- **`schedule` (nightly cron)** carries no `workflow_dispatch` inputs at all, so
  `github.event.inputs.suite` is unset and `RUN_SUITE` falls through to its `|| 'regression'`
  default. **The nightly cron run is the regression suite.**
- **`workflow_dispatch` (manual run)** always resolves to an actual `suite` input value, because
  GitHub Actions applies that input's own declared default (`default: "smoke"`, in the workflow's
  `on.workflow_dispatch.inputs.suite` block) whenever you don't explicitly override it. So
  `RUN_SUITE` is `"smoke"` unless you explicitly pick `regression` in the manual-run dialog.
  **Manually dispatching this workflow without changing the `suite` input does NOT run
  regression — it runs smoke.** This is the single most common way to misread this job; if you
  intend to trigger a manual regression run, you must select `regression` explicitly.
- **Not matrixed over `app`, regardless of which trigger fired it or which suite it runs.** This
  job never passes `-Dapp=`, so it always falls through to the pom's `appA` default
  (`<app>appA</app>` in `pom.xml`, the same value as `ConfigManager.DEFAULT_APP`) — for the nightly
  cron run and for every manual dispatch alike. **This means App B's regression suite is currently
  never exercised by CI, on any schedule or trigger.** This is a known, currently-open gap, not
  something already fixed — it stays true unless a future, explicitly scoped change (see
  **Extending the regression job to cover App B** below) adds an app matrix/input to this job.
- `workflow_dispatch` also accepts `env` (`dev`/`qa`/`stage`, default `qa`), but there is **no
  `app` input** at all — manual runs carry the same App-A-only limitation described above no
  matter which suite you select.
- Same artifact-upload behavior as `smoke`, named `reports-regression-<run_number>`.

## Two different kinds of parallelism — don't conflate them

| Layer | Mechanism | Scope | Where it's set |
|---|---|---|---|
| CI-level | GitHub Actions `strategy.matrix.app` | Process isolation — two separate runner VMs, two separate JVMs | `.github/workflows/tests.yml`'s `smoke` job |
| In-process | TestNG `parallel="methods"` / `thread-count` | Threads inside one JVM, one Maven/Surefire invocation | `suites/<app>/*.xml` (`parallel="methods" thread-count="3"` for App A, `thread-count="1"` for App B) |

Raising a suite's `thread-count` does not add more CI jobs, and adding an app to the CI matrix
does not change how many threads that app's own suite run uses. They are independent knobs that
happen to both be called "parallel."

## Extending the regression job to cover App B

This is the same, already-identified pattern applied to `smoke`: either add a second job for App
B, or add `strategy.matrix.app: [appA, appB]` to the existing `regression` job and pass
`-Dapp=${{ matrix.app }}` the same way `smoke` does. No `pom.xml` change is required —
`-Dapp` is already a first-class Maven property. Do not implement this speculatively; treat it as
a real, scoped change (its own PR) with its own review, since it touches the CI workflow file.

## Secrets

`QA_API_KEY` must exist as a GitHub Actions repository secret before `smoke` or `regression` can
authenticate against App A — nothing in this repo can create that secret automatically. It's passed
via `-D`, never written to a file. `framework-health` needs no secret at all — it never calls App A,
App B, or any other API.

## Before changing the workflow file

1. Confirm which job(s) your change actually needs to touch — don't edit both if only one applies.
2. Preserve `fail-fast: false` on any per-app matrix job — removing it would let one app's known
   external failure hide whether the other app's job ran at all.
3. Preserve the artifact upload step (`if: always()`) on any new job that runs tests and produces
   Allure/Extent/Surefire output — a red build must stay diagnosable from the Actions UI. A
   non-test static-check job like `framework-health`, which produces no such output, has no
   artifact step to preserve; its own log output is the diagnostic.
4. Update this file and, if the change affects what's described there, `README.md`'s
   **Continuous Integration** section and `DESIGN.md`'s **CI/CD flow** section, in the same PR.
5. This file is on the protected-files list — get explicit approval before merging a change to it.
