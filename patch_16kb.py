#!/usr/bin/env python3
"""
Post-build script: patch all .so files in the APK to set ELF p_align=16384.
This eliminates the Android 16KB page-size warning dialog on Pixel 9 / Android 16.

Run after ./gradlew assembleDebug:
    python3 patch_16kb.py
"""
import os
import shutil
import struct
import subprocess
import sys
import zipfile

APK_IN = "app/build/outputs/apk/debug/app-debug.apk"
APK_OUT = "app/build/outputs/apk/debug/app-debug-patched.apk"
APK_FINAL = "app/build/outputs/apk/debug/app-debug.apk"


def patch_elf(data: bytes) -> bytes:
    """Set p_align=16384 for all PT_LOAD segments in an ELF64 binary."""
    arr = bytearray(data)
    if len(arr) < 64:
        return data
    ei_class = arr[4]
    ei_data = arr[5]
    if ei_class != 2 or ei_data != 1:  # not ELF64 little-endian
        return data

    e_phoff = struct.unpack("<Q", arr[32:40])[0]
    e_phentsize = struct.unpack("<H", arr[54:56])[0]
    e_phnum = struct.unpack("<H", arr[56:58])[0]

    patched = 0
    for i in range(e_phnum):
        ph = e_phoff + i * e_phentsize
        if ph + 56 > len(arr):
            break
        p_type = struct.unpack("<I", arr[ph:ph + 4])[0]
        p_align = struct.unpack("<Q", arr[ph + 48:ph + 56])[0]
        if p_type == 1 and p_align < 16384:  # PT_LOAD
            arr[ph + 48:ph + 56] = struct.pack("<Q", 16384)
            patched += 1

    return bytes(arr)


def find_apksigner() -> str | None:
    """Find apksigner in Android SDK build-tools."""
    android_home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK")
    if not android_home:
        # Fallback to common default locations
        home = os.path.expanduser("~")
        for candidate in [os.path.join(home, "Android", "Sdk")]:
            if os.path.isdir(candidate):
                android_home = candidate
                break
    if not android_home:
        return None
    build_tools = os.path.join(android_home, "build-tools")
    if not os.path.isdir(build_tools):
        return None
    versions = sorted(
        [d for d in os.listdir(build_tools) if os.path.isdir(os.path.join(build_tools, d))],
        reverse=True,
    )
    for v in versions:
        path = os.path.join(build_tools, v, "apksigner")
        if os.path.isfile(path):
            return path
    return None


def sign_apk(apk_path: str) -> bool:
    """Re-sign with debug keystore."""
    apksigner = find_apksigner()
    if not apksigner:
        print("Warning: apksigner not found. Install Android SDK build-tools.")
        return False

    # Try to find java if not on PATH
    env = os.environ.copy()
    if not shutil.which("java"):
        jbr = "/opt/android-studio/jbr/bin"
        if os.path.isfile(os.path.join(jbr, "java")):
            env["PATH"] = jbr + os.pathsep + env.get("PATH", "")

    ks = os.path.expanduser("~/.android/debug.keystore")
    cmd = [
        apksigner, "sign",
        "--ks", ks,
        "--ks-pass", "pass:android",
        "--key-pass", "pass:android",
        apk_path,
    ]
    try:
        subprocess.run(cmd, env=env, check=True, capture_output=True, text=True)
        print("Re-signed with debug key.")
        return True
    except subprocess.CalledProcessError as e:
        print(f"Sign failed: {e.stderr}")
        return False


def patch_apk():
    if not os.path.exists(APK_IN):
        print(f"APK not found: {APK_IN}")
        print("Build first: ./gradlew assembleDebug")
        sys.exit(1)

    with zipfile.ZipFile(APK_IN, "r") as zin:
        with zipfile.ZipFile(APK_OUT, "w", zipfile.ZIP_DEFLATED) as zout:
            for item in zin.infolist():
                data = zin.read(item.filename)
                if item.filename.endswith(".so"):
                    new_data = patch_elf(data)
                    if new_data != data:
                        print(f"Patched: {item.filename}")
                    zout.writestr(item, new_data, compress_type=zipfile.ZIP_STORED)
                else:
                    zout.writestr(item, data)

    os.replace(APK_OUT, APK_FINAL)
    print(f"Patched APK: {APK_FINAL}")

    if sign_apk(APK_FINAL):
        print("Ready to install.")
    else:
        print("Install will fail — sign manually with apksigner.")


if __name__ == "__main__":
    patch_apk()
