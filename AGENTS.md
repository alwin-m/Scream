# SCREAM Agent Instructions

This repository has a documentation pack at `docs/ai-context`. Use it before scanning source.

Default workflow for future AI agents:

1. Read `docs/ai-context/README.md`.
2. Match the user request to `docs/ai-context/SKILLS.md`.
3. Read only the relevant area doc and the exact files listed in `docs/ai-context/FILE_REPORTS.md`.
4. Open source files only after the docs identify the likely owner.
5. Do a full project search only when the docs are stale, the request spans unknown areas, the bug cannot be localized, or verification output points somewhere unexpected.
6. When code changes, update the related `docs/ai-context/*.md` file in the same task.

Avoid spending tokens on generated/vendor artifacts:

- `.gradle/`
- `app/build/`
- `gradle-bin/`
- `gradle-8.7-bin.zip`
- `SCREAM-debug.apk`

Build command:

```powershell
.\gradlew.bat :app:assembleDebug
```

## Product and security guardrails

- Describe SCREAM as civilian resilience software for outages, disasters, and
  remote field conditions; do not frame it as military-grade or emergency-certified.
- Preserve the distinction between message retention (48 hours by default) and
  mesh routing TTL/hop count. Never claim that TTL alone guarantees deletion or
  delivery.
- Do not call RSSI a physical distance measurement. Use “signal estimate” or a
  qualitative proximity band.
- Treat the current shared AES-GCM mesh key as an explicit limitation. Do not
  claim Signal-style per-user E2E until audited key exchange and session tests
  exist.
- For future stories or file transfer, require audience, expiry, consent,
  authenticated encryption, deduplication, cancellation, and visible queued/
  relayed/expired states before adding transport behavior.
- Keep the MIT license intact. Security notices belong in `SECURITY.md` and
  `SAFETY.md`; they do not alter the license grant.


# Git Commit Rules
- Treat every meaningful change as a separate development task.
- Break large features into smaller milestones whenever practical.
- Create separate commits for project setup, UI changes, backend logic, database updates, API integration, bug fixes, refactoring, documentation, tests, performance improvements, and configuration changes.
- Each commit should represent one logical unit of work that could be understood or reverted independently.
- Use clear, descriptive commit messages following conventional style where appropriate (e.g., feat:, fix:, refactor:, docs:, style:, test:, chore:).
- When implementing a large feature, complete it in incremental stages and commit after each completed stage rather than waiting until everything is finished.
- Avoid mixing unrelated changes in the same commit.
- If multiple files are modified for a single feature, include them in one commit only if they belong to that same logical change.
- Before creating a commit, review the staged files and ensure they all serve the same purpose.
- Prioritize an accurate, readable, and maintainable Git history over minimizing the number of commits.
- Whenever possible, suggest the next logical commit boundary before continuing development.
