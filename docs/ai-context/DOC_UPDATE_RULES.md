# Documentation Update Rules

## Goal

Keep this folder useful enough that future AI agents can route tasks without a full project scan.

## Required Update Cases

Update docs when any change affects:

- Screen ownership or navigation routes.
- Public feature behavior.
- Repository functions or model fields.
- Persistence keys, JSON shape, expiry rules, or backup behavior.
- Network message types, envelope fields, encryption, TTL, dedupe, ports, or protocol compatibility.
- BLE UUIDs, permissions, scanning/advertising, GATT chunking, or service lifecycle.
- Build commands, dependencies, SDK versions, Gradle versions.
- Web API endpoints, SSE events, server ports, or web/Android protocol compatibility.

## Which File To Update

- New feature behavior: `FUNCTIONS.md`
- UI/UX changes: `UI_UX.md`
- Storage/model changes: `DATA_STORAGE.md`
- LAN/protocol changes: `NETWORK.md`
- Bluetooth/BLE changes: `BLUETOOTH.md`
- Web bridge changes: `WEB.md`
- Build/dependency/test changes: `BUILD_TESTING.md`
- File ownership changes: `FILE_REPORTS.md`
- Agent routing changes: `SKILLS.md` and `AGENTS.md`

## Style

- Keep docs concise and direct.
- Prefer "read this file when..." guidance over broad explanation.
- Mention caveats clearly, especially demo/simulated behavior.
- Keep generated/vendor artifacts listed as "do not read by default".
- Use relative paths from repository root inside docs.

## Agent Rule

Before finalizing a code change, ask:

```text
Would a future agent know where to make this change from docs/ai-context?
```

If the answer is no, update the docs in the same task.

