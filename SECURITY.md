# Security Policy

## Supported Versions

InternetStorer follows an offline-first, cryptographic-first architecture. Security updates and patches are applied to the active development branch.

| Version | Supported          |
| ------- | ------------------ |
| 3.0.x   | :white_check_mark: |
| < 3.0   | :x:                |

## Security Model & Threat Boundaries

InternetStorer 3.0 enforces the following security boundaries:

1. **Hardware-Backed Cryptography:**
   - Cryptographic keys are generated inside the Android Keystore system (`AndroidKeyStore` provider).
   - Sensitive payloads are encrypted with AES-256-GCM (Galois/Counter Mode) utilizing 128-bit authentication tags and unique Initialization Vectors (IVs).
   - In environments without Secure Element / StrongBox support, AES-256-GCM keys are software-backed with strict app-private file permissions (`Context.MODE_PRIVATE`).

2. **Storage Integrity & Path Traversal Resistance:**
   - Relative path resolution strictly enforces that files cannot traverse outside the designated `vault_root` directory.
   - Filenames containing path traversal sequences (`..`), null bytes (`\0`), newlines (`\n`, `\r`), or forbidden control characters are rejected immediately.
   - Chunk reconstruction requires pre-validation of chunk boundaries (`offset >= 0`, `length >= 0`, `offset + length <= total_size`) and per-chunk SHA-256 digest verification prior to writing.
   - Final whole-file SHA-256 cryptographic verification is mandatory before atomic promotion from staging to the vault.

3. **Offline Operation & Network Isolation:**
   - Phase 03 contains **zero external network transmission logic**. All cryptographic hashing, chunking, deduplication, and verification are executed 100% locally on device.
   - No mock networking, telemetry, or third-party cloud synchronization exists.

## Reporting a Vulnerability

If you discover a security vulnerability within InternetStorer, please report it responsibly:

1. **Do not create public GitHub issues** for security vulnerabilities.
2. Email the maintainer directly with details of the vulnerability, reproduction steps, and potential impact.
3. You will receive an acknowledgment within 48 hours.
4. Maintainers will provide an estimated timeline for remediation and coordinate disclosure after a patch is released.
