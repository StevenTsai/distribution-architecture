# Security Policy

[中文](../SECURITY.md) | English

## Supported Versions

This repository contains documentation and sample code, so security updates are provided only for the latest version (the `main` branch).

| Version | Status |
|---------|--------|
| main | ✅ Latest |
| feature branches | ⚠️ In development |

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub Issues.**

Report security issues through one of the following channels:

1. **GitHub Security Advisories** (recommended):
   https://github.com/StevenTsai/distribution-architecture/security/advisories/new

2. **Email**: use the contact details in the GitHub profile

## Our Commitment

- We will acknowledge your report within **48 hours**
- We will provide an initial assessment within **7 days**
- We will **credit you publicly** after the fix (unless you ask to remain anonymous)

## Security-Related Documentation

The following documents in this repository cover security topics:

- [PII Encryption and Masking](architecture/pii-encryption.md) — personal data protection
- [Admin RBAC](architecture/admin-rbac.md) — access control
- [Data Permission Model](architecture/data-permission-model.md) — row-level data isolation
- [Audit Log and Compliance](architecture/audit-and-compliance.md) — operation traceability

## Disclaimer

The code examples in this repository are for learning purposes only. Perform a thorough security assessment and testing before using them in production.
