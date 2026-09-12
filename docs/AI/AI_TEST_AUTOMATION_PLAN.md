AI Test Automation — Future Implementation Plan
Status

Planned — not yet implemented

This document defines the proposed architecture and operating model for adding AI-assisted test generation and maintenance to the Java REST Assured framework.

The purpose is to capture the design now so implementation can resume later without repeating the analysis or making architectural decisions from scratch.

1. Objective

Build an AI-assisted testing workflow that can:

Analyze the existing API test framework.
Understand project-specific testing conventions.
Read and follow docs/AI/SKILLS.md.
Generate new REST Assured tests.
Identify missing test scenarios.
Generate positive, negative, boundary, validation, authentication, and regression tests.
Run generated tests.
Analyze failures.
Refine generated tests when appropriate.
Produce a Pull Request containing the proposed changes.
Allow existing GitHub Actions and branch protection rules to remain the final quality gate.

The AI must not directly modify or merge into main.

The preferred model is:

AI generates and validates → CI verifies → human reviews → protected main accepts the change.

2. Guiding Principle

The AI should behave as a contributor to the test suite, not as an autonomous maintainer with unrestricted repository access.

The AI may propose changes, but repository protection remains the authority.

Required flow
Request
  ↓
AI analyzes repository
  ↓
AI reads docs/AI/SKILLS.md
  ↓
AI analyzes existing tests and API behavior
  ↓
AI generates candidate tests
  ↓
AI runs validation/tests
  ↓
AI analyzes failures
  ↓
AI creates branch
  ↓
AI commits test changes
  ↓
AI opens Pull Request
  ↓
GitHub Actions
  ↓
Human review
  ↓
Protected main

3. SKILLS.md as the AI Contract

docs/AI/SKILLS.md should be treated as the primary project-specific instruction source for AI test automation.

The AI agent should read it before generating or modifying tests.

The agent must follow the repository's existing conventions rather than introducing generic AI-generated testing patterns.

Future skills should cover at least:

api-test-generation

Generate tests for API endpoints based on available API documentation, specifications, implementation behavior, and existing tests.

negative-test-generation

Generate tests for:

Invalid input
Missing required fields
Invalid authentication
Missing authentication
Invalid parameters
Unsupported values
Invalid request structures
Expected HTTP error responses
boundary-test-generation

Identify and test meaningful boundaries such as:

Minimum/maximum values
Empty values
Null values
String length limits
Collection boundaries
Numeric boundaries
Date/time boundaries

The AI must avoid inventing arbitrary limits when the actual API contract does not establish them.

authentication-test-generation

Generate tests around the authentication mechanisms already supported by the framework.

The AI must not expose credentials, print secret values, or create hard-coded secrets.

regression-test-generation

When a defect or failed test identifies a reproducible problem, generate a focused regression test that reproduces the behavior before fixing it.

test-failure-analysis

Analyze test failures and determine whether they appear to originate from:

Test code
Application/API behavior
Configuration
Authentication
Environment
External service
Infrastructure
Flaky behavior

The agent should not automatically rewrite a test merely to make CI green.

coverage-analysis

Identify areas where API behavior appears insufficiently tested.

Coverage suggestions should be based on observable API behavior and project conventions rather than arbitrary test-count targets.

test-data-generation

Generate safe test data consistent with the API contract.

Never generate or commit real credentials, tokens, personal information, production data, or other sensitive material.

4. Source-of-Truth Hierarchy

When determining expected behavior, the AI should use the following priority order:

Explicit API contract/specification
Existing application/API behavior
Existing project tests
Existing project documentation
docs/AI/SKILLS.md
Established repository conventions
Reasonable inference

The AI must not invent API behavior merely to complete a generated test.

If expected behavior is ambiguous or conflicting, the AI should stop and request human clarification.

5. Repository Discovery

Before generating tests, the AI should inspect the repository structure and identify:

Existing test packages
Existing REST Assured utilities
Authentication mechanisms
Configuration handling
Environment configuration
Test fixtures
Request/response models
Assertion conventions
Maven configuration
Existing CI workflows
Existing validation scripts
Relevant documentation
docs/AI/SKILLS.md

The agent should prefer existing utilities over creating duplicate infrastructure.

6. Test Generation Rules

Generated tests should:

