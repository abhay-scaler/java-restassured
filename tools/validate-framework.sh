#!/usr/bin/env bash
#
# Deterministic, static repository-health checks for java-restassured.
#
# This is NOT a test runner and it is not AI-driven. It never executes the
# test suite, never decides whether a test passed or failed, and never
# modifies any file. It only checks a small set of mechanical invariants
# that AGENTS.md and docs/AI/*.md already document, and reports violations
# with the file/line and a concrete remediation. See docs/AI/SKILLS.md for
# where these invariants come from.
#
# Usage: ./tools/validate-framework.sh
# Exit status: 0 if every check passes, 1 if any check fails.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT" || exit 2

if ! command -v xmllint >/dev/null 2>&1; then
    echo "validate-framework.sh: 'xmllint' is required (checks B and C parse TestNG suite XML) but was not found on PATH." >&2
    echo "Install it (e.g. 'apt-get install libxml2-utils' or 'brew install libxml2') and re-run." >&2
    exit 2
fi

CHECKS=5
FAILURES=0
declare -a FINDINGS=()

# ---------------------------------------------------------------------------
# record_failure <check-id> <location> <problem> <remediation>
# ---------------------------------------------------------------------------
record_failure() {
    local check_id="$1" location="$2" problem="$3" remediation="$4"
    FAILURES=$((FAILURES + 1))
    FINDINGS+=("[$check_id] $location
    Problem:     $problem
    Remediation: $remediation")
}

# strip_comments <file>
# Character-level stripper for // line comments and /* ... */ block comments
# (including ones spanning multiple lines / Javadoc). One output line per
# input line, so line numbers in the stripped stream still match the source
# file. This is a heuristic, not a real Java parser: it does not understand
# string/char literals, so a literal containing "//" or "/*" could in theory
# be mis-stripped. That case does not occur in this codebase's shared-core
# files today; if it ever does, prefer fixing the literal over trusting a
# false negative here.
strip_comments() {
    awk '
    BEGIN { incomment = 0 }
    {
        line = $0
        out = ""
        i = 1
        len = length(line)
        while (i <= len) {
            c2 = substr(line, i, 2)
            if (incomment) {
                if (c2 == "*/") { incomment = 0; i += 2; continue }
                i++
                continue
            } else {
                if (c2 == "/*") { incomment = 1; i += 2; continue }
                if (c2 == "//") { break }
                out = out substr(line, i, 1)
                i++
            }
        }
        print out
    }' "$1"
}

# ---------------------------------------------------------------------------
# Check A — shared-core code must not branch on application name.
#
# AGENTS.md rule 1: the only accepted "appA"/"appB" reference anywhere in
# protected shared-core code is the ConfigManager.DEFAULT_APP constant
# itself. Everything else (a branch, a lookup, a case label) belongs in
# src/main/java/com/framework/apps/<app>/ instead.
# ---------------------------------------------------------------------------
check_shared_core_app_branching() {
    local protected_dirs=(auth builders clients config constants dataproviders \
        exceptions filters listeners reporting retry utils validators)
    local files=()
    local d f

    for d in "${protected_dirs[@]}"; do
        while IFS= read -r f; do
            files+=("$f")
        done < <(find "src/main/java/com/framework/$d" -type f -name "*.java" 2>/dev/null | sort)
    done
    if [ -f "src/test/java/com/framework/base/BaseTest.java" ]; then
        files+=("src/test/java/com/framework/base/BaseTest.java")
    fi

    for f in "${files[@]}"; do
        [ -f "$f" ] || continue
        while IFS=: read -r lineno line; do
            [ -z "${lineno:-}" ] && continue
            if echo "$line" | grep -qE 'DEFAULT_APP[[:space:]]*=[[:space:]]*"appA"'; then
                continue
            fi
            local trimmed
            trimmed="$(echo "$line" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
            record_failure "A:shared-core-app-branching" "$f:$lineno" \
                "protected shared-core code references an application name outside the ConfigManager.DEFAULT_APP exception: \`$trimmed\`" \
                "Move application-specific logic into src/main/java/com/framework/apps/<app>/, or read it generically via ConfigManager/ServiceConfig instead of branching on the app name (AGENTS.md rule 1)."
        done < <(strip_comments "$f" | grep -nE '(^|[^A-Za-z0-9_])(appA|appB)([^A-Za-z0-9_]|$)')
    done
}

# ---------------------------------------------------------------------------
# Check B — every active suite XML must register both framework listeners.
#
# Omitting either <listener> entry does not fail the build or any test — it
# silently stops retry (RetryListener) or Allure/Extent population
# (TestListener) for that suite. See docs/AI/ONBOARDING.md section 5.
# ---------------------------------------------------------------------------
check_suite_listeners() {
    local required=("com.framework.listeners.TestListener" "com.framework.retry.RetryListener")
    local suite names req

    while IFS= read -r suite; do
        [ -f "$suite" ] || continue
        if ! xmllint --noout "$suite" 2>/dev/null; then
            record_failure "B:suite-not-well-formed-xml" "$suite" \
                "file is not well-formed XML; listener registration could not be verified." \
                "Fix the XML syntax error reported by 'xmllint --noout $suite'."
            continue
        fi
        names="$(xmllint --xpath '//listener/@class-name' "$suite" 2>/dev/null | grep -oE '"[^"]*"' | tr -d '"')"
        for req in "${required[@]}"; do
            if ! grep -qxF "$req" <<< "$names"; then
                record_failure "B:missing-required-listener" "$suite" \
                    "missing required <listener class-name=\"$req\"/>." \
                    "Add it inside the <listeners> block — copy the block verbatim from src/test/resources/suites/appA/testng.xml."
            fi
        done
    done < <(find src/test/resources/suites -type f -name "*.xml" 2>/dev/null | sort)
}

# ---------------------------------------------------------------------------
# Check C — every <class name="..."> referenced by a suite must exist on disk.
#
# Only checks that the referenced class exists; makes no judgment about
# whether it *should* be in that suite.
# ---------------------------------------------------------------------------
check_suite_class_references() {
    local suite classes fqcn relpath

    while IFS= read -r suite; do
        [ -f "$suite" ] || continue
        xmllint --noout "$suite" 2>/dev/null || continue  # already reported by check B
        classes="$(xmllint --xpath '//class/@name' "$suite" 2>/dev/null | grep -oE '"[^"]*"' | tr -d '"')"
        [ -z "$classes" ] && continue
        while IFS= read -r fqcn; do
            [ -z "$fqcn" ] && continue
            relpath="$(echo "$fqcn" | tr '.' '/').java"
            if [ ! -f "src/test/java/$relpath" ] && [ ! -f "src/main/java/$relpath" ]; then
                record_failure "C:suite-class-missing" "$suite" \
                    "references class '$fqcn', which has no corresponding source file (expected src/test/java/$relpath)." \
                    "Fix the class name in the suite XML, or add the missing class. Do not leave a suite referencing a class that doesn't exist."
            fi
        done <<< "$classes"
    done < <(find src/test/resources/suites -type f -name "*.xml" 2>/dev/null | sort)
}

# ---------------------------------------------------------------------------
# Check D — services.<name>.<key> keys must use a key ServiceConfig actually
# reads.
#
# ServiceConfig.value()/intValue() only ever look up these ten suffixes; any
# other suffix (a typo like base-url/base_url, or anything else) is silently
# ignored and falls back to the app-level default with no error anywhere.
# Keep this list in sync with src/main/java/com/framework/config/ServiceConfig.java.
# ---------------------------------------------------------------------------
check_service_key_convention() {
    local valid_keys="|base.url|base.path|connection.timeout|socket.timeout|auth.type|auth.username|auth.password|auth.token|auth.api.key.name|auth.api.key.value|"
    local propfile lineno matched rest key_suffix

    while IFS= read -r propfile; do
        [ -f "$propfile" ] || continue
        while IFS=: read -r lineno matched; do
            [ -z "${lineno:-}" ] && continue
            rest="${matched#services.}"
            key_suffix="${rest#*.}"
            key_suffix="${key_suffix%=}"
            if [[ "$valid_keys" != *"|$key_suffix|"* ]]; then
                record_failure "D:malformed-service-key" "$propfile:$lineno" \
                    "unrecognized service key suffix '$key_suffix' (from '${matched%=}'); ServiceConfig will never read this key and will silently fall back to the app-level default." \
                    "Use one of the recognized ServiceConfig suffixes: base.url, base.path, connection.timeout, socket.timeout, auth.type, auth.username, auth.password, auth.token, auth.api.key.name, auth.api.key.value."
            fi
        done < <(grep -noE '^services\.[^.=]+\.[^=]+=' "$propfile")
    done < <(find src/test/resources/config -type f -name "*.properties" 2>/dev/null | sort)
}

# ---------------------------------------------------------------------------
# Check E — every @Test-bearing class under apps/<app>/tests/ must be
# registered in at least one of that application's suite XML files.
#
# The inverse of Check C: Check C ensures every suite <class name="...">
# reference resolves to a real file; this ensures every real test class
# under the documented apps/<app>/tests/ convention (see README.md "Project
# layout" / DESIGN.md "Project structure") is reachable from at least one
# suite variant for its own application. Registration in any one of
# testng.xml/smoke.xml/regression.xml is sufficient — smoke.xml is
# intentionally a subset and is never expected to list everything.
#
# Deliberately scoped to apps/<app>/tests/ only, not the whole test tree:
# a class like src/test/java/com/framework/reporting/ReportingMetadataTests.java
# lives outside that convention specifically because it is not meant to run
# as part of any live suite (see its own Javadoc) — Check E does not see it.
# ---------------------------------------------------------------------------
check_test_class_suite_registration() {
    local apps_dir="src/test/java/com/framework/apps"
    local suites_root="src/test/resources/suites"
    local app_path app tests_dir suite_dir suite f fqcn registered

    [ -d "$apps_dir" ] || return

    for app_path in "$apps_dir"/*/; do
        [ -d "$app_path" ] || continue
        app="$(basename "$app_path")"
        tests_dir="${app_path}tests"
        [ -d "$tests_dir" ] || continue

        suite_dir="$suites_root/$app"
        registered=""
        if [ -d "$suite_dir" ]; then
            while IFS= read -r suite; do
                [ -f "$suite" ] || continue
                xmllint --noout "$suite" 2>/dev/null || continue  # already reported by check B
                registered="$registered
$(xmllint --xpath '//class/@name' "$suite" 2>/dev/null | grep -oE '"[^"]*"' | tr -d '"')"
            done < <(find "$suite_dir" -type f -name "*.xml" 2>/dev/null | sort)
        fi

        while IFS= read -r f; do
            [ -f "$f" ] || continue
            strip_comments "$f" | grep -qE '@Test\b' || continue
            fqcn="$(echo "$f" | sed -E 's#^src/test/java/##; s#\.java$##; s#/#.#g')"
            if ! grep -qxF "$fqcn" <<< "$registered"; then
                record_failure "E:test-class-not-in-suite" "$f" \
                    "defines an @Test method but class '$fqcn' is not referenced by any suite XML under $suite_dir/ (testng.xml, smoke.xml, or regression.xml) — it will never run." \
                    "Add <class name=\"$fqcn\"/> to the relevant <classes> block in one of $suite_dir/{testng,smoke,regression}.xml (see docs/AI/ADD_NEW_API.md step 7). A class meant to run ad hoc, never as part of a live suite, should live outside apps/<app>/tests/ instead — see src/test/java/com/framework/reporting/ReportingMetadataTests.java for the established pattern."
            fi
        done < <(find "$tests_dir" -type f -name "*.java" 2>/dev/null | sort)
    done
}

# ---------------------------------------------------------------------------
# Run all checks
# ---------------------------------------------------------------------------
check_shared_core_app_branching
check_suite_listeners
check_suite_class_references
check_service_key_convention
check_test_class_suite_registration

echo
if [ "$FAILURES" -eq 0 ]; then
    echo "Framework validation: PASS"
    echo "Checks: $CHECKS"
    echo "Failures: 0"
    echo
    echo "This confirms the mechanical invariants below hold. It does not mean the"
    echo "repository is healthy in general — it does not run tests, lint, or type-check."
    echo "Checked:"
    echo "  A. No appA/appB references in protected shared-core code (AGENTS.md rule 1)."
    echo "  B. Every suite XML registers TestListener and RetryListener."
    echo "  C. Every suite <class name=\"...\"> resolves to a source file on disk."
    echo "  D. Every services.<name>.<key> key uses a suffix ServiceConfig actually reads."
    echo "  E. Every @Test-bearing class under apps/<app>/tests/ is registered in that app's suites."
    exit 0
else
    echo "Framework validation: FAIL"
    echo "Checks: $CHECKS"
    echo "Failures: $FAILURES"
    echo
    for finding in "${FINDINGS[@]}"; do
        echo "$finding"
        echo
    done
    exit 1
fi
