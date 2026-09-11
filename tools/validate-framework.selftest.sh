#!/usr/bin/env bash
#
# Self-test for tools/validate-framework.sh.
#
# This is a fixture-based regression harness for the *validator itself*,
# not for the repository. Each case builds a small, isolated, throwaway
# directory tree containing only the handful of files needed to exercise
# one specific check (A-E), copies the real, unmodified validate-framework.sh
# into it, runs it there, and asserts on its output. The real repository is
# never read from or written to by any fixture — every fixture is self-
# contained under its own temp directory.
#
# Deterministic, offline, no Maven/JDK/TestNG/network dependency: fixture
# ".java" files are plain text the validator greps, never compiled.
#
# Usage: ./tools/validate-framework.selftest.sh
# Exit status: 0 if every case behaves as expected, 1 if any case doesn't.

set -uo pipefail

SELFTEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VALIDATOR_SRC="$SELFTEST_DIR/validate-framework.sh"

if [ ! -f "$VALIDATOR_SRC" ]; then
    echo "validate-framework.selftest.sh: cannot find $VALIDATOR_SRC" >&2
    exit 2
fi

TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/validate-framework-selftest.XXXXXX")"
cleanup() { rm -rf "$TMP_ROOT"; }
trap cleanup EXIT

SELFTEST_FAILURES=0
SELFTEST_CASES=0

# ---------------------------------------------------------------------------
# add_check_a_placeholder <fixture-dir>
#
# Check A (check_shared_core_app_branching) builds its file list by scanning
# every protected src/main/java/com/framework/<dir>/ directory. When that
# list is empty, the real script now takes a guarded, zero-finding path
# (see the length guard around that loop in validate-framework.sh, and the
# dedicated check-A-empty case below, which exercises exactly that path).
# That empty condition can never happen against the real repository, where
# every protected directory always has at least one file - but a fixture
# built to isolate Checks B-E deliberately has none. Every such fixture
# calls this helper once so Check A always has exactly one harmless file to
# look at (and correctly finds nothing wrong with it), keeping these
# fixtures' assertions about B-E free of any interaction with Check A's
# empty-list handling one way or the other.
# ---------------------------------------------------------------------------
add_check_a_placeholder() {
    mkdir -p "$1/src/main/java/com/framework/auth"
    cat > "$1/src/main/java/com/framework/auth/Placeholder.java" <<'EOF'
package com.framework.auth;

// Placeholder so Check A's file list is never empty in fixtures that are
// only exercising Checks B-E. Deliberately application-agnostic.
public class Placeholder {
    public void noop() {
    }
}
EOF
}

# ---------------------------------------------------------------------------
# run_case <name> <setup-fn> <expected-exit> <expected-finding-or-empty>
#
# Builds a fresh fixture under $TMP_ROOT/<name>, copies the real validator
# into it, runs it, and asserts:
#   - the exit code matches <expected-exit>
#   - if <expected-finding> is non-empty, exactly one finding fires and it
#     is "[<expected-finding>] ..." (proves the check actually detected the
#     intended violation, not just that the script exited non-zero)
#   - if <expected-finding> is empty, zero findings fire (a clean pass)
# Either way, "exactly the expected findings, no more, no fewer" is the
# real assertion - not just the top-level exit code - so an unrelated check
# accidentally also firing on the fixture would be caught as a failure here.
# ---------------------------------------------------------------------------
run_case() {
    local name="$1" setup_fn="$2" expected_exit="$3" expected_finding="${4:-}"
    local case_dir="$TMP_ROOT/$name"
    local expected_findings=0
    [ -n "$expected_finding" ] && expected_findings=1
    SELFTEST_CASES=$((SELFTEST_CASES + 1))

    mkdir -p "$case_dir/tools"
    cp "$VALIDATOR_SRC" "$case_dir/tools/validate-framework.sh"
    chmod +x "$case_dir/tools/validate-framework.sh"
    "$setup_fn" "$case_dir"

    local output exit_code finding_count problem=""
    output="$(cd "$case_dir" && bash tools/validate-framework.sh 2>&1)"
    exit_code=$?
    finding_count="$(grep -c '^\[' <<<"$output" || true)"

    if [ "$exit_code" -ne "$expected_exit" ]; then
        problem="${problem}expected exit code $expected_exit, got $exit_code. "
    fi
    if [ "$finding_count" -ne "$expected_findings" ]; then
        problem="${problem}expected exactly $expected_findings finding(s), got $finding_count (result is ambiguous if >1, or the check never fired if 0 was expected to be 1). "
    fi
    if [ -n "$expected_finding" ] && ! grep -qF "[$expected_finding]" <<<"$output"; then
        problem="${problem}expected finding '[$expected_finding]' was not present in the output. "
    fi

    if [ -z "$problem" ]; then
        echo "[PASS] $name"
    else
        SELFTEST_FAILURES=$((SELFTEST_FAILURES + 1))
        echo "[FAIL] $name: $problem"
        echo "       --- captured validator output ---"
        sed 's/^/       /' <<<"$output"
        echo "       ----------------------------------"
    fi
}

