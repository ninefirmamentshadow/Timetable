#!/bin/sh
# Verify tool-specific agent files stay thin pointers to AGENTS.md.
# Run locally: sh scripts/check-agent-pointers.sh   (no dependencies)
set -u
cd "$(dirname "$0")/.." || exit 2
fail=0
check() { # file max_lines required_text
    f=$1; max=$2; need=$3
    if [ ! -f "$f" ]; then echo "FAIL missing: $f"; fail=1; return; fi
    n=$(wc -l < "$f")
    if [ "$n" -gt "$max" ]; then echo "FAIL $f has $n lines (> $max): rules belong in AGENTS.md"; fail=1; fi
    if ! grep -q "$need" "$f"; then echo "FAIL $f does not reference $need"; fail=1; fi
}
check AGENTS.md 400 "Owner principles"
check CLAUDE.md 20 "@AGENTS.md"
check GEMINI.md 10 "@AGENTS.md"
check .github/copilot-instructions.md 10 "AGENTS.md"
check .cursor/rules/agents.mdc 15 "AGENTS.md"
check docs/agents/HANDOFF_TEMPLATE.md 60 "Status labels"
[ -e .cursorrules ] && { echo "FAIL legacy .cursorrules present; use .cursor/rules"; fail=1; }
[ "$fail" -eq 0 ] && echo "PASS agent pointer files in sync"
exit $fail
