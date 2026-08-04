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


def main():
    apk = sys.argv[1] if len(sys.argv) > 1 else "app/build/outputs/apk/release/app-release.apk"
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

    if failures:
        print("\n".join("FAIL: " + f for f in failures))
        sys.exit(1)
    print("release APK verified")


if __name__ == "__main__":
    main()
