# Security and OPSEC

## Reporting a vulnerability

Use GitHub's private vulnerability reporting on this repository (Security tab → "Report a vulnerability"). Do not open a public issue for security problems.

## Rules for contributors and coding agents

This repository is **public**. Nothing below may appear in code, fixtures, docs, issues, PRs, commit messages, branch names, release notes or build artifacts:

- legal identity or any real personal name not already chosen for public use;
- residence, city of residence, or real-time location;
- real contact handles, phone numbers, or email addresses;
- client information;
- medical, recovery, or benefits information;
- real financial amounts or rates.

Never search for or reconstruct anyone's legal identity. Fixtures are synthetic only (e.g. `MERIDIAN`, `Ashford`, `919-555-0142`, `example.invalid`). Example money values are neutral round numbers. Identity layers stay distinct; never cross-link a public surface to another layer.

Commits use the GitHub noreply address (`Settings → Emails → Keep my email address private`).

## Secrets

Never commit or print secrets, tokens, keystores, or signing passwords. CI reads signing material from repository secrets. If a secret is ever committed, treat it as compromised: rotate it first, then clean history.

## Local guard

```sh
cp scripts/pre-commit .git/hooks/pre-commit && chmod +x .git/hooks/pre-commit
```

It runs `gitleaks` on staged changes, an optional untracked `.opsec-patterns.local` private-pattern file (reports file:line only), and the agent pointer check. Repository-side, GitHub secret scanning and push protection are enabled, and the default branch blocks force-pushes and deletion and requires a PR.
