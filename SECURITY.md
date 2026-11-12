# Security

Artify is used by real people to store their artwork, and this repository is
public. These are the rules the project follows.

## Signing keys and secrets are never in this repository

- Release keystores (`.jks`, `.keystore`, `.p12`, …), `keystore.properties`,
  `local.properties`, `.env` files and API keys are blocked by `.gitignore`.
- `security/check_secrets.py` scans every file that would be committed for
  keystores (by extension **and** by binary signature), private-key blocks,
  common token formats and hard-coded signing passwords. CI runs it on every
  push and pull request and fails the build if anything is found.
- Install it as a local pre-commit hook so a mistake is caught before it ever
  leaves your machine:

  ```bash
  python3 security/check_secrets.py --install-hook
  ```

- `app/build.gradle.kts` reads signing values only from environment variables
  or `~/.gradle/gradle.properties`, and **refuses to build** if the keystore
  path points inside the project directory.
- The release workflow restores the keystore from GitHub encrypted secrets into
  the runner's temp folder (outside the checkout) and deletes it afterwards.

See [docs/RELEASING.md](docs/RELEASING.md) for the full signing setup.

**If a key is ever exposed:** treat it as compromised. Rotating history does not
help once something has been public. Enrol in Play App Signing (Google holds the
app signing key) and request an upload-key reset, revoke any leaked tokens, then
remove the file from history.

## App hardening

- **No permissions at all** — not even `INTERNET`. Artwork never leaves the
  device unless the user explicitly shares or exports it.
- Gallery saves use MediaStore; imports use the system file picker. No storage
  permissions are requested.
- The only exported component is the launcher activity. The `FileProvider` is
  not exported, grants read-only per-share access, and exposes only
  `cache/exports/`.
- Imported `.artify` files are treated as hostile input: magic and version
  checks, hard limits on every count, a 256 MB raw / 512 MB decompressed cap
  (zip-bomb protection), finite-number validation and range clamping. Malformed
  files are rejected and covered by unit tests.
- Document ids come from the app, never from file contents, and must be UUIDs,
  so a crafted file cannot cause path traversal.
- Saves are atomic (write temp file → fsync → rename), so a crash cannot corrupt
  an artwork.
- Release builds are minified with R8 and are not debuggable.
- GitHub Actions are pinned to full commit SHAs and run with read-only tokens.

## Reporting a vulnerability

Please do not open a public issue. Use GitHub's **Report a vulnerability**
(private security advisory) on this repository.
