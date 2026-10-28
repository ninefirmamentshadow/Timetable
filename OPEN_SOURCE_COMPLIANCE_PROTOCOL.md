# SOVEREIGN OPEN-SOURCE COMPLIANCE PROTOCOL

Standing Directive
Mode: Research, Reuse, Implement, Verify, Document

## Mission

You are authorized to examine public repositories, open-source projects, libraries, frameworks, and existing implementations to improve my software.

Reuse proven components wherever doing so saves development time, improves reliability, or reduces maintenance.

However, all reuse must be lawful, ethically defensible, secure, and compatible with the licensing and distribution requirements of my projects.

Optimize aggressively. Do not plagiarize, misappropriate intellectual property, violate licenses, or introduce unnecessary legal exposure.

This directive supplements my Sovereign Execution Protocol. Continue working autonomously within its established authorization boundaries.

## 1. License Verification

Before incorporating third-party code:

1. Identify its source repository and upstream maintainer.
2. Inspect the actual license and its scope.
3. Confirm the license covers the particular code, assets, or components being reused.
4. Check whether the license is compatible with my project's existing licenses and intended distribution.
5. Identify required attribution, copyright notices, license inclusion, and source-disclosure obligations.

Do not assume publicly accessible code is open-source.

Do not assume a GitHub repository without a license grants permission to copy, modify, or redistribute its contents.

Treat unclear or conflicting licensing as a reason to select a verified alternative, not as permission to proceed.

## 2. Prefer Permissively Licensed Components

Where technically appropriate, favor well-maintained components under licenses such as:

- MIT
- Apache License 2.0
- BSD 2-Clause or 3-Clause
- ISC

These licenses generally facilitate reuse, but comply with their specific conditions.

Other licenses are not automatically prohibited.

Evaluate LGPL, MPL, GPL, AGPL, and similar licenses individually for compatibility with the intended application architecture and distribution model.

Do not introduce copyleft obligations into proprietary or differently licensed projects without evaluating the consequences and obtaining my approval where material.

Account for dynamic linking, static linking, bundled source, network deployment, and distribution where relevant.

## 3. Distinguish Inspiration From Code Reuse

You may study public software to understand:

- Architecture
- Algorithms
- User interface conventions
- Performance techniques
- Data structures
- Security patterns
- Feature implementation strategies

General ideas, functional behavior, and engineering concepts can often inform independent implementations.

However, copying implementation details, source files, distinctive expressive elements, artwork, branding, or documentation may carry copyright or other intellectual-property obligations.

When independently implementing a feature, write original code rather than disguising copied code through renaming, formatting, or superficial restructuring.

Do not present third-party work as exclusively our own.

## 4. Attribution and Provenance

Maintain a machine-readable third-party component inventory where practical.

For incorporated code or dependencies, record:

- Component name and upstream URL
- Version or commit identifier
- License and source of license verification
- Files or modules affected
- Required notices and attribution
- Modification history where required
- Relevant distribution obligations

Preserve existing copyright notices.

Generate or maintain a "THIRD_PARTY_NOTICES.md" file where appropriate.

Do not remove required notices merely to simplify a repository.

An attribution file does not replace other license requirements, including source availability or corresponding-source obligations.

## 5. Security and Supply-Chain Hygiene

Assess third-party code before integration.

Check for known vulnerabilities, suspicious installation scripts, unexpected network access, credential harvesting, excessive permissions, and unmaintained dependencies.

Prefer official releases, verified upstream repositories, and pinned or otherwise reproducible dependencies.

Do not execute untrusted installation scripts without inspecting their behavior.

Never import embedded credentials, API keys, personal information, private configuration, or other sensitive content from upstream projects.

Run appropriate dependency and license checks where supported.

## 6. Protect My Existing Projects

Preserve established functionality, public interfaces, user data, and project direction.

Do not replace working implementations simply because a different open-source project looks cleaner.

Favor incremental improvements with clear technical benefits.

Avoid introducing a large framework to solve a small problem.

When evaluating third-party components, consider maintenance burden, binary size, performance, platform compatibility, update frequency, and licensing obligations.

Do not merge unrelated identities, accounts, or private information across projects.

## 7. Android and Public Distribution

For applications intended for APK distribution:

Verify that third-party libraries, fonts, icons, graphics, and other assets permit the intended redistribution.

Distinguish software licenses from licenses covering artwork, fonts, trademarks, and branding.

Do not copy another application's name, logo, distinctive branding, or proprietary visual assets without appropriate rights.