Follow existing naming conventions.
Follow existing package structure.
Reuse existing framework utilities.
Reuse existing authentication/configuration mechanisms.
Use meaningful test names.
Contain focused assertions.
Avoid unnecessary duplication.
Be deterministic where possible.
Avoid arbitrary sleeps.
Avoid hard-coded credentials.
Avoid production data.
Avoid changing production code unless explicitly authorized.
Avoid weakening assertions merely to achieve a passing result.

The AI should explain why each generated test exists.

7. Generated Test Quality

A generated test should answer:

What behavior is being tested?
Why is this behavior important?
What is the expected result?
What API contract or existing behavior establishes that expectation?
What failure would this test detect?

A test should not be generated merely because another endpoint has a similar test.

8. Failure Handling

When generated tests fail, the AI should first classify the failure.

Test defect

The generated test is incorrect or incomplete.

The AI may repair it.

Application/API defect

The API appears to violate the expected contract.

The AI should preserve the failing test and report the suspected defect rather than weakening the assertion.

Environment/configuration failure

Examples:

Missing environment variable
Missing GitHub secret
Unavailable external dependency
Incorrect configuration

The AI should report the dependency and avoid embedding a secret or workaround.

External service failure

The AI should distinguish an external outage or service limitation from an application regression.

Flaky behavior

The AI should collect evidence before adding retries or changing timing.

Retries must never be used simply to hide failures.

9. Security Requirements

Security is a hard boundary.

The AI must never:

Print secret values.
Commit secrets.
Put credentials into test source.
Put credentials into generated reports.
Echo GitHub Actions secrets.
Hash secrets for identification.
Upload secrets to an external AI service.
Include real personal information unnecessarily.
Copy production credentials into test fixtures.

Secrets must remain in the existing configuration/secret-management mechanism.

For CI, GitHub Actions secrets such as QA_API_KEY must be referenced through the existing secret mechanism.

10. Git and Branch Rules

The AI agent must never push generated changes directly to main.

Preferred workflow:

main
 │
 └── ai/test-generation/<short-description>
             │
             ├── generated tests
             ├── validation
             └── commit
                    │
                    ↓
                  Pull Request


The agent should create a Pull Request rather than merge it.

Existing branch protection remains the final enforcement layer.

11. Pull Request Requirements

An AI-generated PR should contain:

Title

A concise description of the testing change.

Example:

test: add negative coverage for user creation

Description

The PR should explain:

What tests were added.
Why they were added.
Which API behavior they cover.
Which project conventions were followed.
What validation was executed.
Test results.
Any assumptions.
Any unresolved ambiguity.

Example structure:

## What changed

Added negative API coverage for POST /users.

## Scenarios

- Missing required field
- Invalid field value
- Unauthorized request

## Validation

- Maven tests: PASS
- Framework validation: PASS
- git diff --check: PASS

## Notes

No production code changed.
No credentials were added.

12. GitHub Actions Integration

The existing CI should remain the authoritative automated validation layer.

The AI workflow should not duplicate the entire CI pipeline unnecessarily.

Preferred separation:

AI workflow

Responsible for:

Reading the request.
Generating candidate tests.
Running focused validation.
Creating the PR.
Existing CI

Responsible for:

Full framework validation.
Smoke tests.
Required status checks.
Regression protection.

The AI workflow should wait for or report the results of the existing CI checks rather than replacing them.

13. Trigger Options

The eventual implementation can support multiple triggers.

Option A — Issue-driven

A developer creates an issue:

Add API tests for POST /users


The AI generates a PR.

Option B — PR-driven

A developer adds a label such as:

ai-test-generation


The agent analyzes the PR and proposes additional tests.

Option C — Coverage-driven

A scheduled workflow periodically identifies API areas with insufficient test coverage and opens proposed PRs.

Option D — Failure-driven

A failed CI test or defect issue triggers the AI to propose a regression test.

The initial implementation should start with Issue-driven generation because it provides the clearest human intent and the lowest automation risk.

14. Recommended First Version

Do not implement every capability at once.

Phase 1 — Manual AI test generation

Provide the AI with:

Repository
docs/AI/SKILLS.md
API requirements
Existing tests

AI produces a PR.

No autonomous issue scanning.

Phase 2 — Issue-driven automation

Add a GitHub Action that responds to an explicitly labelled issue.

Example:

label: ai-test-generation


The agent generates tests and opens a PR.

