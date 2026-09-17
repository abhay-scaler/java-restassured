# AI-Assisted API Test Generation — Safe Workflow

This framework has **no runtime AI** — nothing here calls a model during test execution. This
document is about using an AI coding agent (Claude Code, Copilot, or similar) *during
development* to draft new endpoints, POJOs, or test classes, and how to do that without letting
generated code degrade the architecture or get merged unreviewed.

Everything an AI agent produces under this workflow is a **draft for human review**, never
auto-merged, never auto-pushed to a protected branch without the review step below actually
happening.

## Before generating anything

1. Point the agent at [`../../AGENTS.md`](../../AGENTS.md) and the specific guide for the task —
   [`ADD_NEW_API.md`](ADD_NEW_API.md) to route to the right one of
   [`ADD_NEW_APPLICATION.md`](ADD_NEW_APPLICATION.md) / [`ADD_NEW_SERVICE.md`](ADD_NEW_SERVICE.md)
   / the "new endpoint" case. An agent that hasn't read the multi-app boundary rule is very likely
   to "helpfully" add an `if (app.equals(...))` somewhere in shared code the first time it hits a
   difference between App A and App B.
2. Give the agent the actual current repository state, not a description of it — have it read the
   relevant existing `endpoints/*Endpoints.java`, `models/`, and one existing test class in the
   target application first, so generated code matches existing conventions instead of inventing
   new ones (a different POJO style, a different assertion style, a different endpoint-constant
   naming scheme).
3. Never let an agent invent assertions about behavior it hasn't observed. If it's writing a test
   against App A's `/users/{id}` and doesn't know what a real response looks like, it should say
   so and either fetch a real sample response or leave the assertion narrower — not assert a
   plausible-looking but unverified response shape.

## What's safe to generate directly

- Boilerplate request/response POJOs that mirror a real, already-observed response shape.
- Endpoint constant classes (`*Endpoints.java`) — low risk, easy to review at a glance.
- New `@Test` methods that extend `BaseTest` and follow an existing test class's pattern in the
  same application.
- `@DataProvider` methods and JSON/CSV fixtures, once the actual API contract is known.

## What needs extra scrutiny before merging

- **Anything touching a protected file** (see [`../../AGENTS.md`](../../AGENTS.md) rule 2) — an
  agent should flag this explicitly and stop for approval rather than editing and reporting it as
  done.
- **JSON Schema files** — an agent-generated schema should be checked against a real captured
  response, not just what the POJO fields imply, since the two can drift.
- **Assertions on external, less-controllable APIs** (App A/App B here) — an agent unfamiliar with
  the known 429/403/418 quirks (see [`DEBUG_TEST_FAILURE.md`](DEBUG_TEST_FAILURE.md)) may write a
  test that's flaky by construction, or "fix" a real bug by loosening an assertion instead of
  understanding the failure.
- **Any suggestion to add retry, caching, or special-casing to shared code** to work around a
  specific application's quirk — this is the most common way generated code introduces
  app-specific branching into core files. Redirect it to a config-level or per-application fix
  instead.

## Review gate before merging AI-generated test/code changes

1. Read the full diff yourself — don't rely on the agent's own summary of what it did. An agent's
   description of its changes is a claim, not a verification.
2. Run [`REVIEW_CHECKLIST.md`](REVIEW_CHECKLIST.md) against the diff, same as any other PR.
3. Actually run the generated tests (`mvn test -Dapp=<app> -Denv=qa -Dtest=<NewClass>`) and read
   the real output — don't accept "the agent says it passed" without seeing the Surefire/Extent
   result yourself. Generated tests usually land in `regression`, which the required PR checks do
   not execute — see [`CI_EXECUTION_EVIDENCE.md`](CI_EXECUTION_EVIDENCE.md) for what green checks
   do and don't prove, and how to get real execution evidence.
4. Confirm the generated code didn't duplicate an existing helper (`ResponseValidator`,
   `RandomDataGenerator`, `HttpLogFormatter`) with a new inline equivalent — agents unfamiliar with
   the codebase tend to reinvent utilities that already exist.
5. If the agent proposed a documentation change (this directory, `README.md`, `DESIGN.md`), verify
   it against the actual code the same way this file itself was — don't let documentation drift
   from reality by trusting a generated description of what the code does.

## What this workflow deliberately does not include

There is no automated pipeline step where an agent generates tests and they get merged without a
human reading them. If a future phase adds one, it needs its own explicit design (gating criteria,
what triggers generation, how failures are surfaced) — this document intentionally stops at "draft,
then human review," which is Phase 1's scope.
