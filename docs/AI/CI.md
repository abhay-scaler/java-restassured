# CI

What `.github/workflows/tests.yml` actually does today, what it doesn't cover, and how to extend
it safely. This file is a protected file per [`../../AGENTS.md`](../../AGENTS.md) — read this
guide fully before proposing a change to it.

## The two jobs

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

### `regression` — runs on `schedule` (nightly, `0 6 * * *` UTC) and `workflow_dispatch`

```yaml
run: ./mvnw -B clean test -P"$RUN_SUITE" -Denv="$RUN_ENV" -Dauth.api.key.value=${{ secrets.QA_API_KEY }}
```

- **Not matrixed over `app`.** It never passes `-Dapp=`, so it always falls through to the pom's
  `appA` default (`<app>appA</app>` in `pom.xml`, the same value as
  `ConfigManager.DEFAULT_APP`). **This means App B's regression suite is currently never exercised
  on any schedule.** This is a known, currently-open gap — not something already fixed, and not
  something to describe as fixed in any doc.
- `workflow_dispatch` accepts `env` (`dev`/`qa`/`stage`, default `qa`) and `suite`
  (`smoke`/`regression`, default `smoke`) inputs, but **no `app` input** — manual runs have the
  same App-A-only limitation.
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

`QA_API_KEY` must exist as a GitHub Actions repository secret before either job can authenticate
against App A — nothing in this repo can create that secret automatically. It's passed via `-D`,
never written to a file.

## Before changing the workflow file

1. Confirm which job(s) your change actually needs to touch — don't edit both if only one applies.
2. Preserve `fail-fast: false` on any per-app matrix job — removing it would let one app's known
   external failure hide whether the other app's job ran at all.
3. Preserve the artifact upload step (`if: always()`) on any new job — a red build must stay
   diagnosable from the Actions UI.
4. Update this file and, if the change affects what's described there, `README.md`'s
   **Continuous Integration** section and `DESIGN.md`'s **CI/CD flow** section, in the same PR.
5. This file is on the protected-files list — get explicit approval before merging a change to it.