# ---------------------------------------------------------------------------
# Check A fixtures - shared-core app branching
# ---------------------------------------------------------------------------
setup_A_valid() {
    mkdir -p "$1/src/main/java/com/framework/auth"
    cat > "$1/src/main/java/com/framework/auth/AuthProviderFixture.java" <<'EOF'
package com.framework.auth;

public class AuthProviderFixture {
    public void apply() {
        // Generic, application-agnostic logic - no appA/appB literal here.
    }
}
EOF
}

setup_A_invalid() {
    mkdir -p "$1/src/main/java/com/framework/auth"
    cat > "$1/src/main/java/com/framework/auth/AuthProviderFixture.java" <<'EOF'
package com.framework.auth;

public class AuthProviderFixture {
    public void apply(String app) {
        if (app.equals("appA")) {
            // A protected shared-core class branching on an application name -
            // exactly the violation Check A exists to catch.
        }
    }
}
EOF
}

setup_A_comment_only() {
    mkdir -p "$1/src/main/java/com/framework/auth"
    cat > "$1/src/main/java/com/framework/auth/AuthProviderFixture.java" <<'EOF'
package com.framework.auth;

public class AuthProviderFixture {
    // Historically special-cased appA here; that branch has been removed.
    public void apply() {
    }
}
EOF
}

setup_A_empty() {
    # Deliberately create nothing: no src/main/java/com/framework/<dir>/
    # files at all, and no src/test/java/com/framework/base/BaseTest.java.
    # This is the exact condition that used to crash the real script with
    # "unbound variable" on bash < 4.4 (the `files` array Check A builds
    # stays completely empty) - see the length guard in
    # check_shared_core_app_branching(). Intentionally does NOT call
    # add_check_a_placeholder; that helper exists so *other* checks' fixtures
    # don't accidentally hit this exact condition, but this case exists
    # specifically to prove the condition itself is now handled correctly.
    :
}

# ---------------------------------------------------------------------------
# Check B fixtures - required suite listeners
# ---------------------------------------------------------------------------
setup_B_valid() {
    add_check_a_placeholder "$1"
    mkdir -p "$1/src/test/resources/suites/appX"
    cat > "$1/src/test/resources/suites/appX/testng.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd">
<suite name="Fixture Suite" parallel="methods" thread-count="1" verbose="1">
    <listeners>
        <listener class-name="com.framework.listeners.TestListener"/>
        <listener class-name="com.framework.retry.RetryListener"/>
    </listeners>
    <test name="Fixture Tests">
        <classes>
        </classes>
    </test>
</suite>
EOF
}

setup_B_invalid() {
    add_check_a_placeholder "$1"
    mkdir -p "$1/src/test/resources/suites/appX"
    cat > "$1/src/test/resources/suites/appX/testng.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd">
<suite name="Fixture Suite" parallel="methods" thread-count="1" verbose="1">
    <listeners>
        <listener class-name="com.framework.listeners.TestListener"/>
    </listeners>
    <test name="Fixture Tests">
        <classes>
        </classes>
    </test>
</suite>
EOF
}

# ---------------------------------------------------------------------------
# Check C fixtures - suite <class name> must resolve to a real source file
# ---------------------------------------------------------------------------
setup_C_valid() {
    add_check_a_placeholder "$1"
    mkdir -p "$1/src/test/resources/suites/appX"
    mkdir -p "$1/src/test/java/com/framework/apps/appX/tests"
    cat > "$1/src/test/java/com/framework/apps/appX/tests/DummyTests.java" <<'EOF'
package com.framework.apps.appX.tests;

public class DummyTests {
    public void notATestMethod() {
    }
}
EOF
    cat > "$1/src/test/resources/suites/appX/testng.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd">
<suite name="Fixture Suite" parallel="methods" thread-count="1" verbose="1">
    <listeners>
        <listener class-name="com.framework.listeners.TestListener"/>
        <listener class-name="com.framework.retry.RetryListener"/>
    </listeners>
    <test name="Fixture Tests">
        <classes>
            <class name="com.framework.apps.appX.tests.DummyTests"/>
        </classes>
    </test>
</suite>
EOF
}

setup_C_invalid() {
    add_check_a_placeholder "$1"
    mkdir -p "$1/src/test/resources/suites/appX"
    cat > "$1/src/test/resources/suites/appX/testng.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd">
<suite name="Fixture Suite" parallel="methods" thread-count="1" verbose="1">
    <listeners>
        <listener class-name="com.framework.listeners.TestListener"/>
        <listener class-name="com.framework.retry.RetryListener"/>
    </listeners>
    <test name="Fixture Tests">
        <classes>
            <class name="com.framework.apps.appX.tests.NoSuchTests"/>
        </classes>
    </test>
</suite>
EOF
}

