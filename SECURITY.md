# Security policy

## Supported versions

Security fixes are provided for the latest published TwiBoxCore release.

## Reporting a vulnerability

Do not publish exploitable details in a public issue. Use GitHub's private vulnerability reporting feature for this repository. Include the affected version, server implementation, reproduction steps, impact, and any proposed mitigation.

## Security properties

- Administrative permissions default to operators.
- TwiBoxCore opens no sockets and makes no outbound network requests.
- It includes no telemetry, updater, webhook, remote console, or licensing mechanism.
- Item normalization is restricted to explicitly recognized PDC data or the canonical glacier-pickaxe signature.
- Invalid configured block materials are ignored rather than broadened into an unsafe match.
- Legacy config migration is restricted to one fixed sibling path and seven allowlisted keys; it performs no directory discovery.
- Migration leaves the source untouched, creates a content-hash backup, writes through same-directory temporary files, and records completion to prevent repeat imports.

Always verify release hashes and test migrations on a non-production copy first.