Phase 3 — Failure-driven regression tests

Allow the AI to analyze selected CI failures and propose regression tests.

Phase 4 — Coverage analysis

Allow scheduled analysis of test coverage and missing scenarios.

Phase 5 — Advanced maintenance

Potential future capabilities:

Flaky-test detection
Test deduplication
Test optimization
API contract drift detection
Automated regression-test suggestions
15. Human Approval Boundaries

The following should always require human review:

Production-code changes
Changes to authentication behavior
Changes to security controls
Changes to CI permissions
Changes to GitHub Actions secrets
Changes to branch protection
Changes to API contracts
Deleting or substantially modifying existing tests
Disabling or weakening assertions
Merging AI-generated PRs

The AI may recommend these changes but should not silently perform them.

16. Non-Goals

The initial system should not attempt to:

Autonomously merge PRs.
Modify main.
Modify GitHub security settings.
Manage secrets.
Rewrite repository history.
Invent API requirements.
Replace human review.
Replace existing CI.
Optimize for number of generated tests.
Generate tests solely to increase coverage percentages.

Quality is more important than test quantity.

17. Success Criteria

The AI testing system will be considered successful if it can reliably:

Read and follow docs/AI/SKILLS.md.
Understand existing project conventions.
Generate useful tests without modifying production code.
Reuse existing test infrastructure.
Detect and report ambiguity.
Run the generated tests.
Distinguish test failures from product defects.
Avoid exposing credentials.
Create a clean Pull Request.
Pass the repository's existing CI checks.
Leave the final merge decision to a human.
18. Security and Repository Constraints

The following constraints from the public repository setup remain in force:

main is protected.
Pull requests are required.
Required CI checks must pass.
Force pushes are disabled.
Branch deletion is disabled.
External fork PRs require approval.
GitHub Actions default token permissions remain read-only.
QA_API_KEY remains a GitHub Actions secret.
No credentials may be committed to the repository.

These controls should not be weakened to accommodate AI automation.

19. Future Implementation Checklist

When this project is resumed:

Design
 Review this document.
 Review current docs/AI/SKILLS.md.
 Define the first AI provider/model.
 Define the execution environment.
 Define the exact GitHub permissions required.
 Define the AI workflow trigger.
Repository
 Add/update AI testing skills in docs/AI/SKILLS.md.
 Add the AI workflow under .github/workflows/.
 Add any required agent configuration.
 Document the workflow.
Security
 Confirm no secrets are passed unnecessarily to the AI.
 Confirm GitHub token permissions are minimal.
 Confirm AI cannot push directly to main.
 Confirm generated PRs require existing CI.
 Confirm generated changes receive human review.
Validation
 Test generation against one small API.
 Verify generated tests follow project conventions.
 Verify CI passes.
 Verify failed tests are reported correctly.
 Verify no credentials appear in generated output.
 Verify the agent cannot bypass branch protection.
Rollout
 Start with issue-driven generation.
 Monitor generated PR quality.
 Refine SKILLS.md.
 Only then consider autonomous coverage/failure-driven workflows.
20. Decision Record

The following decisions have been made for the future implementation:

Decision	Choice
AI role	Test-generation and test-maintenance assistant
Primary instruction source	docs/AI/SKILLS.md
Direct main modification	No
Autonomous merge	No
Production-code modification	No, unless explicitly authorized
Secret management	Existing GitHub Actions/configuration mechanism
CI authority	Existing GitHub Actions
Delivery mechanism	Pull Request
Human review	Required
Initial trigger	Issue-driven
First implementation scope	API test generation
Test failure handling	Diagnose before modifying
API ambiguity	Ask human rather than invent behavior
Security posture	Least privilege
Repository history	No further rewrite planned
21. Resume Point

When implementation begins, start here:

Step 1: Review the current docs/AI/SKILLS.md and this document together.

Step 2: Define the first concrete skill:

api-test-generation


Step 3: Select one representative API/endpoint and manually validate the expected AI-generated test structure.

Step 4: Convert that process into an Issue → AI → PR workflow.

Do not begin with autonomous repository-wide test generation.

The first milestone is a single high-quality AI-generated test PR that follows the project's existing conventions and passes all protected CI checks.

Final Principle

AI should make it cheaper to create good tests, not make it easier to merge bad tests.

The repository's existing CI, branch protection, security controls, and human review remain the final authority.
