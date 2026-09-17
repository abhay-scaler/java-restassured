# CI Execution Evidence for New Tests — What "Required Checks Green" Does and Does Not Prove

This guide exists because a Phase 1 AI-test-generation evaluation (see
`AI_TEST_AUTOMATION_PLAN.md`) surfaced a real, non-obvious gap: **a PR can show all three
required checks green without any of its newly added test methods having executed even
once in CI.** Read this before treating a green PR as validated — the distinction below
applies equally to an AI-generated test and a human-written one; it is a property of this
repository's suite/group configuration, not of who authored the change.

## Four things that are easy to conflate — kept separate here

1. **Test registration** — the new `@Test`-bearing class is listed in a `<class
   name="...">` entry in some `suites/<app>/*.xml` file. `tools/validate-framework.sh`
   Check E (part of the required `Framework health validator` check) verifies this
   mechanically. **Registration only proves the test is reachable by a suite. It does not
   run the test.**
2. **Local execution** — running `./mvnw -B test -Dtest=<Class> -Dapp=<app> -Denv=qa`
   yourself. This genuinely executes every method in that class, because Surefire's
   `-Dtest` parameter runs the named class directly and ignores the suite XML's TestNG
   `<groups><run><include .../></run></groups>` filter entirely. This is real, valid
   evidence the test passes against the live API — but it happened on one machine, at one
   point in time, on whatever the branch looked like then, and a reviewer cannot verify it
   without re-running the same command themselves.
3. **CI job execution** — whether the required `Smoke suite (PR / push) - appA` /
   `- appB` checks actually ran the new test's code. They run `./mvnw -Psmoke`, which
   resolves (see `pom.xml`'s `smoke` profile) to `suites/<app>/smoke.xml` — scoped to
   `<include name="smoke"/>` only. **A method tagged only `regression`/`negative` — this
   repository's own stated default for new coverage — is silently skipped by this check,
   every time**, regardless of which suite XML file the class is registered in.
4. **CI green status** — the three required checks (`Smoke suite - appA`, `Smoke suite -
   appB`, `Framework health validator`) reporting "pass". Green here means: the
   pre-existing `smoke`-tagged tests in the affected classes still pass, and the static
   validator found no structural issue (including that the new class satisfies
   registration, item 1 above). **It does not mean the new test method executed.**

`Regression suite (nightly / manual)` — the job that actually runs `regression`-tagged
tests — triggers only on `schedule` or `workflow_dispatch` (see `.github/workflows/
tests.yml`'s `if:` condition), never on `pull_request`/`push`. It is not a required check
and does not run automatically on any PR.

**Net consequence, stated plainly**: a PR whose only new test methods are tagged
`regression`/`negative` can show all three required checks green while zero new test code
has ever executed in CI. Green required checks are necessary but **not sufficient**
evidence that a newly added regression-tagged test works — do not read them as sufficient.

## What to do about it as a reviewer

Until/unless a repository-level mechanism changes this, do **one** of the following before
approving a PR whose new test is not `smoke`-tagged:

1. Manually dispatch the existing regression workflow against the PR's exact branch — this
   requires no new infrastructure; the capability already exists in `tests.yml` today:
   ```bash
   gh workflow run tests.yml --ref <pr-branch> -f suite=regression -f env=qa
   ```
   Then check the resulting run's result for that branch/commit.
2. Or independently re-run `./mvnw -B test -Dtest=<NewClass> -Dapp=<app> -Denv=qa`
   yourself, against the PR's exact commit, rather than trusting a pasted local-run output
   in the PR description without re-verifying it.

Treat a pasted local `-Dtest=` result in a PR description as necessary supporting evidence
(item 2 above), not as a substitute for either of the two actions above, and never as
equivalent to the required checks having covered the change.

## This does not change where new tests belong

This guide is about review discipline, not about test classification. The existing
convention stands: **new coverage still defaults to the `regression` group** unless there
is a specific reason a particular scenario belongs in `smoke` (see `README.md`'s "Writing a
new test" and `AI_TEST_AUTOMATION_PLAN.md` §7). Do not retag a new test as `smoke` merely
to make it execute under the required checks — that changes what `smoke` means for every
other test in that suite and is a different, separate decision a maintainer should make
deliberately, not a workaround for this evidence gap.

## Branch-isolation pattern for multiple independent AI-generated experiments

When an AI agent (or a human) generates several candidate tests in one working session and
they land in overlapping files, isolating each into its own independently-reviewable PR
needs care — diff/patch context from one experiment can collide with another's. The
pattern that held up across a multi-experiment Phase 1 evaluation:

1. For each experiment, create a **fresh branch from `main`** (not from a sibling
   experiment's branch): `git switch -c ai/test-generation/<short-name> main`.
2. Re-apply (or, where hunks overlap, manually re-insert) only that one experiment's
   change against the clean, `main`-based copy of the file — avoid `git cherry-pick`/
   `git apply` across branches whose surrounding file context differs, since a hunk
   generated against one branch's version of a file frequently fails or silently
   mis-applies against another's.
3. Re-run the full local validation (`validate-framework.selftest.sh`,
   `validate-framework.sh`, `mvn test -Dtest=<Class>`, `git diff --check`) **again, on the
   isolated branch** — do not assume validation performed before isolation still applies;
   commit only after re-confirming on the branch that will actually be pushed.
4. Before discarding any `git stash` used to hold pending work during this process, diff
   its content against what was actually committed across the resulting branches to
   confirm nothing was lost or silently altered.
