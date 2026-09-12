# AI/Onboarding Skills Index

This directory is a repository-grounded instruction layer: every file here describes how *this*
codebase actually works, not general REST Assured/TestNG advice. It exists so that a new engineer,
or a fresh AI coding agent with no conversation history, can become productive without having to
reverse-engineer the framework from source alone, and so that recurring tasks (add an API, debug a
failure, review a PR) follow the same checklist every time.

Read [`../../AGENTS.md`](../../AGENTS.md) first — it has the hard rules. Then come back here and
pick the guide that matches your task.

## Guides

| Guide | Use it when |
|---|---|
| [`ONBOARDING.md`](ONBOARDING.md) | You're new to this repo (human or agent) and need the tour: setup, project layout, how to run tests, and the core concepts (multi-app boundary, `ServiceConfig`, two-layer retry, reporting). |
| [`ADD_NEW_API.md`](ADD_NEW_API.md) | You've been asked to "add support for API X" and don't yet know whether that means a new endpoint, a new service, or a whole new application. Start here — it routes you to the right guide. |
| [`ADD_NEW_APPLICATION.md`](ADD_NEW_APPLICATION.md) | You're adding a third independent target application (`apps/appC/`), the same way App B was added alongside App A. |
| [`ADD_NEW_SERVICE.md`](ADD_NEW_SERVICE.md) | An *existing* application now needs to talk to a second backend, via the `services.<name>.*` config convention (`ServiceConfig`). |
| [`DEBUG_TEST_FAILURE.md`](DEBUG_TEST_FAILURE.md) | A test just failed (locally or in CI) and you need to determine whether it's a real regression, a flaky external dependency, or a config/environment problem — before touching any code. |
| [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md) | You're hitting a setup/build/execution error (not a test assertion failure) — a specific exception message, a Maven error, a suite that won't resolve. |
| [`CI.md`](CI.md) | You're touching `.github/workflows/tests.yml`, investigating a CI-only failure, or need to understand what the pipeline does and does not currently cover. |
| [`REPORTING.md`](REPORTING.md) | You're changing anything that affects Allure or ExtentReports output, or need to explain what's already in a generated report. |
| [`REVIEW_CHECKLIST.md`](REVIEW_CHECKLIST.md) | You (or a reviewer) are about to approve a PR against this repo. |
| [`API_TEST_GENERATION.md`](API_TEST_GENERATION.md) | You're using an AI agent to draft new tests, endpoints, or POJOs, and need the safe, human-reviewed workflow for doing that in this framework. |

## How these guides relate to the existing root docs

- [`../../README.md`](../../README.md) and [`../../DESIGN.md`](../../DESIGN.md) are the canonical,
  authoritative description of the framework as it exists today. The `docs/AI/` guides do not
  duplicate that content — they add task-oriented checklists and triage flows on top of it, and
  link back to the specific sections that explain *why*.
- If a `docs/AI/` guide and `DESIGN.md`/`README.md` ever disagree, treat `DESIGN.md`/`README.md`
  as correct and flag the `docs/AI/` guide as stale — it should be corrected in the same change
  that caused the drift (see rule 7 in `AGENTS.md`).

## Maintaining this layer

These guides are documentation, not code — they still rot. When you change something a guide
describes (a new suite variant, a CI job, a config key), update the guide in the same PR. When you
add a genuinely new, recurring category of task that isn't covered by an existing guide, add a new
file here and list it in the table above rather than overloading an existing guide with an
unrelated topic.
