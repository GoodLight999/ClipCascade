# Experiment Log

This document is append-only except for correcting factual errors. Every meaningful attempt must record its hypothesis, exact change or command, evidence, result, and next decision.

## Entry template

```markdown
## YYYY-MM-DD — Short title

- Baseline commit:
- Environment/device:
- Hypothesis:
- Change or command:
- Expected result:
- Observed result:
- Evidence/artifacts:
- Decision:
- Follow-up:
```

## 2026-07-26 — Verify clean upstream baseline

- Baseline commit: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Environment/device: GitHub repository metadata and commit comparison
- Hypothesis: The current `main` branch may contain the broken patchwork implementation and require a destructive reset.
- Change or command: Compared `GoodLight999/Trial-and-Error-ClipCascade:main` against upstream SHA `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`.
- Expected result: Detect divergence and force-reset `main` if necessary.
- Observed result: The branches were identical: ahead 0, behind 0, changed files 0.
- Evidence/artifacts: GitHub compare result; upstream and fork both resolve the verified latest commit to `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`.
- Decision: Do not perform a meaningless force update. Treat `main` as the pristine upstream mirror.
- Follow-up: Isolate all new work on `clean-rebuild`.

## 2026-07-26 — Archive patchwork development line

- Baseline commit: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Environment/device: GitHub pull request state
- Hypothesis: Leaving the old draft PR open would make its 529-commit patchwork branch appear canonical and invite accidental reuse.
- Change or command: Closed draft PR #1 and replaced its description with an archival notice and the verified clean baseline.
- Expected result: The previous implementation remains recoverable in Git history but is no longer presented as active work.
- Observed result: PR #1 is closed and unmerged.
- Evidence/artifacts: PR #1 state and updated description.
- Decision: Do not inspect or reuse the archived branch unless a future experiment names a specific isolated fact to verify.
- Follow-up: Start from a fresh branch created directly from upstream.

## 2026-07-26 — Create clean development branch and handoff discipline

- Baseline commit: `fd2cbbce69d5e5fa6b9b758d13a7dc6efdcb8a39`
- Environment/device: GitHub branch and documentation
- Hypothesis: A pristine branch plus mandatory handoff and experiment records will prevent another untraceable patchwork collapse.
- Change or command: Created `clean-rebuild` from the exact upstream SHA and added `docs/CLEAN_REBUILD_HANDOFF.md` and this log.
- Expected result: Every subsequent thread can identify the authoritative baseline, constraints, current state, and next actions without reading obsolete implementation branches.
- Observed result: Branch and documentation created successfully.
- Evidence/artifacts: `clean-rebuild`; commit `01e811f737a5e06a838cca1eb0d704c046da8715` for the initial handoff document.
- Decision: No product-code change may precede baseline build reproduction and architecture inventory.
- Follow-up: Reproduce upstream builds and record exact commands, versions, and artifacts.