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

