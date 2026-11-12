#!/usr/bin/env python3
"""
Artify secret / signing-material guard.

Fails (exit 1) if the repository contains anything that looks like release
signing material or credentials. Runs in CI on every push and pull request,
and can be installed as a local pre-commit hook:

    python3 security/check_secrets.py --install-hook

Checks:
  1. Forbidden file types anywhere in the tree (keystores, private keys, ...).
  2. Binary keystore signatures (JKS / JCEKS / PKCS#12) regardless of extension.
  3. Private-key PEM blocks and common token formats inside text files.
  4. Hard-coded signing passwords in Gradle files.
"""
import os
import re
import stat
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SKIP_DIRS = {".git", ".gradle", ".kotlin", "build", ".idea", ".cxx", "node_modules"}

FORBIDDEN_EXT = {
    ".jks", ".keystore", ".p12", ".pfx", ".pem", ".key", ".der", ".p8",
    ".mobileprovision", ".apk", ".aab",
}
FORBIDDEN_NAMES = {
    "keystore.properties", "signing.properties", "local.properties",
    "google-services.json", ".env",
}

TEXT_PATTERNS = [
    (re.compile(r"-----BEGIN (RSA |EC |DSA |OPENSSH |ENCRYPTED )?PRIVATE KEY-----"), "private key block"),
    (re.compile(r"AKIA[0-9A-Z]{16}"), "AWS access key id"),
    (re.compile(r"gh[pousr]_[A-Za-z0-9]{36,}"), "GitHub token"),
    (re.compile(r"AIza[0-9A-Za-z_\-]{35}"), "Google API key"),
    (re.compile(r"xox[abpr]-[A-Za-z0-9-]{10,}"), "Slack token"),
    (re.compile(r"sk-[A-Za-z0-9]{32,}"), "API secret key"),
]
GRADLE_PASSWORD = re.compile(
    r"""(storePassword|keyPassword)\s*[=(]\s*["'][^"']+["']""")

# Magic numbers of keystore containers.
JKS_MAGIC = b"\xfe\xed\xfe\xed"
JCEKS_MAGIC = b"\xce\xce\xce\xce"


def tracked_or_all_files():
    """Files git would commit (tracked + untracked, not ignored); falls back to a walk."""
    try:
        out = subprocess.run(
            ["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"],
            cwd=ROOT, capture_output=True, check=True,
        ).stdout
        return [os.path.join(ROOT, p) for p in out.decode().split("\0") if p]
    except Exception:
        files = []
        for d, dirs, names in os.walk(ROOT):
            dirs[:] = [x for x in dirs if x not in SKIP_DIRS]
            files += [os.path.join(d, n) for n in names]
        return files


def looks_like_pkcs12(head: bytes) -> bool:
    # DER SEQUENCE followed by INTEGER version 3 — the PKCS#12 PFX header.
    return len(head) > 8 and head[0] == 0x30 and b"\x02\x01\x03" in head[:8]


def check():
    problems = []
    for path in tracked_or_all_files():
        if not os.path.isfile(path):
            continue
        rel = os.path.relpath(path, ROOT).replace(os.sep, "/")
        name = os.path.basename(path)
        ext = os.path.splitext(name)[1].lower()
        if ext in FORBIDDEN_EXT or name in FORBIDDEN_NAMES or (name.startswith(".env") and name != ".env.example"):
            problems.append(f"{rel}: forbidden file type (signing material / credentials / build output)")
            continue
        try:
            with open(path, "rb") as f:
                head = f.read(16)
                f.seek(0)
                data = f.read(2 * 1024 * 1024)
        except OSError:
            continue
        if head.startswith(JKS_MAGIC) or head.startswith(JCEKS_MAGIC):
            problems.append(f"{rel}: Java keystore detected")
            continue
        if ext not in {".png", ".jpg", ".webp", ".jar", ".gif"} and looks_like_pkcs12(head):
            problems.append(f"{rel}: possible PKCS#12 keystore")
            continue
        if b"\0" in data[:4096]:
            continue  # other binary files
        text = data.decode("utf-8", "ignore")
        if rel == "security/check_secrets.py":
            continue
        for pat, label in TEXT_PATTERNS:
            if pat.search(text):
                problems.append(f"{rel}: contains {label}")
        if name.endswith((".gradle", ".gradle.kts", ".properties")) and GRADLE_PASSWORD.search(text):
            problems.append(f"{rel}: hard-coded signing password")
    return problems


def install_hook():
    hook = os.path.join(ROOT, ".git", "hooks", "pre-commit")
    with open(hook, "w", newline="\n") as f:
        f.write("#!/bin/sh\npython3 security/check_secrets.py || python security/check_secrets.py\n")
    os.chmod(hook, os.stat(hook).st_mode | stat.S_IEXEC)
    print(f"Installed pre-commit hook at {hook}")


if __name__ == "__main__":
    if "--install-hook" in sys.argv:
        install_hook()
        sys.exit(0)
    found = check()
    if found:
        print("SECURITY CHECK FAILED — refusing to continue:\n")
        for p in found:
            print("  - " + p)
        print("\nSigning keys and credentials must never be committed. See SECURITY.md.")
        sys.exit(1)
    print("Security check passed: no signing material or credentials found.")