Avoid misleading claims of endorsement or affiliation with upstream projects.

Ensure applicable license notices and source-distribution obligations are satisfied for released builds.

Do not assume that modifying or repackaging an APK grants redistribution rights.

## 8. Legal and Ethical Boundaries

Do not:

- Copy unlicensed source code into my repositories.
- Bypass paywalls, license enforcement, or access controls to obtain proprietary source.
- Strip copyright notices or required attribution.
- Repackage paid applications without authorization.
- Import leaked, stolen, or improperly disclosed source code.
- Circumvent license restrictions through superficial rewrites.
- Misrepresent third-party work as original.
- Distribute third-party assets without appropriate rights.

Respect repository-specific contribution policies, privacy restrictions, and terms applicable to the access methods used.

When legal obligations are uncertain, explain the specific uncertainty and identify a compliant alternative.

Do not make unsupported guarantees of legal compliance.

## 9. Compliance Without Workflow Paralysis

Do not turn ordinary license diligence into repeated approval requests.

Within existing authorization:

- Use clearly compatible licensed components automatically.
- Preserve required notices.
- Record provenance as part of the implementation.
- Run relevant tests.
- Continue with unrelated work if one dependency is unsuitable.
- Replace problematic components with compatible alternatives.

Escalate only when a material licensing conflict, mandatory source-disclosure requirement, redistribution restriction, or unresolved legal exposure genuinely affects the project.

Do not ask me to approve routine use of clearly compatible open-source libraries.

## 10. Verification and Reporting

For each substantial integration, report:

1. What was adopted or independently implemented.
2. Which upstream projects informed the work.
3. Which licenses were verified.
4. Whether attribution or source-disclosure requirements apply.
5. What tests passed.
6. Whether the integration introduced any material legal, security, or maintenance concerns.

Keep reports concise and evidence-based.

Separate confirmed license facts from interpretation.

If a component's license cannot be established, do not incorporate that component. Find another implementation or build the feature independently.

## 11. Governing Principle

Reuse knowledge freely where lawful. Reuse code according to its license. Preserve attribution. Protect intellectual property. Ship working software.

Apply this policy across my repositories while preserving repository-specific licensing requirements and existing project invariants.

---

## Application in this repository

This section maps the directive onto rules this repository already has. It adds no new rules.

- The owner's current explicit instruction wins, as `AGENTS.md` already says. This protocol supplements `SOVEREIGN_EXECUTION_PROTOCOL.md`; both load for every agent here.
- The security posture and the protected data boundary in `AGENTS.md` are the §5 and §6 limits made concrete. A component may not add a permission, a network path, a telemetry or crash-reporting hook, or cloud backup. Permissions are an owner decision recorded in `AGENTS.md`; a library's manifest merge counts, so check the merged manifest after adding a dependency.
- The inventory is `third_party.json` (machine-readable) plus `THIRD_PARTY_NOTICES.md` (human-readable, shipped in the app). Every dependency in `gradle/libs.versions.toml` and every copied snippet has a row: name, upstream URL, version or commit, SPDX id, the URL its license was read from, files affected, notices required, whether modified, and scope (`runtime`, `test` or `build`). `scripts/check_third_party.py` runs inside `scripts/verify.sh` and fails when the two disagree.
- Copy code only from MIT, Apache-2.0, BSD-2/3 or ISC sources, after reading the upstream LICENSE at a pinned tag or commit, and keep the upstream copyright header on any copied file. GPL, AGPL, LGPL, MPL and unlicensed projects are ideas only: independent code, credited as "informed by" in `THIRD_PARTY_NOTICES.md`.
- If a license cannot be established, the component is not used.
- Release builds ship the notices: the APK carries `THIRD_PARTY_NOTICES.md` as an asset and shows it in the app. A debug build is never published as a release.
- This repository has no license file of its own; that means all rights reserved. Nothing here relicenses an upstream component.

## Agent workflow

- Run `scripts/verify.sh` before every push and report `NOT_RUN` honestly. A step that did not run is never a pass.
- Every confirmed, reproducible defect gets a regression test or check when practical.
- One agent owns an active branch. Others read or review only, using separate clones or worktrees.
- Update the branch handoff file (`.agent/handoffs/`) at every stop. Only `scripts/verify.sh` writes `verified`.
- Check the component inventory before building something that already exists.
- Spend reasoning by complexity: a reasoning model for planning and debugging, a coding model for implementation, scripts for anything deterministic. Final acceptance is automated evidence plus a test on the owner's device.
