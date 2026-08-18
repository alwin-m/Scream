# SCREAM Safety Protocols


SCREAM is designed for resilient local communication during connectivity loss,
including outages, disasters, and remote field conditions. It is not a
replacement for emergency services or a guarantee that a message will arrive.

The app is civilian resilience software, not a military or emergency-grade
system. Treat every delivery indicator as local evidence from the current
device, not proof that every intended recipient received the message.

## Operator checklist

- Keep the app, Android system, and device firmware updated.
- Install APKs only from a trusted source and verify the signing information
  before sharing builds.
- Test two or more device models before an event; LAN, BLE, battery saver, and
  background limits vary by manufacturer.
- Keep a second communication method and a printed emergency plan.
- Share only the minimum sensitive information needed. Nearby radios reveal
  metadata even when message contents are encrypted.
- Disable mesh networking when it is not needed, especially in sensitive
  locations.
- Nearby discovery can expose radio metadata even when message content is
  encrypted. Use a neutral alias and public emoji when identity exposure would
  create risk.

## Automatic protections

SCREAM rejects malformed or unencrypted Android envelopes, bounds message size
and age, rate-limits noisy peers, quarantines repeated violations, and avoids
counting the same Bluetooth address multiple times when it advertises multiple
services. These controls reduce risk; they do not prove that a peer is safe.

## QR and file transfer

The current QR feature shares public identity bootstrap data only. High-rate
QR projects can later be useful for offline file transport, but a QR frame is
only an encoding. SCREAM must encrypt, authenticate, size-check, and
user-confirm every imported file before saving or playing it. QR file transfer
is therefore still a planned transport feature, not an automatic trust path.
