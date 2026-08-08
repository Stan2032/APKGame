#!/usr/bin/env python3
"""Audits the built release APK against promises we make to users.

Run after `./gradlew :app:assembleRelease`. Checks, in order of how badly a
regression would hurt:

1. PERMISSIONS match an explicit allowlist. The privacy story is a claim about
   this exact list; a transitive dependency can add to it silently (that is
   exactly how android.permission.INTERNET arrived, via Play Billing's
   datatransport telemetry library). Any change must be a conscious decision
   with the docs updated in the same commit.
2. R8 kept every kotlinx-serialization serializer. If minification strips one,
   saves fail to load at runtime and players lose progress — a failure mode
   unit tests cannot see because they run unminified.
3. APK stays under a size budget (download size affects install conversion).

Usage: python3 tools/verify_release_apk.py [path/to/app-release.apk]
Exit code 0 = all good; 1 = a promise is broken.
"""
import os
import re
import subprocess
import sys

# Exactly the permissions we accept in a shipped build. Adding one here is a
# deliberate act: update docs/PRIVACY_POLICY.md, the Settings privacy text,
# the Play Data Safety form, and docs/DECISIONS.md D-3 in the same change.
ALLOWED_PERMISSIONS = {
    # Play Billing, for in-app purchases.
    "com.android.vending.BILLING",
    # Added by AndroidX; a locally-scoped receiver guard, not a real capability.
    "io.github.stan2032.gravitygambit.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
    # Both arrive via com.google.android.datatransport (a transitive dependency
    # of Play Billing). Our own code performs no networking; see
    # docs/DECISIONS.md D-3 for the full story and why we did not strip them.
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.INTERNET",
}

REQUIRED_CLASSES = [
    "Lio/github/stan2032/gravitygambit/core/Profile$$serializer;",
    "Lio/github/stan2032/gravitygambit/core/SystemSpec$$serializer;",
    "Lio/github/stan2032/gravitygambit/core/Body$$serializer;",
    "Lio/github/stan2032/gravitygambit/core/Station$$serializer;",
    "Lio/github/stan2032/gravitygambit/core/TurnInput$$serializer;",
]

MAX_APK_BYTES = 6 * 1024 * 1024  # generous headroom over the current ~1.7MB


def build_tool(name):
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or "/opt/android-sdk"
    candidates = sorted(
        (os.path.join(sdk, "build-tools", v, name)
         for v in os.listdir(os.path.join(sdk, "build-tools"))),
        reverse=True,
    )
    for c in candidates:
        if os.path.exists(c):
            return c
    sys.exit(f"FAIL: could not find {name} in {sdk}/build-tools")


DEBUG_SIGNER_MARKERS = ("CN=Android Debug", "O=Android")


def signer_of(apk):
    """
    The certificate subject the APK is signed with, or None if unreadable.

    Worth checking because the one launch mistake that costs real time is
    uploading a **debug-signed** artifact: `app/build.gradle.kts` deliberately
    falls back to the debug key so release builds stay installable for testing
    (docs/RELEASE.md §Signing), which means an unsigned-for-upload AAB looks
    exactly like a signed one until Play refuses it.
    """
    try:
        out = subprocess.run(
            [build_tool("apksigner"), "verify", "--print-certs", apk],
            capture_output=True, text=True,
        ).stdout
    except SystemExit:
        return None
    m = re.search(r"Signer #1 certificate DN:\s*(.+)", out)
    return m.group(1).strip() if m else None


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    require_upload_key = "--require-upload-key" in sys.argv
    apk = args[0] if args else "app/build/outputs/apk/release/app-release.apk"
    if not os.path.exists(apk):
        sys.exit(f"FAIL: {apk} not found — run ./gradlew :app:assembleRelease first")

    failures = []

    badging = subprocess.run(
        [build_tool("aapt2"), "dump", "badging", apk],
        capture_output=True, text=True,
    ).stdout
    found = set(re.findall(r"uses-permission: name='([^']+)'", badging))

    unexpected = found - ALLOWED_PERMISSIONS
    if unexpected:
        failures.append(
            "Unexpected permission(s) in the release APK: "
            + ", ".join(sorted(unexpected))
            + "\n  A dependency added a capability we do not document. Either remove it or"
            "\n  accept it consciously: update ALLOWED_PERMISSIONS here, PRIVACY_POLICY.md,"
            "\n  the Settings privacy text, the Play Data Safety form, and DECISIONS.md D-3."
        )
    missing_perms = ALLOWED_PERMISSIONS - found
    if missing_perms:
        print(f"note: allowlisted permissions absent from this build: {sorted(missing_perms)}")

    dex = subprocess.run(
        [build_tool("dexdump"), "-f", apk], capture_output=True, text=True,
    ).stdout
    for cls in REQUIRED_CLASSES:
        if cls not in dex:
            failures.append(
                f"R8 stripped {cls} — saves would fail to load at runtime. "
                "Check app/proguard-rules.pro."
            )

    size = os.path.getsize(apk)
    if size > MAX_APK_BYTES:
        failures.append(f"APK is {size/1024/1024:.1f}MB, over the {MAX_APK_BYTES/1024/1024:.0f}MB budget")

    print(f"permissions: {len(found)} (all allowlisted)" if not unexpected else "permissions: MISMATCH")
    print(f"serializers kept: {len(REQUIRED_CLASSES) - sum(1 for c in REQUIRED_CLASSES if c not in dex)}/{len(REQUIRED_CLASSES)}")
    print(f"apk size: {size/1024/1024:.2f}MB (budget {MAX_APK_BYTES/1024/1024:.0f}MB)")

    signer = signer_of(apk)
    debug_signed = signer is not None and any(m in signer for m in DEBUG_SIGNER_MARKERS)
    if signer is None:
        print("signed by: UNKNOWN (apksigner unavailable — signing not verified)")
    elif debug_signed:
        print(f"signed by: DEBUG KEY — {signer}  (installable for testing, rejected by Play)")
    else:
        print(f"signed by: {signer}")

    # Only an upload build is required to carry the upload key. Ordinary CI
    # has no keystore and must stay green, so this is opt-in rather than
    # always-on — but when it is asked for, it is the difference between
    # finding out here and finding out from a Play Console rejection.
    if require_upload_key and (signer is None or debug_signed):
        failures.append(
            "This build is not signed with the upload key, but --require-upload-key was set.\n"
            "  Set GG_KEYSTORE_FILE / GG_KEYSTORE_PASSWORD / GG_KEY_ALIAS / GG_KEY_PASSWORD\n"
            "  (docs/RELEASE.md §Signing) and rebuild. Do NOT upload this artifact."
        )

    if failures:
        print("\n".join("FAIL: " + f for f in failures))
        sys.exit(1)
    print("release APK verified")


if __name__ == "__main__":
    main()