# ---------------------------------------------------------------------------
# Check D fixtures - services.<name>.<key> suffix convention
# ---------------------------------------------------------------------------
setup_D_valid() {
    add_check_a_placeholder "$1"
    mkdir -p "$1/src/test/resources/config/appX"
    cat > "$1/src/test/resources/config/appX/qa.properties" <<'EOF'
base.url=https://example.test
services.orders.base.url=https://orders.example.test
EOF
}

setup_D_invalid() {
    add_check_a_placeholder "$1"
    mkdir -p "$1/src/test/resources/config/appX"
    cat > "$1/src/test/resources/config/appX/qa.properties" <<'EOF'
base.url=https://example.test
services.orders.base-url=https://orders.example.test
EOF
}

# ---------------------------------------------------------------------------
# Check E fixtures - @Test-bearing class under apps/<app>/tests/ must be
# registered in that app's suite XML.
# ---------------------------------------------------------------------------
setup_E_valid() {
    add_check_a_placeholder "$1"
    mkdir -p "$1/src/test/java/com/framework/apps/appX/tests"
    mkdir -p "$1/src/test/resources/suites/appX"
    cat > "$1/src/test/java/com/framework/apps/appX/tests/DummyTests.java" <<'EOF'
package com.framework.apps.appX.tests;

import org.testng.annotations.Test;

public class DummyTests {

    @Test
    public void dummy() {
    }
}
EOF
    cat > "$1/src/test/resources/suites/appX/testng.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd">
<suite name="Fixture Suite" parallel="methods" thread-count="1" verbose="1">
    <listeners>
        <listener class-name="com.framework.listeners.TestListener"/>
        <listener class-name="com.framework.retry.RetryListener"/>
    </listeners>
    <test name="Fixture Tests">
        <classes>
            <class name="com.framework.apps.appX.tests.DummyTests"/>
        </classes>
    </test>
</suite>
EOF
}

setup_E_invalid() {
    # Deliberately no suite XML at all under src/test/resources/suites/ -
    # this class is registered nowhere, and there is nothing else in this
    # fixture for Checks B/C to have an opinion about.
    add_check_a_placeholder "$1"
    mkdir -p "$1/src/test/java/com/framework/apps/appX/tests"
    cat > "$1/src/test/java/com/framework/apps/appX/tests/OrphanTests.java" <<'EOF'
package com.framework.apps.appX.tests;

import org.testng.annotations.Test;

public class OrphanTests {

    @Test
    public void neverRegisteredInAnySuite() {
    }
}
EOF
}

# ---------------------------------------------------------------------------
# Run every case
# ---------------------------------------------------------------------------
run_case "check-A-valid"          setup_A_valid          0
run_case "check-A-invalid"        setup_A_invalid        1 "A:shared-core-app-branching"
run_case "check-A-comment-only"   setup_A_comment_only   0
run_case "check-A-empty"          setup_A_empty          0
run_case "check-B-valid"          setup_B_valid          0
run_case "check-B-invalid"        setup_B_invalid        1 "B:missing-required-listener"
run_case "check-C-valid"          setup_C_valid          0
run_case "check-C-invalid"        setup_C_invalid        1 "C:suite-class-missing"
run_case "check-D-valid"          setup_D_valid          0
run_case "check-D-invalid"        setup_D_invalid        1 "D:malformed-service-key"
run_case "check-E-valid"          setup_E_valid          0
run_case "check-E-invalid"        setup_E_invalid        1 "E:test-class-not-in-suite"

echo
if [ "$SELFTEST_FAILURES" -eq 0 ]; then
    echo "validate-framework.selftest.sh: PASS ($SELFTEST_CASES/$SELFTEST_CASES cases)"
    exit 0
else
    echo "validate-framework.selftest.sh: FAIL ($SELFTEST_FAILURES/$SELFTEST_CASES cases failed)"
    exit 1
fi

# ---------------------------------------------------------------------------
# Bash 3.2 empty-array issue - now fixed, regression-tested by check-A-empty
# above.
#
# check_shared_core_app_branching() builds `files=()`, then iterates
# "${files[@]}". Under `set -uo pipefail`, expanding "${array[@]}" on a
# declared-but-EMPTY array raises "unbound variable" on bash < 4.4 (fixed
# upstream in 4.4). Triggering condition: every one of the 13 protected
# src/main/java/com/framework/<dir>/ directories, plus
# src/test/java/com/framework/base/BaseTest.java, has zero files - which the
# real repository never hits (every protected directory always has at least
# one file), but which a fixture built to isolate Checks B-E deliberately
# does hit unless it calls add_check_a_placeholder. The production fix is a
# length guard (`if [ "${#files[@]}" -gt 0 ]; then ... fi`) around that loop
# in validate-framework.sh; the check-A-empty case above exercises exactly
# this condition directly, so a regression here would show up as that case
# failing (or the whole self-test crashing), not just as a comment going
# stale.
# ---------------------------------------------------------------------------
