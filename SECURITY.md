# SCREAM Security Model

SCREAM is an offline-first communication project. Its security goal is to
reduce exposure when there is no trusted server, not to promise invulnerability
or anonymity against a compromised phone, operating system, radio firmware, or
modified APK.

SCREAM is not certified military, emergency-services, or high-assurance
communications software. In the current release, “encrypted” means that a
native SCREAM envelope passed AES-GCM admission checks; it does not yet mean a
unique, mutually authenticated end-to-end session between room participants.

## Protections enabled today

- Android SCREAM envelopes require AES-256-GCM ciphertext, a valid IV, a known
  message type, a valid timestamp, and a bounded TTL.
- Invalid, oversized, replay-like, or abusive traffic is rejected before it
  reaches the repository. Repeated violations temporarily quarantine the
  originating route, allowing healthy mesh routes to continue.
- BLE scan callbacks are deduplicated by physical Bluetooth address. Once a
  signed SCREAM identity is received, temporary BLE placeholders are merged so
  one device is not counted as several peers.
- APK signing fingerprints are exchanged as a warning/control signal. A
  mismatched signing certificate is treated as suspicious and routed away from
  automatically. The release certificate must be pinned before calling a build
  official; a placeholder is never trusted.
- BitChat private key material is wrapped with an Android Keystore AES-GCM key
  before it is stored in DataStore. Legacy raw values are migrated when read.
- OTA manifests reject missing or unverified signing information.
- Native content and routes are bounded by size, timestamp, TTL, replay-cache,
  and rate limits. Local content cleanup is set to 48 hours by default. TTL
  limits mesh hops; it is not the same as the retention period.

## Important limitations

The current mesh payload key is still a shared application key. It protects
payloads from casual network observers and tampering, but it is not equivalent
to modern per-peer end-to-end encryption. A compromised or modified official
device can still read messages it legitimately receives. Local SQLite/media
storage and Android system metadata also require additional hardening.

Do not use SCREAM as the sole channel for emergency dispatch, medical care,
military operations, evacuation coordination, or protection from a capable
state-level adversary. Test the exact phones, Android versions, radios, and
power conditions before relying on it during an outage.

The supported Android baseline is API 26 (Android 8.0). BLE advertising,
background execution, notifications, Wi-Fi discovery, and power behavior vary
by manufacturer and must be tested on the target devices.

## Planned security work

1. Replace the shared transport key with an audited authenticated key exchange
   and per-peer/per-room session keys.
2. Add authenticated sender signatures and explicit key fingerprints with a
   human-verifiable trust flow.
3. Encrypt the local database and media blobs at rest using Keystore-backed
   keys, with migration and recovery tests.
4. Add resumable encrypted file transfer. The current QR feature is limited to
   public identity bootstrap; QR itself is not encryption. File payloads must
   be authenticated before import or playback.
5. Add security tests, fuzzing, two-device integration tests, and an external
   review before production claims.

## Reporting

Please report suspected vulnerabilities privately to the project maintainers
before publishing reproduction details. Include the affected version, device
and Android version, transport, logs with personal content removed, and a
minimal reproduction.
